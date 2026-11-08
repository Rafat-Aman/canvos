package com.rafat.canvos.ui.nav

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable object LoginRoute
@Serializable object MainRoute
@Serializable object AllCoursesRoute
@Serializable object SettingsRoute
@Serializable object CalendarSyncRoute
@Serializable object OfflineRoute
@Serializable object OfflineFilesRoute
@Serializable data class CourseRoute(val courseId: Long)
@Serializable data class AnnouncementsRoute(val courseId: Long)
@Serializable data class AssignmentsRoute(val courseId: Long)
@Serializable data class AssignmentRoute(val courseId: Long, val assignmentId: Long)
@Serializable data class SubmitRoute(val courseId: Long, val assignmentId: Long, val type: String)
@Serializable data class SubmissionRoute(val courseId: Long, val assignmentId: Long)
@Serializable data class GradesRoute(val courseId: Long)
@Serializable data class DiscussionsRoute(val courseId: Long)
@Serializable data class DiscussionRoute(val courseId: Long, val topicId: Long, val announcement: Boolean = false)
@Serializable data class PagesRoute(val courseId: Long)
/** Empty [url] = the course front page. */
@Serializable data class PageRoute(val courseId: Long, val url: String = "")
/** [courseId] 0 = "My files". [folderId] 0 = root. */
@Serializable data class FilesRoute(val courseId: Long = 0, val folderId: Long = 0, val title: String = "")
@Serializable data class SyllabusRoute(val courseId: Long)
@Serializable data class PeopleRoute(val courseId: Long)
@Serializable data class ModulesRoute(val courseId: Long)
@Serializable data class QuizzesRoute(val courseId: Long)
@Serializable data class CourseFeedRoute(val courseId: Long)
@Serializable data class ConversationRoute(val conversationId: Long)
@Serializable data class ComposeRoute(
    val courseId: Long = 0,
    val recipientId: String = "",
    val recipientName: String = "",
    val subject: String = "",
)
@Serializable data class WebRoute(val url: String, val title: String = "")
@Serializable data class EventRoute(val eventId: String)

/** What tapping a link should do. */
sealed interface LinkAction {
    data class Navigate(val route: Any) : LinkAction
    data class OpenFile(val fileId: Long, val courseId: Long?) : LinkAction
    data class External(val url: String) : LinkAction
}

object CanvasLinks {
    /**
     * Map a Canvas web URL (absolute or relative) to an in-app screen. Unknown
     * Canvas pages open in a logged-in web view; other sites go to the browser.
     */
    fun resolve(raw: String, base: HttpUrl): LinkAction {
        val url = (raw.toHttpUrlOrNull() ?: base.resolve(raw)) ?: return LinkAction.External(raw)
        if (!url.host.equals(base.host, ignoreCase = true)) return LinkAction.External(url.toString())
        val s = url.pathSegments.filter { it.isNotEmpty() }
        val web = LinkAction.Navigate(WebRoute(url.toString()))

        fun long(i: Int) = s.getOrNull(i)?.toLongOrNull()

        // /files/123[/download|/preview]  and  /users/self/files/123
        val fileIdx = s.indexOf("files")
        if (fileIdx >= 0) {
            val fid = long(fileIdx + 1)
            if (fid != null) return LinkAction.OpenFile(fid, if (s.firstOrNull() == "courses") long(1) else null)
        }
        if (s.firstOrNull() == "conversations") {
            long(1)?.let { return LinkAction.Navigate(ConversationRoute(it)) }
            return web
        }
        if (s.firstOrNull() != "courses") return web
        val c = long(1) ?: return web
        if (s.size == 2) return LinkAction.Navigate(CourseRoute(c))
        val id = long(3)
        return when (s[2]) {
            "assignments" -> when {
                s.getOrNull(3) == "syllabus" -> LinkAction.Navigate(SyllabusRoute(c))
                id != null && s.getOrNull(4) == "submissions" -> LinkAction.Navigate(SubmissionRoute(c, id))
                id != null -> LinkAction.Navigate(AssignmentRoute(c, id))
                s.size == 3 -> LinkAction.Navigate(AssignmentsRoute(c))
                else -> web
            }
            "announcements" -> if (id != null) LinkAction.Navigate(DiscussionRoute(c, id, true)) else LinkAction.Navigate(AnnouncementsRoute(c))
            "discussion_topics" -> if (id != null) LinkAction.Navigate(DiscussionRoute(c, id)) else if (s.size == 3) LinkAction.Navigate(DiscussionsRoute(c)) else web
            "pages", "wiki" -> {
                val page = s.getOrNull(3)
                when {
                    page == null && s[2] == "wiki" -> LinkAction.Navigate(PageRoute(c))
                    page == null -> LinkAction.Navigate(PagesRoute(c))
                    s.size == 4 -> LinkAction.Navigate(PageRoute(c, page))
                    else -> web // edit, revisions...
                }
            }
            "grades" -> LinkAction.Navigate(GradesRoute(c))
            "modules" -> if (s.size == 3) LinkAction.Navigate(ModulesRoute(c)) else web
            "users" -> if (s.size == 3) LinkAction.Navigate(PeopleRoute(c)) else web
            "quizzes" -> if (s.size == 3) LinkAction.Navigate(QuizzesRoute(c)) else web
            "syllabus" -> LinkAction.Navigate(SyllabusRoute(c))
            "files" -> LinkAction.Navigate(FilesRoute(c))
            else -> web
        }
    }
}
