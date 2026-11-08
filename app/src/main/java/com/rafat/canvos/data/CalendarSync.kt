package com.rafat.canvos.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import com.rafat.canvos.data.api.PlannerItem
import com.rafat.canvos.util.htmlToText
import com.rafat.canvos.util.pretty
import com.rafat.canvos.util.toInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import com.rafat.canvos.data.api.CanvasJson
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Mirrors Canvas due dates and course events into a calendar on the phone.
 * When that calendar belongs to a Google account, Android's own Google sync
 * uploads the events, so they appear in Google Calendar everywhere.
 */
object CalendarSync {
    data class DeviceCalendar(
        val id: Long, val name: String, val account: String, val accountType: String,
        val color: Int, val isPrimary: Boolean,
    ) {
        val isGoogle get() = accountType == "com.google"
    }

    data class Status(val running: Boolean = false, val message: String? = null)

    @Serializable
    data class Mapped(val eventId: Long, val hash: Int, val startMillis: Long)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private val lock = Mutex()

    const val PAST_DAYS = 14L
    const val FUTURE_DAYS = 150L
    private val syncedTypes = setOf(
        "assignment", "sub_assignment", "quiz", "discussion_topic", "wiki_page", "planner_note", "calendar_event", "assessment_request",
    )

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Calendars this app may add events to, Google ones first. */
    fun calendars(ctx: Context): List<DeviceCalendar> {
        if (!hasPermission(ctx)) return emptyList()
        val out = ArrayList<DeviceCalendar>()
        ctx.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(
                Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
                Calendars.CALENDAR_COLOR, Calendars.IS_PRIMARY, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.VISIBLE,
            ),
            "${Calendars.CALENDAR_ACCESS_LEVEL} >= ?", arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                out += DeviceCalendar(
                    id = c.getLong(0), name = c.getString(1) ?: "Calendar", account = c.getString(2) ?: "",
                    accountType = c.getString(3) ?: "", color = c.getInt(4), isPrimary = c.getInt(5) == 1,
                )
            }
        }
        return out.sortedWith(compareByDescending<DeviceCalendar> { it.isGoogle }.thenBy { it.account }.thenByDescending { it.isPrimary }.thenBy { it.name })
    }

    // ------------------------------------------------------------------ settings

    class Prefs(ctx: Context) {
        private val p = ctx.getSharedPreferences("calendar_sync", Context.MODE_PRIVATE)
        var enabled: Boolean
            get() = p.getBoolean("enabled", false)
            set(v) = p.edit().putBoolean("enabled", v).apply()
        var calendarId: Long
            get() = p.getLong("calendar_id", -1)
            set(v) = p.edit().putLong("calendar_id", v).apply()
        /** -1 = no reminder. */
        var reminderMinutes: Int
            get() = p.getInt("reminder", 60)
            set(v) = p.edit().putInt("reminder", v).apply()
        var includeEvents: Boolean
            get() = p.getBoolean("include_events", true)
            set(v) = p.edit().putBoolean("include_events", v).apply()
        var lastSync: Long
            get() = p.getLong("last_sync", 0)
            set(v) = p.edit().putLong("last_sync", v).apply()
        var lastResult: String?
            get() = p.getString("last_result", null)
            set(v) = p.edit().putString("last_result", v).apply()
        var mappedCalendarId: Long
            get() = p.getLong("mapped_calendar", -1)
            set(v) = p.edit().putLong("mapped_calendar", v).apply()

        fun map(): MutableMap<String, Mapped> = runCatching {
            CanvasJson.decodeFromString(MapSerializer(String.serializer(), Mapped.serializer()), p.getString("map", "{}")!!).toMutableMap()
        }.getOrDefault(mutableMapOf())

        fun saveMap(m: Map<String, Mapped>) =
            p.edit().putString("map", CanvasJson.encodeToString(MapSerializer(String.serializer(), Mapped.serializer()), m)).apply()

        fun clearAll() = p.edit().clear().apply()
    }

    // ------------------------------------------------------------------ sync

    private data class Desired(
        val key: String, val title: String, val description: String, val start: Long, val end: Long,
        val allDay: Boolean, val location: String?,
    ) {
        fun hash(reminder: Int) = listOf(title, description, start, end, allDay, location, reminder).hashCode()
    }

    /** Build the events we want in the calendar from planner items. */
    private fun desired(items: List<PlannerItem>, codes: Map<Long, String>, domain: String, includeEvents: Boolean): List<Desired> {
        val zone = ZoneId.systemDefault()
        return items.filter { it.plannableType in syncedTypes }
            .filter { includeEvents || it.plannableType != "calendar_event" }
            .mapNotNull { item ->
                val p = item.plannable
                val code = item.courseId?.let { codes[it] }
                val prefix = code?.let { "$it: " } ?: ""
                val link = item.htmlUrl?.let { if (it.startsWith("http")) it else domain + it }
                val key = "${item.plannableType}-${item.plannableId}"
                if (item.plannableType == "calendar_event") {
                    val start = p.startAt.toInstant() ?: item.plannableDate.toInstant() ?: return@mapNotNull null
                    val allDay = p.allDay == true
                    val end = p.endAt.toInstant()?.takeIf { it.isAfter(start) } ?: start.plus(Duration.ofHours(1))
                    val (s, e) = if (allDay) {
                        val d = start.atZone(zone).toLocalDate()
                        d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() to d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    } else start.toEpochMilli() to end.toEpochMilli()
                    Desired(
                        key, prefix + item.title,
                        listOfNotNull(item.contextName, p.details?.htmlToText(1500), link, MARKER + key)
                            .filter { it.isNotBlank() }.joinToString("\n\n"),
                        s, e, allDay, p.locationName,
                    )
                } else {
                    val due = (p.dueAt ?: p.todoDate ?: item.plannableDate).toInstant() ?: return@mapNotNull null
                    val kind = when (item.plannableType) {
                        "quiz" -> "Quiz"; "discussion_topic" -> "Discussion"; "wiki_page" -> "Page"
                        "planner_note" -> "To do"; "assessment_request" -> "Peer review"; else -> "Assignment"
                    }
                    val done = item.isComplete
                    Desired(
                        key, (if (done) "✓ " else "") + prefix + item.title,
                        listOfNotNull(
                            "$kind due" + (item.contextName?.let { " · $it" } ?: ""),
                            p.pointsPossible?.let { "Points: ${it.pretty()}" },
                            if (done) "Done / submitted" else null,
                            link, MARKER + key,
                        ).joinToString("\n"),
                        due.toEpochMilli(), due.toEpochMilli(), false, null,
                    )
                }
            }
    }

    /** Run a sync now. Returns a short human summary. */
    suspend fun sync(ctx: Context, repo: Repository): String = lock.withLock {
        val prefs = Prefs(ctx)
        _status.value = Status(true, "Syncing…")
        val result = try {
            if (!hasPermission(ctx)) error("Calendar permission is needed")
            val calId = prefs.calendarId
            if (calId < 0 || calendars(ctx).none { it.id == calId }) error("Choose a calendar first")
            val today = LocalDate.now()
            val windowStart = today.minusDays(PAST_DAYS)
            val items = repo.planner(windowStart, today.plusDays(FUTURE_DAYS))
            val codes = runCatching { repo.allCourses() }.getOrDefault(emptyList())
                .associate { it.id to (it.course.courseCode ?: it.displayName).substringBefore(' ').take(14) }
            syncItems(ctx, calId, items, codes, repo.domain, windowStart)
        } catch (e: Exception) {
            "Sync failed: ${e.message ?: e.javaClass.simpleName}"
        }
        prefs.lastSync = System.currentTimeMillis()
        prefs.lastResult = result
        _status.value = Status(false, result)
        result
    }

    /** Reconcile [calId] with the given planner items (separated out for testing). */
    internal suspend fun syncItems(
        ctx: Context, calId: Long, items: List<PlannerItem>, codes: Map<Long, String>, domain: String, windowStart: LocalDate,
    ): String {
        val prefs = Prefs(ctx)
        val want = desired(items, codes, domain, prefs.includeEvents)
        return withContext(Dispatchers.IO) {
            apply(ctx, prefs, calId, want, windowStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }

    private fun apply(ctx: Context, prefs: Prefs, calId: Long, want: List<Desired>, windowStartMillis: Long): String {
        val cr = ctx.contentResolver
        val reminder = prefs.reminderMinutes
        val map = prefs.map()
        // Calendar changed: move everything by removing it from the old one.
        if (prefs.mappedCalendarId != calId) {
            map.values.filter { it.eventId > 0 }.forEach { deleteEvent(ctx, it.eventId) }
            map.clear()
            prefs.mappedCalendarId = calId
        }
        var added = 0; var updated = 0; var removed = 0
        val tz = ZoneId.systemDefault().id
        for (d in want) {
            val h = d.hash(reminder)
            val m = map[d.key]
            val values = ContentValues().apply {
                put(Events.CALENDAR_ID, calId)
                put(Events.TITLE, d.title)
                put(Events.DESCRIPTION, d.description)
                put(Events.DTSTART, d.start)
                put(Events.DTEND, d.end)
                put(Events.ALL_DAY, if (d.allDay) 1 else 0)
                put(Events.EVENT_TIMEZONE, if (d.allDay) "UTC" else tz)
                put(Events.EVENT_LOCATION, d.location ?: "")
                put(Events.HAS_ALARM, if (reminder >= 0) 1 else 0)
                put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
            }
            when {
                m == null -> {
                    insert(ctx, values, reminder)?.let { id -> map[d.key] = Mapped(id, h, d.start); added++ }
                }
                m.eventId < 0 -> {
                    // The user deleted it; only bring it back if the date moved.
                    if (m.startMillis != d.start) insert(ctx, values, reminder)?.let { id -> map[d.key] = Mapped(id, h, d.start); added++ }
                }
                !exists(ctx, m.eventId, calId) -> map[d.key] = Mapped(-1, h, m.startMillis)
                m.hash != h -> {
                    cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, m.eventId), values, null, null)
                    cr.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(m.eventId.toString()))
                    if (reminder >= 0) addReminder(ctx, m.eventId, reminder)
                    map[d.key] = Mapped(m.eventId, h, d.start)
                    updated++
                }
            }
        }
        val wanted = want.map { it.key }.toSet()
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val (key, m) = it.next()
            if (key in wanted) continue
            if (m.startMillis >= windowStartMillis) {
                // Gone from Canvas (deleted/unpublished) or filtered out: remove.
                if (m.eventId > 0 && deleteEvent(ctx, m.eventId)) removed++
                it.remove()
            } else {
                // Just older than the window: keep the event, stop tracking it.
                it.remove()
            }
        }
        prefs.saveMap(map)
        return if (added + updated + removed == 0) "Up to date (${want.size} items)"
        else listOfNotNull(
            if (added > 0) "$added added" else null, if (updated > 0) "$updated updated" else null, if (removed > 0) "$removed removed" else null,
        ).joinToString(", ")
    }

    private fun insert(ctx: Context, values: ContentValues, reminder: Int): Long? {
        val uri = ctx.contentResolver.insert(Events.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        if (reminder >= 0) addReminder(ctx, id, reminder)
        return id
    }

    private fun addReminder(ctx: Context, eventId: Long, minutes: Int) {
        ctx.contentResolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, eventId)
            put(Reminders.MINUTES, minutes)
            put(Reminders.METHOD, Reminders.METHOD_ALERT)
        })
    }

    private fun exists(ctx: Context, eventId: Long, calId: Long): Boolean =
        ctx.contentResolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events._ID, Events.DELETED, Events.CALENDAR_ID),
            null, null, null,
        )?.use { c -> c.moveToFirst() && c.getInt(1) == 0 && c.getLong(2) == calId } ?: false

    private fun deleteEvent(ctx: Context, eventId: Long): Boolean =
        runCatching { ctx.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null) > 0 }.getOrDefault(false)

    /** Remove every event this app created and stop tracking them. */
    suspend fun removeAll(ctx: Context): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            val prefs = Prefs(ctx)
            var n = 0
            if (hasPermission(ctx)) prefs.map().values.filter { it.eventId > 0 }.forEach { if (deleteEvent(ctx, it.eventId)) n++ }
            prefs.saveMap(emptyMap())
            prefs.lastResult = "Removed $n events"
            _status.value = Status(false, "Removed $n events from your calendar")
            n
        }
    }

    const val MARKER = "Synced from Canvas by Canvos · "
}
