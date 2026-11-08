package com.rafat.canvos

import com.rafat.canvos.data.Alerts
import com.rafat.canvos.data.Notifier
import com.rafat.canvos.data.Repository
import com.rafat.canvos.data.api.CanvasApi
import com.rafat.canvos.data.api.CanvasException
import com.rafat.canvos.data.api.CanvasFile
import com.rafat.canvos.data.api.CanvasJson
import com.rafat.canvos.data.api.Conversation
import com.rafat.canvos.data.api.ConversationParticipant
import com.rafat.canvos.data.api.Course
import com.rafat.canvos.data.api.Folder
import com.rafat.canvos.data.api.NetworkException
import com.rafat.canvos.data.api.NetworkOnly
import com.rafat.canvos.data.api.NotSavedException
import com.rafat.canvos.data.api.PlannerItem
import com.rafat.canvos.data.api.StreamItem
import com.rafat.canvos.data.api.StreamSubmissionAssignment
import com.rafat.canvos.data.api.SubmissionComment
import com.rafat.canvos.data.api.UnauthorizedException
import com.rafat.canvos.data.offline.ResponseCache
import com.rafat.canvos.data.offline.Resources
import com.rafat.canvos.data.offline.cacheKey
import com.rafat.canvos.data.offline.folderPathOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OfflineTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    @Test
    fun savedCopyIsUsedWhenOfflineAndOnlyThen() = runBlocking {
        val s = MockWebServer(); s.start()
        val u = s.url("/").toString().trimEnd('/')
        var online = true
        val api = CanvasApi(u, "t", cache = ResponseCache(tmp.newFolder()), hasNetwork = { online })
        s.enqueue(ok("""[{"id":1,"name":"Physics"}]"""))
        assertEquals("Physics", api.getAll("/api/v1/courses", Course.serializer()).single().name)

        // No network: answered from disk without a request.
        online = false
        assertEquals("Physics", api.getAll("/api/v1/courses", Course.serializer()).single().name)
        assertEquals(1, s.requestCount)
        try {
            api.get("/api/v1/courses/9", Course.serializer()); fail()
        } catch (_: NotSavedException) {}

        // Canvas failing (5xx) also falls back; background jobs (NetworkOnly) never do.
        online = true
        s.enqueue(MockResponse.Builder().code(503).body("down").build())
        assertEquals("Physics", api.getAll("/api/v1/courses", Course.serializer()).single().name)
        s.enqueue(MockResponse.Builder().code(503).body("down").build())
        try {
            withContext(NetworkOnly) { api.getAll("/api/v1/courses", Course.serializer()) }; fail()
        } catch (e: CanvasException) { assertEquals(503, e.status) }

        // A 404 is a real answer: shown as an error and the stale copy is dropped.
        s.enqueue(MockResponse.Builder().code(404).body("""{"message":"gone"}""").build())
        try {
            api.getAll("/api/v1/courses", Course.serializer()); fail()
        } catch (e: CanvasException) { assertEquals(404, e.status) }
        online = false
        try {
            api.getAll("/api/v1/courses", Course.serializer()); fail()
        } catch (_: NotSavedException) {}

        // Connection refused once the server is gone -> saved copy (re-saved first).
        online = true
        s.enqueue(ok("""{"id":3,"name":"Chem"}"""))
        api.get("/api/v1/courses/3", Course.serializer())
        s.close()
        assertEquals("Chem", api.get("/api/v1/courses/3", Course.serializer()).name)
    }

    @Test
    fun allPagesAreSavedAsOneList() = runBlocking {
        val s = MockWebServer(); s.start()
        val u = s.url("/").toString().trimEnd('/')
        var online = true
        val api = CanvasApi(u, "t", cache = ResponseCache(tmp.newFolder()), hasNetwork = { online })
        s.enqueue(MockResponse.Builder().code(200).addHeader("Link", "<$u/api/v1/courses?page=2&per_page=100>; rel=\"next\"").body("""[{"id":1,"name":"A"}]""").build())
        s.enqueue(MockResponse.Builder().code(200).addHeader("Link", "<$u/api/v1/courses?page=3&per_page=100>; rel=\"next\"").body("[]").build())
        s.enqueue(ok("""[{"id":2,"name":"B"},{"id":3,"name":"C"}]"""))
        assertEquals(listOf(1L, 2L, 3L), api.getAll("/api/v1/courses", Course.serializer()).map { it.id })
        online = false
        assertEquals(listOf(1L, 2L, 3L), api.getAll("/api/v1/courses", Course.serializer()).map { it.id })
        s.close()
    }

    @Test
    fun cacheKeyIgnoresMarkAsRead() {
        val a = "https://x.instructure.com/api/v1/conversations/5?auto_mark_as_read=false".toHttpUrl()
        val b = "https://x.instructure.com/api/v1/conversations/5".toHttpUrl()
        val c = "https://x.instructure.com/api/v1/conversations/6".toHttpUrl()
        assertEquals(cacheKey(a), cacheKey(b))
        assertFalse(cacheKey(b) == cacheKey(c))
        assertEquals(CanvasApi.mergeJsonArrays(listOf("[1,2]", " [] ", "[3]")), "[1,2,3]")
    }

    @Test
    fun permissionDenied401IsNotAnExpiredToken() = runBlocking {
        val s = MockWebServer(); s.start()
        val api = CanvasApi(s.url("/").toString().trimEnd('/'), "t")
        s.enqueue(MockResponse.Builder().code(401).body("""{"status":"unauthorized","errors":[{"message":"user not authorized to perform that action"}]}""").build())
        try {
            api.get("/api/v1/courses/1/pages", Course.serializer()); fail()
        } catch (e: UnauthorizedException) {
            fail("permission problem must not log the user out")
        } catch (e: CanvasException) { assertEquals(401, e.status) }
        s.enqueue(MockResponse.Builder().code(401).addHeader("WWW-Authenticate", "Bearer realm=\"canvas-lms\"").body("""{"errors":[{"message":"user not authorized to perform that action"}]}""").build())
        try {
            api.get("/api/v1/users/self", Course.serializer()); fail()
        } catch (_: UnauthorizedException) {}
        s.close()
    }

    @Test
    fun plannerOfflineComesFromTheSnapshot() = runBlocking {
        val s = MockWebServer(); s.start()
        var online = true
        val repo = Repository(CanvasApi(s.url("/").toString().trimEnd('/'), "t", cache = ResponseCache(tmp.newFolder()), hasNetwork = { online }))
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toString()
        s.enqueue(ok("""[
            {"plannable_id":1,"plannable_type":"assignment","plannable_date":"${at(today)}","plannable":{"title":"Today"}},
            {"plannable_id":2,"plannable_type":"assignment","plannable_date":"${at(today.plusDays(10))}","plannable":{"title":"Later"}},
            {"plannable_id":3,"plannable_type":"quiz","plannable_date":"${at(today.minusDays(20))}","plannable":{"title":"Past"}}
        ]"""))
        assertEquals(3, repo.savePlannerSnapshot(today.minusDays(30), today.plusDays(60)).size)
        online = false
        // A range the app never asked for online, like tomorrow's "to do" window.
        assertEquals(listOf("Today", "Later"), repo.planner(today, today.plusDays(14)).map { it.title })
        assertEquals(listOf("Past"), repo.planner(today.minusDays(21), today).map { it.title })
        // Background checks must not mistake the snapshot for live data.
        s.close()
        try {
            withContext(NetworkOnly) { repo.planner(today, today.plusDays(1)) }; fail()
        } catch (_: NetworkException) {}
    }

    @Test
    fun folderTreeBuiltFromFlatListsWorksOffline() = runBlocking {
        val s = MockWebServer(); s.start()
        var online = false
        val repo = Repository(CanvasApi(s.url("/").toString().trimEnd('/'), "t", cache = ResponseCache(tmp.newFolder()), hasNetwork = { online }))
        val folders = listOf(
            Folder(id = 10, name = "course files", fullName = "course files", filesCount = 1, foldersCount = 1),
            Folder(id = 11, name = "Week 1", fullName = "course files/Week 1", parentFolderId = 10, filesCount = 1),
        )
        val files = listOf(
            CanvasFile(id = 100, displayName = "syllabus.pdf", folderId = 10, url = "u"),
            CanvasFile(id = 101, displayName = "slides.pptx", folderId = 11, url = "u"),
        )
        repo.saveFolderTree(7, folders, files)
        val root = repo.rootFolder(7)
        assertEquals(10L, root.id)
        val (subfolders, rootFiles) = repo.folderContents(root.id)
        assertEquals(listOf("Week 1"), subfolders.map { it.name })
        assertEquals(listOf("syllabus.pdf"), rootFiles.map { it.name })
        assertEquals(listOf("slides.pptx"), repo.folderContents(11).second.map { it.name })
        assertEquals("Week 1", repo.folder(11).name)
        assertEquals("Week 1", folderPathOf(folders[1]))
        assertEquals("", folderPathOf(folders[0]))
        assertEquals(0, s.requestCount)
        online = true
        s.close()
    }

    @Test
    fun htmlImagesAndFileLinks() {
        val base = "https://x.instructure.com".toHttpUrl()
        val html = """<p><img src="/courses/1/files/5/preview?a=1&amp;b=2" alt=""><img class="x" src='https://cdn.example.com/a.png'>
            <a href="/courses/1/files/77?wrap=1">notes</a> <a href="https://x.instructure.com/files/78/download">x</a>
            <a href="https://other.example.com/files/99">ext</a></p>"""
        assertEquals(
            listOf("https://x.instructure.com/courses/1/files/5/preview?a=1&b=2", "https://cdn.example.com/a.png"),
            Resources.imageUrls(html, base).map { it.toString() },
        )
        assertEquals(setOf(77L, 78L), Resources.linkedFileIds(html, base))
    }

    @Test
    fun streamAlerts() {
        val items = listOf(
            StreamItem(id = 1, type = "Announcement", announcementId = 50, title = "Midterm moved", courseId = 3, updatedAt = "2026-11-03T10:00:00Z", htmlUrl = "/courses/3/announcements/50"),
            StreamItem(
                id = 2, type = "Submission", title = "Lab 1", score = 8.0, grade = "8", courseId = 3, updatedAt = "2026-11-03T11:00:00Z",
                assignment = StreamSubmissionAssignment("Lab 1", 10.0),
                submissionComments = listOf(
                    SubmissionComment(id = 7, authorId = 99, authorName = "Dr. K", comment = "Nice work"),
                    SubmissionComment(id = 8, authorId = 42, authorName = "Me", comment = "thanks"),
                ),
            ),
            StreamItem(id = 3, type = "Conversation", title = "hi"),
        )
        val alerts = Alerts.fromStream(items, selfId = 42) { if (it == 3L) "CSE115" else null }
        assertEquals(listOf("ann:50", "grade:2:8:8.0", "comment:2:7"), alerts.map { it.key })
        assertEquals("Midterm moved", alerts[0].title)
        assertEquals("CSE115", alerts[0].text)
        assertEquals(Notifier.CH_ANNOUNCEMENTS, alerts[0].channel)
        assertEquals("CSE115 · Grade 8 / 10", alerts[1].text)
        assertEquals("CSE115 · Dr. K: Nice work", alerts[2].text)

        val convs = listOf(
            Conversation(id = 5, subject = "Project", workflowState = "unread", lastMessage = "See you", lastMessageAt = "2026-11-03T12:00:00Z",
                participants = listOf(ConversationParticipant(42, "Me"), ConversationParticipant(9, "Ayesha"))),
            // Own message last: nothing new to tell.
            Conversation(id = 6, workflowState = "unread", lastMessageAt = "2026-11-03T12:00:00Z", lastAuthoredMessageAt = "2026-11-03T12:00:00Z"),
            Conversation(id = 7, workflowState = "read", lastMessageAt = "2026-11-03T12:00:00Z"),
        )
        val c = Alerts.fromConversations(convs, selfId = 42)
        assertEquals(listOf("conv:5:2026-11-03T12:00:00Z"), c.map { it.key })
        assertEquals("Ayesha: See you", c[0].text)
        assertEquals(5L, c[0].conversationId)
    }

    @Test
    fun dueRemindersAndWhatIsShown() {
        val now = Instant.parse("2026-11-04T12:00:00Z")
        fun item(id: Long, due: Instant, done: Boolean = false) = PlannerItem(
            plannableId = id, plannableType = "assignment", plannableDate = due.toString(),
            plannable = com.rafat.canvos.data.api.Plannable(title = "A$id", dueAt = due.toString()),
            submissions = if (done) buildJsonObject { put("submitted", JsonPrimitive(true)) } else JsonPrimitive(false),
        )
        val items = listOf(
            item(1, now.plus(Duration.ofMinutes(30))), // both reminders due -> only the 1-hour one
            item(2, now.plus(Duration.ofHours(20))), // 1-day reminder
            item(3, now.plus(Duration.ofDays(3))), // too early
            item(4, now.plus(Duration.ofMinutes(10)), done = true), // handed in
            item(5, now.minus(Duration.ofMinutes(5))), // already past
        )
        val due = Alerts.dueSoon(items, now, listOf(1440, 60))
        assertEquals(2, due.size)
        assertTrue(due[0].key.endsWith(":60"))
        assertEquals("Due in 30 min: A1", due[0].title)
        assertTrue(due[1].key.endsWith(":1440"))
        assertEquals("Due in 20 hours: A2", due[1].title)
        assertEquals("in 1 hour", Alerts.untilText(Duration.ofMinutes(65)))
        assertEquals("in 2 days", Alerts.untilText(Duration.ofHours(47)))

        val t = now.toEpochMilli()
        val news = listOf(
            com.rafat.canvos.data.Alert("ann:1", Notifier.CH_ANNOUNCEMENTS, "a", "", time = t - 60_000),
            com.rafat.canvos.data.Alert("ann:2", Notifier.CH_ANNOUNCEMENTS, "b", "", time = t - 10L * 86_400_000), // too old
            com.rafat.canvos.data.Alert("ann:3", Notifier.CH_ANNOUNCEMENTS, "c", "", time = t),
        )
        // First check: only reminders, the rest becomes the starting point.
        assertEquals(due.map { it.key }, Alerts.toShow(news, due, emptySet(), firstRun = true, now = t).map { it.key })
        val later = Alerts.toShow(news, due, setOf("ann:3", due[1].key), firstRun = false, now = t)
        assertEquals(listOf("ann:1", due[0].key), later.map { it.key })
    }

    @Test
    fun localFileManifestRoundTrips() {
        val e = com.rafat.canvos.data.offline.LocalFile(CanvasFile(id = 5, displayName = "a.pdf", size = 3, updatedAt = "x"), 1, "Physics", "Physics/a.pdf", "3|x")
        val json = CanvasJson.encodeToString(com.rafat.canvos.data.offline.LocalFile.serializer(), e)
        assertEquals(e, CanvasJson.decodeFromString(com.rafat.canvos.data.offline.LocalFile.serializer(), json))
    }
}
