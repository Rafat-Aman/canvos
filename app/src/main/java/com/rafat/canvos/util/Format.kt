package com.rafat.canvos.util

import androidx.core.text.HtmlCompat
import com.rafat.canvos.data.api.Assignment
import com.rafat.canvos.data.api.Submission
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

fun String?.toInstant(): Instant? = this?.let {
    runCatching { Instant.parse(it) }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(it).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(it.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
}

fun Instant.local(): ZonedDateTime = atZone(ZoneId.systemDefault())

fun Instant.localDate(): LocalDate = local().toLocalDate()

// Getters so a language change while the app runs is picked up.
private val timeFmt get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
private val monthDay get() = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val monthDayYear get() = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val weekdayMonthDay get() = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

/** "Nov 5 at 11:59 PM" (year shown when not this year). */
fun Instant.dateTime(): String {
    val z = local()
    val date = if (z.year == ZonedDateTime.now().year) monthDay.format(z) else monthDayYear.format(z)
    return "$date at ${timeFmt.format(z)}"
}

fun Instant.date(): String {
    val z = local()
    return if (z.year == ZonedDateTime.now().year) monthDay.format(z) else monthDayYear.format(z)
}

fun Instant.time(): String = timeFmt.format(local())

/** "Today", "Tomorrow", "Yesterday" or "Monday, Nov 3". */
fun LocalDate.dayHeader(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> weekdayMonthDay.format(this) + if (year != today.year) ", $year" else ""
    }
}

/** "just now", "5m ago", "3h ago", "2d ago", else a date. */
fun Instant.ago(): String {
    val d = Duration.between(this, Instant.now())
    val s = d.seconds
    return when {
        s < 0 -> date()
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86_400 -> "${s / 3600}h ago"
        s < 7 * 86_400 -> "${s / 86_400}d ago"
        else -> date()
    }
}

/** Plain-text preview of a Canvas HTML body. */
fun String?.htmlToText(max: Int = 300): String {
    if (this.isNullOrBlank()) return ""
    val t = HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        .replace('￼', ' ') // object replacement chars from images
        .replace(Regex("[\\s\\u00A0]+"), " ")
        .trim()
    return if (t.length > max) t.take(max).trimEnd() + "…" else t
}

fun Double.pretty(): String {
    if (this.isNaN()) return "-"
    val r = (this * 100).roundToLong() / 100.0
    return if (abs(r - r.toLong()) < 1e-9) r.toLong().toString() else r.toString().trimEnd('0').trimEnd('.')
}

fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes < 0) return ""
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.roundToLong()} KB"
    val mb = kb / 1024
    return if (mb < 1024) String.format(Locale.getDefault(), "%.1f MB", mb) else String.format(Locale.getDefault(), "%.2f GB", mb / 1024)
}

/** Human status of an assignment for the current student. */
enum class SubmissionStatus(val label: String) {
    GRADED("Graded"), SUBMITTED("Submitted"), LATE("Late"), MISSING("Missing"), EXCUSED("Excused"),
    NOT_SUBMITTED("Not submitted"), NONE("")
}

fun Assignment.status(): SubmissionStatus {
    val s = submission
    val noSubmission = submissionTypes.isEmpty() || submissionTypes.all { it == "none" || it == "not_graded" || it == "on_paper" }
    if (s != null) {
        if (s.excused == true) return SubmissionStatus.EXCUSED
        if (s.missing) return SubmissionStatus.MISSING
        if (s.isGraded && (s.postedAt != null || s.score != null || s.grade != null)) return SubmissionStatus.GRADED
        if (s.late && s.isSubmitted) return SubmissionStatus.LATE
        if (s.isSubmitted) return SubmissionStatus.SUBMITTED
    }
    if (noSubmission) return SubmissionStatus.NONE
    val due = dueAt.toInstant()
    if (due != null && due.isBefore(Instant.now())) return SubmissionStatus.MISSING.takeIf { s?.missing == true } ?: SubmissionStatus.NOT_SUBMITTED
    return SubmissionStatus.NOT_SUBMITTED
}

/** "8 / 10", "A (9 / 10)", "Complete" ... or null if ungraded. */
fun gradeText(sub: Submission?, pointsPossible: Double?, gradingType: String?): String? {
    if (sub == null) return null
    if (sub.excused == true) return "Excused"
    val score = sub.score
    val grade = sub.grade
    if (score == null && grade.isNullOrBlank()) return null
    val pts = pointsPossible?.let { " / ${it.pretty()}" } ?: ""
    return when (gradingType) {
        "pass_fail" -> when (grade) { "complete" -> "Complete"; "incomplete" -> "Incomplete"; else -> grade ?: score?.pretty() }
        "letter_grade", "gpa_scale" -> if (score != null) "$grade (${score.pretty()}$pts)" else grade
        "percent" -> if (score != null) "$grade (${score.pretty()}$pts)" else grade
        "not_graded" -> null
        else -> if (score != null) "${score.pretty()}$pts" else grade
    }
}

fun pointsText(p: Double?): String? = p?.let { if (it == 1.0) "1 pt" else "${it.pretty()} pts" }

fun submissionTypeLabel(t: String): String = when (t) {
    "online_text_entry" -> "Text entry"
    "online_url" -> "Website URL"
    "online_upload" -> "File upload"
    "media_recording" -> "Media recording"
    "online_quiz" -> "Quiz"
    "discussion_topic" -> "Discussion"
    "external_tool" -> "External tool"
    "on_paper" -> "On paper"
    "student_annotation" -> "Student annotation"
    "none" -> "No submission"
    "not_graded" -> "Not graded"
    else -> t.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
