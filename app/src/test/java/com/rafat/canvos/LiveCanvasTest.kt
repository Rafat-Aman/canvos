package com.rafat.canvos

import com.rafat.canvos.data.Repository
import com.rafat.canvos.data.api.CanvasApi
import com.rafat.canvos.data.api.CanvasException
import com.rafat.canvos.data.api.ReadOnlyException
import com.rafat.canvos.ui.nav.CanvasLinks
import com.rafat.canvos.ui.nav.LinkAction
import com.rafat.canvos.ui.nav.WebRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Exercises every read endpoint the app uses against a real account, in
 * read-only mode (writes are refused by the client). Skipped unless
 * CANVAS_URL and CANVAS_TOKEN are set.
 */
class LiveCanvasTest {
    private val url: String? = System.getenv("CANVAS_URL")
    private val token: String? = System.getenv("CANVAS_TOKEN")
    private val log = StringBuilder()
    private var calls = 0
    private val failures = ArrayList<String>()

    private suspend fun <T> step(name: String, allow404: Boolean = false, block: suspend () -> T): T? = try {
        calls++
        block().also { r -> log.append("  ok  $name").append(if (r is Collection<*>) " (${r.size})" else "").append('\n') }
    } catch (e: CanvasException) {
        if ((allow404 && e.status == 404) || e.status == 403 || e.status == 401 && name.contains("optional")) {
            log.append("  --  $name: ${e.status} ${e.message}\n"); null
        } else { failures += "$name: ${e.message}"; log.append("  FAIL $name: ${e.message}\n"); null }
    } catch (e: Exception) {
        failures += "$name: $e"; log.append("  FAIL $name: $e\n"); null
    }

    @Test
    fun everyReadEndpointParses() = runBlocking {
        assumeTrue("CANVAS_URL/CANVAS_TOKEN not set", url != null && token != null)
        val repo = Repository(CanvasApi(url!!, token!!, readOnly = true))

        val me = step("profile") { repo.profile() }!!
        assertTrue(me.name.isNotBlank())
        step("calendar feed present") { require(!me.calendar?.ics.isNullOrBlank()) { "no ics" } }
        val dash = step("dashboard courses") { repo.dashboardCourses() }.orEmpty()
        step("all courses") { repo.allCourses() }
        val today = LocalDate.now()
        val planner = step("planner -60/+120 days") { repo.planner(today.minusDays(60), today.plusDays(120)) }.orEmpty()
        val stream = step("activity stream") { repo.activityStream() }.orEmpty()
        for (scope in listOf("inbox", "unread", "starred", "sent", "archived")) step("conversations $scope") { repo.conversations(scope, null) }
        val convs = repo.conversations("inbox", null)
        convs.take(3).forEach { c -> step("conversation ${c.id}") { repo.conversation(c.id) } }
        step("planner snapshot -45/+180 days") { repo.savePlannerSnapshot(today.minusDays(45), today.plusDays(180)) }
        convs.take(3).forEach { c -> step("conversation ${c.id} (not marked read)") { repo.conversation(c.id, markRead = false) } }
        planner.filter { it.plannableType == "calendar_event" }.take(3).forEach { p -> step("calendar event ${p.plannableId}") { repo.calendarEvent(p.plannableId.toString()) } }

        for (ci in dash) {
            val id = ci.id
            step("[$id] course") { repo.course(id, true) }
            step("[$id] tabs") { repo.tabs(id) }
            val groups = step("[$id] assignment groups") { repo.assignmentGroups(id) }.orEmpty()
            groups.flatMap { it.assignments }.take(8).forEach { a ->
                step("[$id] assignment ${a.id}") { repo.assignment(id, a.id) }
                step("[$id] submission ${a.id}") { repo.submission(id, a.id) }
            }
            val ann = step("[$id] announcements") { repo.announcements(id) }.orEmpty()
            ann.take(3).forEach { t ->
                step("[$id] announcement ${t.id}") { repo.topic(id, t.id) }
                step("[$id] announcement view ${t.id}") { repo.topicView(id, t.id) }
            }
            val disc = step("[$id] discussions") { repo.discussions(id) }.orEmpty()
            disc.take(3).forEach { t ->
                step("[$id] discussion ${t.id}") { repo.topic(id, t.id) }
                step("[$id] discussion view ${t.id}", allow404 = true) { repo.topicView(id, t.id) }
            }
            val pages = step("[$id] pages") { repo.pages(id) }.orEmpty()
            pages.take(3).forEach { p -> step("[$id] page ${p.url}") { repo.page(id, p.url) } }
            step("[$id] front page", allow404 = true) { repo.frontPage(id) }
            val mods = step("[$id] modules") { repo.modules(id) }.orEmpty()
            mods.flatMap { it.items.orEmpty() }.filter { it.type == "File" && it.contentId != null }.take(2).forEach { item ->
                step("[$id] module file ${item.contentId}") { repo.file(item.contentId!!, id) }
            }
            mods.flatMap { it.items.orEmpty() }.filter { it.type == "Page" && it.pageUrl != null }.take(2).forEach { item ->
                step("[$id] module page ${item.pageUrl}") { repo.page(id, item.pageUrl!!) }
            }
            step("[$id] quizzes", allow404 = true) { repo.quizzes(id) }
            step("[$id] people") { repo.people(id) }
            val root = step("[$id] files root", allow404 = true) { repo.rootFolder(id) }
            if (root != null) {
                val (folders, files) = step("[$id] folder contents") { repo.folderContents(root.id) } ?: (emptyList<com.rafat.canvos.data.api.Folder>() to emptyList())
                folders.take(2).forEach { f -> step("[$id] subfolder ${f.name}") { repo.folderContents(f.id) } }
                files.take(1).forEach { f -> step("[$id] file ${f.id}") { repo.file(f.id, id) } }
            }
            // Flat lists the offline sync uses instead of walking every folder.
            val allFolders = step("[$id] all folders") { repo.courseFolders(id) }
            val allFiles = step("[$id] all files") { repo.courseFiles(id) }
            if (allFolders != null && allFiles != null && root != null) {
                step("[$id] flat lists match folder view") {
                    require(allFolders.any { it.id == root.id && it.parentFolderId == null }) { "root folder missing from flat list" }
                    val direct = repo.folderContents(root.id).second.map { it.id }.toSet()
                    val flat = allFiles.filter { it.folderId == root.id && !it.hidden }.map { it.id }.toSet()
                    require(direct == flat) { "root files differ: folder=$direct flat=$flat" }
                }
            }
            step("[$id] course activity") { repo.activityStream(id) }
            step("[$id] recipients search") { repo.searchRecipients(id, "a") }
        }
        step("my files root") { repo.rootFolder(null) }

        // Every link we may be handed should resolve without throwing; Canvas links stay in-app.
        val links = planner.mapNotNull { it.htmlUrl } + stream.mapNotNull { it.htmlUrl }
        var native = 0
        links.forEach { l ->
            val a = CanvasLinks.resolve(l, repo.api.base)
            if (a is LinkAction.External) failures += "link went external: $l"
            if (a is LinkAction.Navigate && a.route !is WebRoute) native++
        }
        log.append("  links: ${links.size} resolved, $native to native screens\n")

        // Safety: the client must refuse writes in read-only mode.
        try {
            repo.setFavorite(dash.firstOrNull()?.id ?: 1, true)
            fail("write was not blocked")
        } catch (_: ReadOnlyException) { log.append("  ok  writes blocked in read-only mode\n") }

        println("Live Canvas test: $calls calls, ${failures.size} failures\n$log")
        assertTrue("Failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
