package com.rafat.canvos

import com.rafat.canvos.data.Repository
import com.rafat.canvos.data.UploadSource
import com.rafat.canvos.data.api.Assignment
import com.rafat.canvos.data.api.CanvasApi
import com.rafat.canvos.data.api.CanvasJson
import com.rafat.canvos.data.api.PlannerItem
import com.rafat.canvos.data.api.Submission
import com.rafat.canvos.data.api.UnauthorizedException
import com.rafat.canvos.data.parseColor
import com.rafat.canvos.ui.course.bucketByDue
import com.rafat.canvos.ui.course.textToHtml
import com.rafat.canvos.ui.login.cleanToken
import com.rafat.canvos.ui.nav.AnnouncementsRoute
import com.rafat.canvos.ui.nav.AssignmentRoute
import com.rafat.canvos.ui.nav.CanvasLinks
import com.rafat.canvos.ui.nav.ConversationRoute
import com.rafat.canvos.ui.nav.CourseRoute
import com.rafat.canvos.ui.nav.DiscussionRoute
import com.rafat.canvos.ui.nav.FilesRoute
import com.rafat.canvos.ui.nav.LinkAction
import com.rafat.canvos.ui.nav.PageRoute
import com.rafat.canvos.ui.nav.SubmissionRoute
import com.rafat.canvos.ui.nav.SyllabusRoute
import com.rafat.canvos.ui.nav.WebRoute
import com.rafat.canvos.util.SubmissionStatus
import com.rafat.canvos.util.gradeText
import com.rafat.canvos.util.pretty
import com.rafat.canvos.util.status
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LogicTest {
    private val base = "https://school.instructure.com".toHttpUrl()

    private fun nav(url: String) = (CanvasLinks.resolve(url, base) as LinkAction.Navigate).route

    @Test
    fun links() {
        assertEquals(CourseRoute(5), nav("/courses/5"))
        assertEquals(AssignmentRoute(5, 9), nav("https://school.instructure.com/courses/5/assignments/9"))
        assertEquals(SubmissionRoute(5, 9), nav("/courses/5/assignments/9/submissions/45779"))
        assertEquals(SyllabusRoute(5), nav("/courses/5/assignments/syllabus"))
        assertEquals(DiscussionRoute(5, 7, true), nav("/courses/5/announcements/7"))
        assertEquals(AnnouncementsRoute(5), nav("/courses/5/announcements"))
        assertEquals(DiscussionRoute(5, 7), nav("/courses/5/discussion_topics/7?module_item_id=3"))
        assertEquals(PageRoute(5, "welcome-note"), nav("/courses/5/pages/welcome-note"))
        assertEquals(PageRoute(5), nav("/courses/5/wiki"))
        assertEquals(FilesRoute(5), nav("/courses/5/files"))
        assertEquals(ConversationRoute(12), nav("/conversations/12"))
        assertTrue(nav("/courses/5/quizzes/3") is WebRoute)
        assertTrue(nav("/courses/5/external_tools/1") is WebRoute)
        assertEquals(LinkAction.OpenFile(77, 5), CanvasLinks.resolve("/courses/5/files/77/download?wrap=1", base))
        assertEquals(LinkAction.OpenFile(77, null), CanvasLinks.resolve("/users/3/files/77/preview", base))
        assertTrue(CanvasLinks.resolve("https://youtube.com/watch?v=x", base) is LinkAction.External)
    }

    @Test
    fun schoolAndToken() {
        assertEquals("https://northsouth.instructure.com", com.rafat.canvos.ui.login.SCHOOL_URL)
        assertEquals("1~abc", cleanToken(" Bearer 1~abc \n"))
        assertEquals("1~abc", cleanToken("1~abc"))
    }

    @Test
    fun paginationAndErrors() {
        val h = "<https://x/api/v1/c?page=1>; rel=\"current\",<https://x/api/v1/c?page=2&per_page=100>; rel=\"next\",<https://x/api/v1/c?page=1>; rel=\"first\""
        assertEquals("https://x/api/v1/c?page=2&per_page=100", CanvasApi.nextLink(h))
        assertNull(CanvasApi.nextLink("<https://x?page=1>; rel=\"current\""))
        assertEquals("Invalid access token.", CanvasApi.errorMessage("""{"errors":[{"message":"Invalid access token."}]}"""))
        assertEquals("is too long", CanvasApi.errorMessage("""{"errors":{"body":[{"message":"is too long"}]}}"""))
        assertEquals("nope", CanvasApi.errorMessage("""{"message":"nope"}"""))
        assertNull(CanvasApi.errorMessage("<html>"))
    }

    @Test
    fun formatting() {
        assertEquals("8.5", 8.5.pretty()); assertEquals("10", 10.0.pretty()); assertEquals("3.33", (10.0 / 3).pretty())
        assertEquals("8 / 10", gradeText(Submission(score = 8.0, grade = "8", workflowState = "graded"), 10.0, "points"))
        assertEquals("A (95 / 100)", gradeText(Submission(score = 95.0, grade = "A", workflowState = "graded"), 100.0, "letter_grade"))
        assertEquals("Complete", gradeText(Submission(score = 1.0, grade = "complete"), 1.0, "pass_fail"))
        assertEquals("Excused", gradeText(Submission(excused = true), 10.0, "points"))
        assertNull(gradeText(Submission(workflowState = "unsubmitted"), 10.0, "points"))
        assertEquals("<p>a &lt;b&gt;<br>c</p><p>d</p>", textToHtml("a <b>\nc\n\n d"))
        assertEquals(0xFF3C4F36.toInt(), parseColor("#3C4F36"))
        assertEquals(0xFFFFFFFF.toInt(), parseColor("fff"))
        assertNull(parseColor("zz"))
    }

    @Test
    fun assignmentStatus() {
        val past = Instant.now().minusSeconds(86_400).toString()
        val future = Instant.now().plusSeconds(86_400).toString()
        val upload = listOf("online_upload")
        assertEquals(SubmissionStatus.NOT_SUBMITTED, Assignment(dueAt = future, submissionTypes = upload, submission = Submission(workflowState = "unsubmitted")).status())
        assertEquals(SubmissionStatus.MISSING, Assignment(dueAt = past, submissionTypes = upload, submission = Submission(workflowState = "unsubmitted", missing = true)).status())
        assertEquals(SubmissionStatus.SUBMITTED, Assignment(dueAt = future, submissionTypes = upload, submission = Submission(workflowState = "submitted", submittedAt = past)).status())
        assertEquals(SubmissionStatus.LATE, Assignment(dueAt = past, submissionTypes = upload, submission = Submission(workflowState = "submitted", submittedAt = past, late = true)).status())
        assertEquals(SubmissionStatus.GRADED, Assignment(dueAt = past, submissionTypes = upload, submission = Submission(workflowState = "graded", score = 5.0, grade = "5", submittedAt = past)).status())
        assertEquals(SubmissionStatus.NONE, Assignment(submissionTypes = listOf("none")).status())

        val b = bucketByDue(listOf(
            Assignment(id = 1, name = "over", dueAt = past, submissionTypes = upload, submission = Submission(workflowState = "unsubmitted", missing = true)),
            Assignment(id = 2, name = "up", dueAt = future, submissionTypes = upload),
            Assignment(id = 3, name = "nodate", submissionTypes = upload),
            Assignment(id = 4, name = "done", dueAt = past, submissionTypes = upload, submission = Submission(workflowState = "graded", score = 1.0, grade = "1", submittedAt = past)),
        )).toMap()
        assertEquals(listOf(1L), b["Overdue"]!!.map { it.id })
        assertEquals(listOf(2L), b["Upcoming"]!!.map { it.id })
        assertEquals(listOf(3L), b["Undated"]!!.map { it.id })
        assertEquals(listOf(4L), b["Past"]!!.map { it.id })
    }

    @Test
    fun plannerSubmissionsCanBeFalseOrObject() {
        val a = CanvasJson.decodeFromString(PlannerItem.serializer(), """{"plannable_id":1,"plannable_type":"announcement","plannable":{"id":1,"title":"Hi"},"submissions":false}""")
        assertEquals(false, a.isComplete)
        val b = CanvasJson.decodeFromString(PlannerItem.serializer(), """{"plannable_id":2,"plannable_type":"assignment","plannable":{"id":2,"name":"Essay"},"submissions":{"submitted":true,"graded":false,"missing":null}}""")
        assertTrue(b.isSubmitted); assertTrue(b.isComplete); assertEquals("Essay", b.title)
        val c = CanvasJson.decodeFromString(PlannerItem.serializer(), """{"plannable_id":3,"plannable_type":"assignment","plannable":{"id":3},"submissions":{"submitted":true},"planner_override":{"id":9,"marked_complete":false}}""")
        assertEquals(false, c.isComplete) // explicit override wins
    }

    @Test
    fun fileUploadKeepsTokenOnCanvasOnly() = runBlocking {
        val canvas = MockWebServer(); canvas.start()
        val storage = MockWebServer(); storage.start() // different port = different host
        val canvasUrl = canvas.url("/").toString().trimEnd('/')
        canvas.enqueue(MockResponse.Builder().code(200).body(
            """{"upload_url":"${storage.url("/upload")}","upload_params":{"key":"abc","policy":"p"},"file_param":"file"}""").build())
        storage.enqueue(MockResponse.Builder().code(303).addHeader("Location", "$canvasUrl/api/v1/files/55/create_success?uuid=u").build())
        canvas.enqueue(MockResponse.Builder().code(200).body("""{"id":55,"display_name":"essay.pdf","size":5}""").build())
        canvas.enqueue(MockResponse.Builder().code(200).body("""{"id":900,"workflow_state":"submitted","submitted_at":"2026-11-01T10:00:00Z","attempt":1}""").build())

        val repo = Repository(CanvasApi(canvasUrl, "secret-token"))
        val sub = repo.submitFiles(1, 2, listOf(UploadSource("essay.pdf", 5, "application/pdf") { "hello".byteInputStream() }), "see attached")
        assertEquals(900L, sub.id)

        val ticket = canvas.takeRequest()
        assertEquals("/api/v1/courses/1/assignments/2/submissions/self/files", ticket.url.encodedPath)
        assertEquals("Bearer secret-token", ticket.headers["Authorization"])
        val up = storage.takeRequest()
        assertNull("token must not go to file storage", up.headers["Authorization"])
        val upBody = up.body!!.utf8()
        assertTrue(upBody.contains("name=\"key\"") && upBody.contains("abc") && upBody.contains("hello") && upBody.contains("filename=\"essay.pdf\""))
        val confirm = canvas.takeRequest()
        assertEquals("/api/v1/files/55/create_success", confirm.url.encodedPath)
        assertEquals("Bearer secret-token", confirm.headers["Authorization"])
        val submit = canvas.takeRequest()
        val form = submit.body!!.utf8()
        assertTrue(form, form.contains("submission%5Bsubmission_type%5D=online_upload") && form.contains("submission%5Bfile_ids%5D%5B%5D=55") && form.contains("comment%5Btext_comment%5D=see+attached"))
        canvas.close(); storage.close()
    }

    @Test
    fun paginationFollowsLinksAndAuthErrorsSurface() = runBlocking {
        val s = MockWebServer(); s.start()
        val u = s.url("/").toString().trimEnd('/')
        s.enqueue(MockResponse.Builder().code(200).addHeader("Link", "<$u/api/v1/courses?page=2&per_page=100>; rel=\"next\"").body("""[{"id":1,"name":"A"}]""").build())
        s.enqueue(MockResponse.Builder().code(200).body("""[{"id":2,"name":"B","unknown_field":{"x":1},"term":null}]""").build())
        s.enqueue(MockResponse.Builder().code(401).body("""{"errors":[{"message":"Invalid access token."}]}""").build())
        val api = CanvasApi(u, "t")
        val all = api.getAll("/api/v1/courses", com.rafat.canvos.data.api.Course.serializer())
        assertEquals(listOf(1L, 2L), all.map { it.id })
        try {
            api.get("/api/v1/users/self", com.rafat.canvos.data.api.User.serializer()); org.junit.Assert.fail()
        } catch (e: UnauthorizedException) { assertEquals("Invalid access token.", e.message) }
        s.close()
    }
}
