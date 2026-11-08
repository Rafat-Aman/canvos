package com.rafat.canvos.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rafat.canvos.MainActivity
import com.rafat.canvos.R
import com.rafat.canvos.data.api.Conversation
import com.rafat.canvos.data.api.PlannerItem
import com.rafat.canvos.data.api.StreamItem
import com.rafat.canvos.util.dateTime
import com.rafat.canvos.util.htmlToText
import com.rafat.canvos.util.pretty
import com.rafat.canvos.util.toInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** One notification to show. [key] identifies it so it is shown only once. */
data class Alert(
    val key: String,
    val channel: String,
    val title: String,
    val text: String,
    val url: String? = null,
    val conversationId: Long = 0L,
    val time: Long = 0L,
)

enum class DueReminder(val label: String, val offsetsMinutes: List<Long>) {
    OFF("Off", emptyList()),
    H1("1 hour before", listOf(60)),
    H3("3 hours before", listOf(180)),
    D1("1 day before", listOf(1440)),
    D1H1("1 day and 1 hour before", listOf(1440, 60)),
}

/** Turns Canvas data into notifications. No Android here, so it is unit-tested. */
object Alerts {
    private fun join(vararg parts: String?) = parts.filter { !it.isNullOrBlank() }.joinToString(" · ")

    fun fromStream(items: List<StreamItem>, selfId: Long?, courseName: (Long?) -> String?): List<Alert> = items.flatMap { item ->
        val time = (item.updatedAt ?: item.createdAt).toInstant()?.toEpochMilli() ?: 0L
        val course = courseName(item.courseId)
        val msg = item.message.htmlToText(200)
        when (item.type) {
            "Announcement" -> listOf(
                Alert("ann:${item.announcementId ?: item.id}", Notifier.CH_ANNOUNCEMENTS, item.title ?: "New announcement", join(course, msg), item.htmlUrl, time = time),
            )
            "DiscussionTopic" -> listOf(
                Alert("disc:${item.discussionTopicId ?: item.id}", Notifier.CH_DISCUSSIONS, "New discussion: ${item.title.orEmpty()}", join(course, msg), item.htmlUrl, time = time),
            )
            "Submission" -> buildList {
                val name = item.assignment?.name ?: item.title ?: "Assignment"
                if (item.score != null || !item.grade.isNullOrBlank()) {
                    val pts = item.assignment?.pointsPossible
                    val result = when {
                        item.score != null && pts != null -> "${item.score.pretty()} / ${pts.pretty()}"
                        item.score != null -> item.score.pretty()
                        else -> item.grade
                    }
                    add(Alert("grade:${item.id}:${item.grade}:${item.score}", Notifier.CH_GRADES, "Graded: $name", join(course, "Grade $result"), item.htmlUrl, time = time))
                }
                val comment = item.submissionComments.lastOrNull { it.authorId != selfId }
                if (comment != null) {
                    val at = comment.createdAt.toInstant()?.toEpochMilli() ?: time
                    val who = comment.authorName ?: comment.author?.displayName
                    add(Alert("comment:${item.id}:${comment.id}", Notifier.CH_GRADES, "New comment on $name", join(course, listOfNotNull(who, comment.comment).joinToString(": ")), item.htmlUrl, time = at))
                }
            }
            // Canvas' own notices: assignment created, due date changed...
            "Message" -> listOf(Alert("msg:${item.id}", Notifier.CH_OTHER, item.title ?: "Canvas", join(course, msg), item.htmlUrl, time = time))
            else -> emptyList() // conversations come from the inbox itself
        }
    }

    /** Unread conversations whose last message isn't the student's own. */
    fun fromConversations(list: List<Conversation>, selfId: Long?): List<Alert> = list
        .filter { it.workflowState == "unread" && it.lastMessageAt != null && it.lastMessageAt != it.lastAuthoredMessageAt }
        .map { c ->
            val others = c.participants.filter { it.id != selfId }.take(2).mapNotNull { it.name }.joinToString(", ")
            Alert(
                "conv:${c.id}:${c.lastMessageAt}", Notifier.CH_MESSAGES,
                c.subject?.takeIf { it.isNotBlank() } ?: "New message",
                listOf(others.ifBlank { null }, c.lastMessage?.take(300)).filterNotNull().joinToString(": "),
                conversationId = c.id, time = c.lastMessageAt.toInstant()?.toEpochMilli() ?: 0L,
            )
        }

    private val dueTypes = setOf("assignment", "quiz", "discussion_topic", "sub_assignment")

    /** For each unfinished item, the reminder whose time has come (the latest one only). */
    fun dueSoon(items: List<PlannerItem>, now: Instant, offsetsMinutes: List<Long>): List<Alert> {
        if (offsetsMinutes.isEmpty()) return emptyList()
        val offsets = offsetsMinutes.sortedDescending()
        return items.filter { it.plannableType in dueTypes && !it.isComplete }.mapNotNull { item ->
            val due = (item.plannable.dueAt ?: item.plannableDate).toInstant() ?: return@mapNotNull null
            if (!due.isAfter(now)) return@mapNotNull null
            val off = offsets.lastOrNull { !now.isBefore(due.minus(Duration.ofMinutes(it))) } ?: return@mapNotNull null
            Alert(
                "due:${item.plannableType}:${item.plannableId}:${due.epochSecond}:$off", Notifier.CH_DUE,
                "Due ${untilText(Duration.between(now, due))}: ${item.title}",
                join(item.contextName, "Due ${due.dateTime()}"), item.htmlUrl, time = now.toEpochMilli(),
            )
        }
    }

    fun untilText(d: Duration): String {
        val m = d.toMinutes().coerceAtLeast(1)
        return when {
            m < 60 -> "in $m min"
            m < 36 * 60 -> ((m + 30) / 60).let { h -> if (h == 1L) "in 1 hour" else "in $h hours" }
            else -> "in ${(m + 720) / 1440} days"
        }
    }

    /** What to show now; on the very first check only reminders, the rest is history. */
    fun toShow(alerts: List<Alert>, due: List<Alert>, seen: Set<String>, firstRun: Boolean, now: Long): List<Alert> {
        val fresh = if (firstRun) emptyList() else alerts.filter { it.key !in seen && now - it.time < 3 * 86_400_000L }
        return (fresh + due.filter { it.key !in seen }).distinctBy { it.key }
    }
}

/** Posts Canvas notifications (Canvas can't push to this app, so they're checked for regularly). */
object Notifier {
    const val CH_ANNOUNCEMENTS = "announcements"
    const val CH_MESSAGES = "messages"
    const val CH_GRADES = "grades"
    const val CH_DUE = "due_dates"
    const val CH_DISCUSSIONS = "discussions"
    const val CH_OTHER = "canvas_other"
    private const val OLD_CHANNEL = "canvas_activity"
    private const val MAX_SEEN = 800

    fun canNotify(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** High importance so they pop up on screen like the official app's push notifications. */
    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(OLD_CHANNEL)
        fun ch(id: String, name: String, desc: String, importance: Int = NotificationManager.IMPORTANCE_HIGH) =
            NotificationChannel(id, name, importance).apply { description = desc }
        nm.createNotificationChannels(
            listOf(
                ch(CH_ANNOUNCEMENTS, "Announcements", "New course announcements"),
                ch(CH_MESSAGES, "Inbox messages", "New messages in your Canvas inbox"),
                ch(CH_GRADES, "Grades and feedback", "New grades and comments on your work"),
                ch(CH_DUE, "Due date reminders", "Reminders before work is due"),
                ch(CH_DISCUSSIONS, "Discussions", "New discussion topics", NotificationManager.IMPORTANCE_DEFAULT),
                ch(CH_OTHER, "Other Canvas updates", "New or changed assignments and other course notices", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("notify", Context.MODE_PRIVATE)

    fun lastCheck(ctx: Context): Long = prefs(ctx).getLong("last_check", 0L)

    private fun loadSeen(ctx: Context): LinkedHashSet<String> = runCatching {
        LinkedHashSet(com.rafat.canvos.data.api.CanvasJson.decodeFromString(ListSerializer(String.serializer()), prefs(ctx).getString("seen", "[]")!!))
    }.getOrDefault(LinkedHashSet())

    private fun saveSeen(ctx: Context, seen: Collection<String>) {
        val list = seen.toList().takeLast(MAX_SEEN)
        prefs(ctx).edit().putString("seen", com.rafat.canvos.data.api.CanvasJson.encodeToString(ListSerializer(String.serializer()), list)).apply()
    }

    /** Look for new announcements, messages, grades and upcoming due dates. */
    suspend fun check(ctx: Context, repo: Repository, settings: AppSettings) {
        val seen = loadSeen(ctx)
        val firstRun = !prefs(ctx).getBoolean("baselined", false)
        val alerts = ArrayList<Alert>()
        if (settings.notifications) {
            if (repo.self == null) runCatching { repo.profile() }
            repo.ensureCoursesCached()
            alerts += Alerts.fromStream(repo.activityStream(), repo.self?.id) { repo.cachedCourseName(it) }
            alerts += Alerts.fromConversations(repo.conversations("unread", null), repo.self?.id)
        }
        val due = if (settings.dueReminders != DueReminder.OFF) {
            val today = LocalDate.now()
            Alerts.dueSoon(repo.planner(today.minusDays(1), today.plusDays(3)), Instant.now(), settings.dueReminders.offsetsMinutes)
        } else emptyList()
        val now = System.currentTimeMillis()
        post(ctx, Alerts.toShow(alerts, due, seen, firstRun, now))
        seen.addAll(alerts.map { it.key })
        seen.addAll(due.map { it.key })
        saveSeen(ctx, seen)
        prefs(ctx).edit().putBoolean("baselined", true).putLong("last_check", now).apply()
    }

    /** Forget what was seen, e.g. after signing in as someone else. */
    fun reset(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    fun test(ctx: Context) {
        post(ctx, listOf(Alert("test:${System.currentTimeMillis()}", CH_ANNOUNCEMENTS, "Notifications are working", "New announcements, messages, grades and due dates will appear like this.", time = System.currentTimeMillis())))
    }

    private fun post(ctx: Context, alerts: List<Alert>) {
        if (alerts.isEmpty() || !canNotify(ctx)) return
        ensureChannels(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        // Oldest first so the newest ends up on top; Android groups them per app.
        for (a in alerts.sortedBy { it.time }.takeLast(8)) {
            val open = Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_URL, a.url)
                .putExtra(MainActivity.EXTRA_CONVERSATION, a.conversationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(ctx, a.key.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, a.channel)
                .setSmallIcon(R.drawable.ic_stat_canvos)
                .setColor(0xFF0374B5.toInt())
                .setContentTitle(a.title)
                .setContentText(a.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(a.text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(
                    when (a.channel) {
                        CH_MESSAGES -> NotificationCompat.CATEGORY_MESSAGE
                        CH_DUE -> NotificationCompat.CATEGORY_REMINDER
                        else -> NotificationCompat.CATEGORY_SOCIAL
                    },
                )
                .apply { if (a.time > 0) setWhen(a.time).setShowWhen(true) }
                .build()
            if (canNotify(ctx)) {
                try {
                    nm.notify(a.key.hashCode(), n)
                } catch (_: SecurityException) {
                    return
                }
            }
        }
    }
}
