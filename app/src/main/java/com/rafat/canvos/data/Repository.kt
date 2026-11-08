package com.rafat.canvos.data

import com.rafat.canvos.data.api.Assignment
import com.rafat.canvos.data.api.AssignmentGroup
import com.rafat.canvos.data.api.CalendarEvent
import com.rafat.canvos.data.api.CachePolicy
import com.rafat.canvos.data.api.CanvasApi
import com.rafat.canvos.data.api.CanvasException
import com.rafat.canvos.data.api.CanvasJson
import com.rafat.canvos.data.api.NetworkException
import com.rafat.canvos.data.api.CanvasFile
import com.rafat.canvos.data.api.Conversation
import com.rafat.canvos.data.api.Course
import com.rafat.canvos.data.api.CourseNickname
import com.rafat.canvos.data.api.CustomColors
import com.rafat.canvos.data.api.DashboardCard
import com.rafat.canvos.data.api.DiscussionEntry
import com.rafat.canvos.data.api.DiscussionTopic
import com.rafat.canvos.data.api.DiscussionView
import com.rafat.canvos.data.api.Folder
import com.rafat.canvos.data.api.Module
import com.rafat.canvos.data.api.ModuleItem
import com.rafat.canvos.data.api.Page
import com.rafat.canvos.data.api.PlannerItem
import com.rafat.canvos.data.api.PlannerOverride
import com.rafat.canvos.data.api.Quiz
import com.rafat.canvos.data.api.Recipient
import com.rafat.canvos.data.api.SessionToken
import com.rafat.canvos.data.api.StreamItem
import com.rafat.canvos.data.api.Submission
import com.rafat.canvos.data.api.Tab
import com.rafat.canvos.data.api.UploadTicket
import com.rafat.canvos.data.api.User
import com.rafat.canvos.data.api.asParamString
import com.rafat.canvos.util.toInstant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId

/** A course plus the user's personal colour and nickname. */
data class CourseInfo(val course: Course, val color: Int, val nickname: String?) {
    val id get() = course.id
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: course.name
    val code: String get() = course.courseCode ?: ""
    val studentEnrollment get() = course.enrollments.firstOrNull { it.type == "student" } ?: course.enrollments.firstOrNull()
}

/** A file to upload, read lazily from a content stream. */
class UploadSource(val name: String, val size: Long, val mimeType: String, val open: () -> InputStream)

class Repository(val api: CanvasApi) {
    private val cacheLock = Mutex()
    private var colorsCache: Map<String, String>? = null
    private var nicknamesCache: Map<Long, String>? = null
    private val courseCache = HashMap<Long, Course>()
    var self: User? = null
        private set

    val domain: String get() = api.base.toString().trimEnd('/')

    // ----------------------------------------------------------------- user

    suspend fun profile(): User {
        val u = api.get("/api/v1/users/self/profile", User.serializer())
        self = u
        return u
    }

    // Only successful answers are remembered, so a failure while offline doesn't stick.
    suspend fun colors(force: Boolean = false): Map<String, String> = cacheLock.withLock {
        if (!force) colorsCache?.let { return it }
        try {
            api.get("/api/v1/users/self/colors", CustomColors.serializer()).colors.also { colorsCache = it }
        } catch (_: CanvasException) {
            colorsCache ?: emptyMap()
        }
    }

    suspend fun nicknames(force: Boolean = false): Map<Long, String> = cacheLock.withLock {
        if (!force) nicknamesCache?.let { return it }
        try {
            api.getAll("/api/v1/users/self/course_nicknames", CourseNickname.serializer())
                .filter { !it.nickname.isNullOrBlank() }.associate { it.courseId to it.nickname!! }
                .also { nicknamesCache = it }
        } catch (_: CanvasException) {
            nicknamesCache ?: emptyMap()
        }
    }

    fun courseColor(colors: Map<String, String>, courseId: Long): Int =
        parseColor(colors["course_$courseId"]) ?: Palette.fallback(courseId)

    // ----------------------------------------------------------------- courses

    private val courseIncludes = listOf(
        "include[]" to "term", "include[]" to "total_scores", "include[]" to "course_image",
        "include[]" to "favorites", "include[]" to "current_grading_period_scores",
    )

    /** Dashboard courses in the user's dashboard order (favourites, or all current if none). */
    suspend fun dashboardCourses(force: Boolean = false): List<CourseInfo> = coroutineScope {
        val colorsD = async { colors(force) }
        val nickD = async { nicknames(force) }
        val cardsD = async { runCatching { api.get("/api/v1/dashboard/dashboard_cards", ListSerializer(DashboardCard.serializer())) }.getOrNull() }
        val coursesD = async {
            api.getAll("/api/v1/courses", Course.serializer(), courseIncludes + ("enrollment_state" to "active"))
        }
        val courses = coursesD.await().filter { usable(it) }
        courses.forEach { courseCache[it.id] = it }
        val colors = colorsD.await()
        val nicks = nickD.await()
        val cards = cardsD.await()
        val byId = courses.associateBy { it.id }
        val ordered: List<Course> = if (cards != null) {
            cards.sortedBy { it.position ?: Int.MAX_VALUE }.mapNotNull { card ->
                byId[card.id] ?: (card.shortName ?: card.originalName)?.let { name ->
                    Course(id = card.id, name = name, courseCode = card.courseCode, imageUrl = card.image, isFavorite = card.isFavorited)
                }
            }
        } else {
            val favs = courses.filter { it.isFavorite }
            favs.ifEmpty { courses }
        }
        ordered.map { CourseInfo(it, courseColor(colors, it.id), nicks[it.id]) }
    }

    /** Every course the user can see, current and past. */
    suspend fun allCourses(): List<CourseInfo> = coroutineScope {
        val colorsD = async { colors() }
        val nickD = async { nicknames() }
        val courses = api.getAll(
            "/api/v1/courses", Course.serializer(),
            courseIncludes + listOf("include[]" to "concluded", "state[]" to "available", "state[]" to "completed"),
        ).filter { usable(it) }
        courses.forEach { courseCache[it.id] = it }
        val colors = colorsD.await()
        val nicks = nickD.await()
        courses.map { CourseInfo(it, courseColor(colors, it.id), nicks[it.id]) }
    }

    /** Courses closed by date come back as just an id with no name; nothing to show. */
    private fun usable(c: Course) = !c.accessRestricted && c.name.isNotBlank()

    suspend fun course(id: Long, force: Boolean = false): CourseInfo {
        val cached = courseCache[id]
        val c = if (cached != null && !force && cached.syllabusBody != null) cached else {
            api.get("/api/v1/courses/$id", Course.serializer(), courseIncludes + ("include[]" to "syllabus_body")).also { courseCache[id] = it }
        }
        return CourseInfo(c, courseColor(colors(), id), nicknames()[id])
    }

    /** Cheap lookup for list screens; falls back to loading. */
    suspend fun courseInfo(id: Long): CourseInfo {
        courseCache[id]?.let { return CourseInfo(it, courseColor(colors(), id), nicknames()[id]) }
        return course(id)
    }

    fun cachedCourseName(id: Long?): String? = id?.let { courseCache[it] }?.let { nicknamesCache?.get(it.id) ?: it.name }

    suspend fun ensureCoursesCached() {
        if (courseCache.isEmpty()) runCatching { dashboardCourses() }
    }

    suspend fun tabs(courseId: Long): List<Tab> =
        api.getAll("/api/v1/courses/$courseId/tabs", Tab.serializer())
            .filter { it.hidden != true }
            .sortedBy { it.position }

    suspend fun setFavorite(courseId: Long, favorite: Boolean) {
        api.sendRaw(if (favorite) "POST" else "DELETE", "/api/v1/users/self/favorites/courses/$courseId").close()
        courseCache[courseId]?.let { courseCache[courseId] = it.copy(isFavorite = favorite) }
    }

    suspend fun setNickname(courseId: Long, nickname: String) {
        if (nickname.isBlank()) api.sendRaw("DELETE", "/api/v1/users/self/course_nicknames/$courseId").close()
        else api.sendRaw("PUT", "/api/v1/users/self/course_nicknames/$courseId", listOf("nickname" to nickname.trim())).close()
        nicknamesCache = null
    }

    suspend fun setColor(courseId: Long, hex: String) {
        api.sendRaw("PUT", "/api/v1/users/self/colors/course_$courseId", listOf("hexcode" to hex.removePrefix("#"))).close()
        colorsCache = null
    }

    // ----------------------------------------------------------------- assignments

    suspend fun assignmentGroups(courseId: Long): List<AssignmentGroup> =
        api.getAll(
            "/api/v1/courses/$courseId/assignment_groups", AssignmentGroup.serializer(),
            listOf(
                "include[]" to "assignments", "include[]" to "submission",
                "include[]" to "discussion_topic", "exclude_response_fields[]" to "description",
                "exclude_response_fields[]" to "rubric", "override_assignment_dates" to "true",
            ),
        ).sortedBy { it.position }

    suspend fun assignment(courseId: Long, id: Long): Assignment =
        api.get(
            "/api/v1/courses/$courseId/assignments/$id", Assignment.serializer(),
            listOf("include[]" to "submission", "override_assignment_dates" to "true"),
        )

    suspend fun submission(courseId: Long, assignmentId: Long): Submission =
        api.get(
            "/api/v1/courses/$courseId/assignments/$assignmentId/submissions/self", Submission.serializer(),
            listOf("include[]" to "submission_comments", "include[]" to "rubric_assessment", "include[]" to "submission_history"),
        )

    suspend fun submitText(courseId: Long, assignmentId: Long, html: String, comment: String?): Submission =
        api.send(
            "POST", "/api/v1/courses/$courseId/assignments/$assignmentId/submissions",
            listOf("submission[submission_type]" to "online_text_entry", "submission[body]" to html) + commentParam(comment),
            Submission.serializer(),
        )

    suspend fun submitUrl(courseId: Long, assignmentId: Long, url: String, comment: String?): Submission =
        api.send(
            "POST", "/api/v1/courses/$courseId/assignments/$assignmentId/submissions",
            listOf("submission[submission_type]" to "online_url", "submission[url]" to url) + commentParam(comment),
            Submission.serializer(),
        )

    suspend fun submitFiles(
        courseId: Long, assignmentId: Long, files: List<UploadSource>, comment: String?,
        progress: (String) -> Unit = {},
    ): Submission {
        val ids = files.mapIndexed { i, f ->
            progress("Uploading ${f.name} (${i + 1}/${files.size})…")
            uploadFile("/api/v1/courses/$courseId/assignments/$assignmentId/submissions/self/files", f).id
        }
        progress("Submitting…")
        return api.send(
            "POST", "/api/v1/courses/$courseId/assignments/$assignmentId/submissions",
            listOf("submission[submission_type]" to "online_upload") + ids.map { "submission[file_ids][]" to it.toString() } + commentParam(comment),
            Submission.serializer(),
        )
    }

    suspend fun addSubmissionComment(courseId: Long, assignmentId: Long, text: String): Submission =
        api.send(
            "PUT", "/api/v1/courses/$courseId/assignments/$assignmentId/submissions/self",
            listOf("comment[text_comment]" to text), Submission.serializer(),
        )

    private fun commentParam(c: String?) = if (c.isNullOrBlank()) emptyList() else listOf("comment[text_comment]" to c.trim())

    /**
     * Canvas' three-step upload: ask for an upload ticket, POST the bytes to the
     * returned URL (no Canvas token there), then follow the confirmation redirect.
     */
    suspend fun uploadFile(ticketPath: String, file: UploadSource): CanvasFile {
        val ticket = api.send(
            "POST", ticketPath,
            listOf("name" to file.name, "size" to file.size.toString(), "content_type" to file.mimeType),
            UploadTicket.serializer(),
        )
        if (ticket.uploadUrl.isBlank()) throw CanvasException("Canvas did not provide an upload URL")
        val media = file.mimeType.toMediaTypeOrNull()
        val stream = object : RequestBody() {
            override fun contentType() = media
            override fun contentLength() = if (file.size > 0) file.size else -1
            override fun writeTo(sink: BufferedSink) {
                file.open().use { sink.writeAll(it.source()) }
            }
        }
        val form = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            ticket.uploadParams.forEach { (k, v) -> addFormDataPart(k, v.asParamString()) }
            addFormDataPart(ticket.fileParam ?: "file", file.name, stream)
        }.build()
        val resp = api.execute(Request.Builder().url(ticket.uploadUrl).post(form).build())
        return api.decode(resp, CanvasFile.serializer()).also {
            if (it.id == 0L) throw CanvasException("Upload of ${file.name} failed")
        }
    }

    // ----------------------------------------------------------------- discussions & announcements

    suspend fun announcements(courseId: Long): List<DiscussionTopic> =
        api.getAll(
            "/api/v1/courses/$courseId/discussion_topics", DiscussionTopic.serializer(),
            listOf("only_announcements" to "true"), maxPages = 5,
        )

    suspend fun discussions(courseId: Long): List<DiscussionTopic> =
        api.getAll("/api/v1/courses/$courseId/discussion_topics", DiscussionTopic.serializer(), maxPages = 10)
            .sortedWith(compareByDescending<DiscussionTopic> { it.pinned }.thenByDescending { it.lastReplyAt ?: it.postedAt ?: "" })

    suspend fun topic(courseId: Long, topicId: Long): DiscussionTopic =
        api.get("/api/v1/courses/$courseId/discussion_topics/$topicId", DiscussionTopic.serializer())

    suspend fun topicView(courseId: Long, topicId: Long): DiscussionView =
        api.get(
            "/api/v1/courses/$courseId/discussion_topics/$topicId/view", DiscussionView.serializer(),
            listOf("include_new_entries" to "1"),
        )

    suspend fun reply(courseId: Long, topicId: Long, parentEntryId: Long?, html: String): DiscussionEntry {
        val path = if (parentEntryId == null) "/api/v1/courses/$courseId/discussion_topics/$topicId/entries"
        else "/api/v1/courses/$courseId/discussion_topics/$topicId/entries/$parentEntryId/replies"
        return api.send("POST", path, listOf("message" to html), DiscussionEntry.serializer())
    }

    /** Best effort; silently skipped in read-only mode. */
    suspend fun markTopicRead(courseId: Long, topicId: Long) {
        if (api.readOnly) return
        runCatching { api.sendRaw("PUT", "/api/v1/courses/$courseId/discussion_topics/$topicId/read_all").close() }
    }

    // ----------------------------------------------------------------- pages, modules, syllabus, quizzes, people

    suspend fun pages(courseId: Long): List<Page> =
        api.getAll("/api/v1/courses/$courseId/pages", Page.serializer(), listOf("sort" to "title"))

    suspend fun page(courseId: Long, url: String): Page =
        api.get("/api/v1/courses/$courseId/pages/${encodeSegment(url)}", Page.serializer())

    suspend fun frontPage(courseId: Long): Page = api.get("/api/v1/courses/$courseId/front_page", Page.serializer())

    suspend fun modules(courseId: Long): List<Module> = coroutineScope {
        val mods = api.getAll(
            "/api/v1/courses/$courseId/modules", Module.serializer(),
            listOf("include[]" to "items", "include[]" to "content_details"),
        )
        // Large modules come back without items; fetch those separately.
        mods.map { m ->
            async {
                if (m.items != null) m else m.copy(
                    items = runCatching {
                        api.getAll(
                            "/api/v1/courses/$courseId/modules/${m.id}/items", ModuleItem.serializer(),
                            listOf("include[]" to "content_details"),
                        )
                    }.getOrDefault(emptyList()),
                )
            }
        }.awaitAll().sortedBy { it.position }
    }

    suspend fun markModuleItemRead(courseId: Long, moduleId: Long, itemId: Long) {
        if (api.readOnly) return
        runCatching { api.sendRaw("POST", "/api/v1/courses/$courseId/modules/$moduleId/items/$itemId/mark_read").close() }
    }

    suspend fun quizzes(courseId: Long): List<Quiz> =
        api.getAll("/api/v1/courses/$courseId/quizzes", Quiz.serializer())

    suspend fun people(courseId: Long): List<User> =
        api.getAll(
            "/api/v1/courses/$courseId/users", User.serializer(),
            listOf("include[]" to "avatar_url", "include[]" to "enrollments", "include[]" to "pronouns"),
        )

    // ----------------------------------------------------------------- files

    suspend fun rootFolder(courseId: Long?): Folder =
        if (courseId == null) api.get("/api/v1/users/self/folders/root", Folder.serializer())
        else api.get("/api/v1/courses/$courseId/folders/root", Folder.serializer())

    suspend fun folder(id: Long): Folder = api.get("/api/v1/folders/$id", Folder.serializer())

    suspend fun folderContents(folderId: Long): Pair<List<Folder>, List<CanvasFile>> = coroutineScope {
        val f = async { api.getAll("/api/v1/folders/$folderId/folders", Folder.serializer()) }
        val files = async { api.getAll("/api/v1/folders/$folderId/files", CanvasFile.serializer()) }
        f.await().filter { !it.hiddenForUser }.sortedBy { it.name.lowercase() } to
            files.await().filter { !it.hidden }.sortedBy { it.name.lowercase() }
    }

    /** Every folder of a course in one flat list (for offline sync). */
    suspend fun courseFolders(courseId: Long): List<Folder> =
        api.getAll("/api/v1/courses/$courseId/folders", Folder.serializer())

    /** Every file of a course in one flat list (for offline sync). */
    suspend fun courseFiles(courseId: Long): List<CanvasFile> =
        api.getAll("/api/v1/courses/$courseId/files", CanvasFile.serializer())

    /**
     * Save the answers the Files screens ask for (root, each folder and its contents),
     * built from the two flat lists, so browsing works offline without a request per folder.
     */
    suspend fun saveFolderTree(courseId: Long, folders: List<Folder>, files: List<CanvasFile>) {
        val folderJson = Folder.serializer()
        val folderList = ListSerializer(Folder.serializer())
        val fileList = ListSerializer(CanvasFile.serializer())
        val childFolders = folders.groupBy { it.parentFolderId }
        val childFiles = files.groupBy { it.folderId }
        folders.firstOrNull { it.parentFolderId == null }?.let { root ->
            api.saveForOffline(api.url("/api/v1/courses/$courseId/folders/root"), CanvasJson.encodeToString(folderJson, root))
        }
        for (f in folders) {
            api.saveForOffline(api.url("/api/v1/folders/${f.id}"), CanvasJson.encodeToString(folderJson, f))
            api.saveForOffline(api.listUrl("/api/v1/folders/${f.id}/folders"), CanvasJson.encodeToString(folderList, childFolders[f.id].orEmpty()))
            api.saveForOffline(api.listUrl("/api/v1/folders/${f.id}/files"), CanvasJson.encodeToString(fileList, childFiles[f.id].orEmpty()))
        }
    }

    suspend fun file(id: Long, courseId: Long? = null): CanvasFile =
        if (courseId != null) {
            runCatching { api.get("/api/v1/courses/$courseId/files/$id", CanvasFile.serializer()) }
                .getOrElse { api.get("/api/v1/files/$id", CanvasFile.serializer()) }
        } else api.get("/api/v1/files/$id", CanvasFile.serializer())

    // ----------------------------------------------------------------- inbox

    suspend fun conversations(scope: String, courseId: Long?): List<Conversation> {
        val params = buildList {
            if (scope != "inbox") add("scope" to scope)
            courseId?.let { add("filter[]" to "course_$it") }
            add("include[]" to "participant_avatars")
            add("per_page" to "50")
        }
        return api.getAll("/api/v1/conversations", Conversation.serializer(), params, maxPages = 4)
    }

    /** Opening a message marks it read, except in read-only mode or when saving it for offline. */
    suspend fun conversation(id: Long, markRead: Boolean = !api.readOnly): Conversation =
        api.get(
            "/api/v1/conversations/$id", Conversation.serializer(),
            if (!markRead) listOf("auto_mark_as_read" to "false") else emptyList(),
        )

    suspend fun replyConversation(id: Long, body: String): Conversation =
        api.send("POST", "/api/v1/conversations/$id/add_message", listOf("body" to body), Conversation.serializer())

    suspend fun updateConversation(id: Long, starred: Boolean? = null, state: String? = null): Conversation {
        val form = buildList {
            starred?.let { add("conversation[starred]" to it.toString()) }
            state?.let { add("conversation[workflow_state]" to it) }
        }
        return api.send("PUT", "/api/v1/conversations/$id", form, Conversation.serializer())
    }

    suspend fun createConversation(courseId: Long?, recipients: List<String>, subject: String, body: String): List<Conversation> {
        val form = buildList {
            recipients.forEach { add("recipients[]" to it) }
            add("subject" to subject)
            add("body" to body)
            add("group_conversation" to "true")
            courseId?.let { add("context_code" to "course_$it") }
        }
        return api.send("POST", "/api/v1/conversations", form, ListSerializer(Conversation.serializer()))
    }

    suspend fun searchRecipients(courseId: Long, query: String): List<Recipient> {
        val params = buildList {
            add("context" to "course_$courseId")
            add("search" to query)
            add("per_page" to "50")
            add("permissions[]" to "send_messages_all")
        }
        return api.get("/api/v1/search/recipients", ListSerializer(Recipient.serializer()), params)
    }

    // ----------------------------------------------------------------- planner, calendar, activity

    /**
     * Planner items in a date range. The URL changes every day, so offline the answer comes
     * from the wide snapshot the offline sync keeps (see [savePlannerSnapshot]).
     */
    suspend fun planner(start: LocalDate, endExclusive: LocalDate): List<PlannerItem> {
        val zone = ZoneId.systemDefault()
        val from = start.atStartOfDay(zone).toInstant()
        val to = endExclusive.atStartOfDay(zone).toInstant()
        return try {
            api.getAll(
                "/api/v1/planner/items", PlannerItem.serializer(),
                listOf("start_date" to from.toString(), "end_date" to to.toString()),
                maxPages = 10,
            )
        } catch (e: NetworkException) {
            if (currentCoroutineContext()[CachePolicy]?.networkOnly == true) throw e
            val snap = api.savedCopy(plannerSnapshotUrl(), ListSerializer(PlannerItem.serializer())) ?: throw e
            snap.filter { item ->
                val t = item.plannableDate.toInstant() ?: return@filter false
                !t.isBefore(from) && t.isBefore(to)
            }
        }
    }

    private fun plannerSnapshotUrl() = api.url("/api/v1/planner/items", listOf("canvos_offline_snapshot" to "1"))

    /** Fetch a wide planner window and keep it for offline use. */
    suspend fun savePlannerSnapshot(start: LocalDate, endExclusive: LocalDate): List<PlannerItem> {
        val items = planner(start, endExclusive)
        api.saveForOffline(plannerSnapshotUrl(), CanvasJson.encodeToString(ListSerializer(PlannerItem.serializer()), items))
        return items
    }

    /** Toggle the planner "done" checkbox. Returns the new override. */
    suspend fun setPlannerComplete(item: PlannerItem, complete: Boolean): PlannerOverride {
        val existing = item.plannerOverride
        return if (existing != null && existing.id != 0L) {
            api.send("PUT", "/api/v1/planner/overrides/${existing.id}", listOf("marked_complete" to complete.toString()), PlannerOverride.serializer())
        } else {
            api.send(
                "POST", "/api/v1/planner/overrides",
                listOf(
                    "plannable_type" to item.plannableType, "plannable_id" to item.plannableId.toString(),
                    "marked_complete" to complete.toString(),
                ),
                PlannerOverride.serializer(),
            )
        }
    }

    suspend fun calendarEvent(id: String): CalendarEvent = api.get("/api/v1/calendar_events/$id", CalendarEvent.serializer())

    suspend fun activityStream(courseId: Long? = null): List<StreamItem> =
        if (courseId == null) api.get("/api/v1/users/self/activity_stream", ListSerializer(StreamItem.serializer()), listOf("per_page" to "60"))
        else api.get("/api/v1/courses/$courseId/activity_stream", ListSerializer(StreamItem.serializer()), listOf("per_page" to "60"))

    // ----------------------------------------------------------------- web sessions

    /** A one-time URL that logs the web view into Canvas and lands on [returnTo]. */
    suspend fun sessionUrl(returnTo: String): String =
        api.get("/login/session_token", SessionToken.serializer(), listOf("return_to" to returnTo)).sessionUrl

    fun isReadOnly() = api.readOnly

    companion object {
        fun encodeSegment(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}

internal fun parseColor(hex: String?): Int? {
    if (hex.isNullOrBlank()) return null
    val h = hex.trim().removePrefix("#")
    return try {
        when (h.length) {
            6 -> (0xFF000000 or h.toLong(16)).toInt()
            3 -> {
                val r = h[0].digitToInt(16); val g = h[1].digitToInt(16); val b = h[2].digitToInt(16)
                (0xFF000000 or ((r * 17L) shl 16) or ((g * 17L) shl 8) or (b * 17L)).toInt()
            }
            8 -> h.toLong(16).toInt()
            else -> null
        }
    } catch (_: NumberFormatException) {
        null
    }
}

object Palette {
    /** Canvas' own course colour choices. */
    val colors = listOf(
        0xFFBD3C14, 0xFFFF2717, 0xFFE71F63, 0xFF8F3E97, 0xFF65499D, 0xFF4554A4, 0xFF1770AB,
        0xFF0B9BE3, 0xFF06A3B7, 0xFF009688, 0xFF009606, 0xFF8D9900, 0xFFD97900, 0xFFFD5D10, 0xFF6E6E6E,
    ).map { it.toInt() }

    fun fallback(id: Long): Int = colors[(id % colors.size).toInt().let { if (it < 0) -it else it }]
}
