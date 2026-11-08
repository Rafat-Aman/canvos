package com.rafat.canvos

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.rafat.canvos.data.CalendarSync
import com.rafat.canvos.data.api.CanvasJson
import com.rafat.canvos.data.api.PlannerItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Runs the calendar sync against a LOCAL (non-syncing) test calendar only, never a
 * Google calendar, with synthetic Canvas planner items.
 */
@RunWith(AndroidJUnit4::class)
class CalendarSyncTest {
    @get:Rule
    val perms: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var calId = -1L

    private fun asSyncAdapter(uri: Uri) = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "canvos-test")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()

    @Before
    fun setUp() {
        // Find or create the local test calendar.
        ctx.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.ACCOUNT_NAME} = ?",
            arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, "canvos-test"), null,
        )?.use { if (it.moveToFirst()) calId = it.getLong(0) }
        if (calId < 0) {
            val v = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, "canvos-test")
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, "CanvosTest")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "CanvosTestLocal")
                put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF0000FF.toInt())
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, "canvos-test")
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            }
            calId = ContentUris.parseId(ctx.contentResolver.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), v)!!)
        }
        // Start clean.
        ctx.contentResolver.delete(asSyncAdapter(CalendarContract.Events.CONTENT_URI), "${CalendarContract.Events.CALENDAR_ID} = ?", arrayOf(calId.toString()))
        CalendarSync.Prefs(ctx).clearAll()
        CalendarSync.Prefs(ctx).calendarId = calId
        CalendarSync.Prefs(ctx).reminderMinutes = 60
    }

    private fun item(json: String) = CanvasJson.decodeFromString(PlannerItem.serializer(), json)

    private fun assignment(id: Long, due: Instant, submitted: Boolean = false) = item(
        """{"course_id":10,"plannable_id":$id,"plannable_type":"assignment","plannable_date":"$due",
           "plannable":{"id":$id,"title":"Essay $id","due_at":"$due","points_possible":20},
           "html_url":"/courses/10/assignments/$id","context_name":"ENG111.10 Public Speaking",
           "submissions":{"submitted":$submitted}}""",
    )

    private fun event(id: Long, start: Instant, allDay: Boolean) = item(
        """{"course_id":10,"plannable_id":$id,"plannable_type":"calendar_event","plannable_date":"$start",
           "plannable":{"id":$id,"title":"Midterm","start_at":"$start","end_at":"${start.plus(2, ChronoUnit.HOURS)}","all_day":$allDay,"location_name":"Room 501"},
           "context_name":"ENG111.10 Public Speaking","submissions":false}""",
    )

    private val announcement = item(
        """{"course_id":10,"plannable_id":99,"plannable_type":"announcement","plannable_date":"${Instant.now()}","plannable":{"id":99,"title":"Hi"},"submissions":false}""",
    )

    data class Ev(val id: Long, val title: String, val start: Long, val end: Long, val allDay: Boolean, val location: String?, val description: String)

    private fun events(): List<Ev> {
        val out = ArrayList<Ev>()
        ctx.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY, CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.DESCRIPTION),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0", arrayOf(calId.toString()), null,
        )?.use { c -> while (c.moveToNext()) out += Ev(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getInt(4) == 1, c.getString(5), c.getString(6) ?: "") }
        return out.sortedBy { it.title }
    }

    private fun reminders(eventId: Long): List<Int> {
        val out = ArrayList<Int>()
        ctx.contentResolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf(CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()), null)?.use { while (it.moveToNext()) out += it.getInt(0) }
        return out
    }

    private fun sync(items: List<PlannerItem>) = runBlocking {
        CalendarSync.syncItems(ctx, calId, items, mapOf(10L to "ENG111"), "https://school.instructure.com", LocalDate.now().minusDays(14))
    }

    @Test
    fun fullLifecycle() {
        val due = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES)
        val start = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)

        // 1. First sync: 2 assignments + timed event; announcements are skipped.
        assertEquals("3 added", sync(listOf(assignment(1, due), assignment(2, due), event(7, start, false), announcement)))
        var ev = events()
        assertEquals(listOf("ENG111: Essay 1", "ENG111: Essay 2", "ENG111: Midterm"), ev.map { it.title })
        val essay1 = ev[0]
        assertEquals(due.toEpochMilli(), essay1.start)
        assertTrue(essay1.description.contains("https://school.instructure.com/courses/10/assignments/1"))
        assertTrue(essay1.description.contains("Points: 20"))
        assertEquals(listOf(60), reminders(essay1.id))
        assertEquals("Room 501", ev[2].location)
        assertEquals(start.plus(2, ChronoUnit.HOURS).toEpochMilli(), ev[2].end)

        // 2. Nothing changed -> nothing written.
        assertTrue(sync(listOf(assignment(1, due), assignment(2, due), event(7, start, false))).startsWith("Up to date"))

        // 3. Due date moved + submitted -> updated in place (same row), ticked.
        val later = due.plus(1, ChronoUnit.DAYS)
        assertEquals("2 updated", sync(listOf(assignment(1, later, submitted = true), assignment(2, due), event(7, start, true))))
        ev = events()
        val e1 = ev.first { it.title.endsWith("Essay 1") }
        assertEquals(essay1.id, e1.id)
        assertEquals("✓ ENG111: Essay 1", e1.title)
        assertEquals(later.toEpochMilli(), e1.start)
        assertTrue("all-day event", ev.first { it.title.endsWith("Midterm") }.allDay)
        assertEquals(listOf(60), reminders(e1.id)) // reminders not duplicated

        // 4. Assignment 2 deleted on Canvas -> removed from the calendar.
        assertEquals("1 removed", sync(listOf(assignment(1, later, true), event(7, start, true))))
        assertEquals(2, events().size)

        // 5. User deletes an event in their calendar app -> not recreated.
        ctx.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e1.id), null, null)
        sync(listOf(assignment(1, later, true), event(7, start, true)))
        assertTrue(sync(listOf(assignment(1, later, true), event(7, start, true))).startsWith("Up to date"))
        assertEquals(listOf("ENG111: Midterm"), events().map { it.title })

        // 6. ...unless its due date changes again.
        assertEquals("1 added", sync(listOf(assignment(1, later.plus(1, ChronoUnit.DAYS), true), event(7, start, true))))

        // 7. Reminder setting change is applied to existing events.
        CalendarSync.Prefs(ctx).reminderMinutes = 1440
        sync(listOf(assignment(1, later.plus(1, ChronoUnit.DAYS), true), event(7, start, true)))
        events().forEach { assertEquals(listOf(1440), reminders(it.id)) }

        // 8. "Remove events" clears everything we created.
        runBlocking { CalendarSync.removeAll(ctx) }
        assertEquals(0, events().size)
    }
}
