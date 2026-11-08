package com.rafat.canvos.data.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.rafat.canvos.CanvosApp
import com.rafat.canvos.data.CourseInfo
import com.rafat.canvos.data.Repository
import com.rafat.canvos.data.api.Assignment
import com.rafat.canvos.data.api.CanvasApi
import com.rafat.canvos.data.api.CanvasException
import com.rafat.canvos.data.api.CanvasFile
import com.rafat.canvos.data.api.DiscussionEntry
import com.rafat.canvos.data.api.DiscussionTopic
import com.rafat.canvos.data.api.Folder
import com.rafat.canvos.data.api.NetworkException
import com.rafat.canvos.data.api.NetworkOnly
import com.rafat.canvos.data.api.UnauthorizedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Keeps the chosen courses available offline: saves every screen's data (through the
 * api client's response cache), downloads course files into app storage, and saves
 * images used in pages. Unchanged items are skipped, so later runs are quick.
 */
object OfflineSync {
    data class Status(val running: Boolean = false, val message: String = "", val fraction: Float? = null)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private val lock = Mutex()

    /** Returns true when everything is saved, false when time ran out (run again to continue). */
    suspend fun run(store: OfflineStore, context: Context, repo: Repository, deadline: Long): Boolean {
        if (!lock.tryLock()) return true
        try {
            _status.value = Status(true, "Starting…")
            return withContext(NetworkOnly + Dispatchers.IO) { SyncRun(store, context, repo, deadline, _status).run() }
        } finally {
            _status.value = Status()
            lock.unlock()
        }
    }

    internal fun ownerOf(domain: String, userId: Long) = "$domain|$userId"
}

private class Paused : Exception()

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

internal fun assignmentVersion(a: Assignment): String {
    val s = a.submission
    return listOf(
        a.updatedAt, a.dueAt, a.lockAt, a.unlockAt, a.lockedForUser,
        s?.workflowState, s?.submittedAt, s?.gradedAt, s?.score, s?.grade, s?.postedAt, s?.attempt,
    ).joinToString("|")
}

internal fun topicVersion(t: DiscussionTopic): String =
    listOf(t.postedAt, t.lastReplyAt, t.replyCount, t.lockedForUser, t.message?.hashCode()).joinToString("|")

/** "course files/Week 1/Slides" -> "Week 1/Slides" (each part safe as a file name). */
internal fun folderPathOf(f: Folder): String =
    (f.fullName ?: f.name).split('/').drop(1).map(::safeSegment).filter { it.isNotEmpty() }.joinToString("/")

internal fun safeSegment(s: String): String =
    s.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trim('.').take(120)

private fun usable(f: CanvasFile) = !f.hidden && !f.lockedForUser && !f.url.isNullOrBlank()

private class SyncRun(
    val store: OfflineStore,
    val context: Context,
    val repo: Repository,
    val deadline: Long,
    val status: MutableStateFlow<OfflineSync.Status>,
) {
    val api: CanvasApi = repo.api
    val settings = store.prefs.state.value
    val now = System.currentTimeMillis()
    private var courseIndex = 0
    private var courseCount = 1
    private val reserved = HashMap<String, Long>()

    suspend fun run(): Boolean {
        try {
            say("Updating your courses…")
            repo.profile()
            val dashboard = repo.dashboardCourses(force = true)
            val all = soft { repo.allCourses() }.orEmpty()
            val dashIds = dashboard.map { it.id }.toSet()
            val selected = dashboard.filter { settings.isSelected(it.id, true) } +
                all.filter { it.id !in dashIds && settings.isSelected(it.id, false) }
            courseCount = selected.size + 1
            global(dashboard)
            selected.forEachIndexed { i, c ->
                courseIndex = i + 1
                course(c)
            }
            // Drop files of courses (or all files) that are no longer kept offline.
            val keep = if (settings.downloadFiles) selected.map { it.id }.toSet() else emptySet()
            store.files.all().filter { it.courseId !in keep }.forEach { store.files.remove(it.file.id) }
            store.responses.trim(45 * DAY)
            store.resources.trim(300L * 1024 * 1024)
            store.prefs.update { it.copy(lastSuccess = System.currentTimeMillis(), lastError = null) }
            return true
        } catch (_: Paused) {
            return false
        } finally {
            runCatching { store.index.save() }
            runCatching { store.files.save() }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun checkTime() {
        if (System.currentTimeMillis() > deadline) throw Paused()
    }

    private fun say(message: String, within: Float = 0f) {
        status.value = OfflineSync.Status(true, message, ((courseIndex + within) / courseCount).coerceIn(0f, 1f))
    }

    /**
     * Run one request; things the student can't see (403/404, locked items) are skipped.
     * No connection or a rejected token stops the whole sync. Throttling is retried.
     */
    private suspend fun <T> soft(block: suspend () -> T): T? {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: UnauthorizedException) {
                throw e
            } catch (e: NetworkException) {
                throw e
            } catch (e: CanvasException) {
                val throttled = e.status == 403 && e.message?.contains("Rate Limit", ignoreCase = true) == true
                if (!throttled || attempt >= 2) return null
                attempt++
                delay(2000L * attempt)
            }
        }
    }

    /** Fetch a detail screen's data unless the saved copy is still this [version]. */
    private suspend fun detail(key: String, version: String, maxAge: Long, fetch: suspend () -> Unit) {
        checkTime()
        if (store.index.isFresh(key, version, maxAge, now)) return
        if (soft { fetch() } != null) store.index.mark(key, version, now)
    }

    private suspend fun <T> Collection<T>.par(n: Int, f: suspend (T) -> Unit) = coroutineScope {
        val sem = Semaphore(n)
        map { item -> launch { sem.withPermit { f(item) } } }.joinAll()
    }

    // ------------------------------------------------------------------ account-wide

    private suspend fun global(dashboard: List<CourseInfo>) {
        soft { repo.activityStream() }
        val today = LocalDate.now()
        val planner = soft { repo.savePlannerSnapshot(today.minusDays(45), today.plusDays(180)) }.orEmpty()
        planner.filter { it.plannableType == "calendar_event" }.par(4) { item ->
            val p = item.plannable
            detail("event:${item.plannableId}", "${p.startAt}|${p.endAt}|${p.title}", 3 * DAY) {
                repo.calendarEvent(item.plannableId.toString())
            }
        }
        var inbox = emptyList<com.rafat.canvos.data.api.Conversation>()
        for (scope in listOf("inbox", "unread", "starred", "sent")) {
            checkTime()
            soft { repo.conversations(scope, null) }?.let { if (scope == "inbox") inbox = it }
        }
        // Saved without marking anything as read.
        inbox.take(40).par(4) { c ->
            detail("conv:${c.id}", "${c.lastMessageAt}|${c.messageCount}", 7 * DAY) { repo.conversation(c.id, markRead = false) }
        }
        // Course card images and the profile picture.
        val loader = SingletonImageLoader.get(context)
        (dashboard.mapNotNull { it.course.imageUrl } + listOfNotNull(repo.self?.avatarUrl)).forEach { url ->
            runCatching { loader.execute(ImageRequest.Builder(context).data(url).build()) }
        }
    }

    // ------------------------------------------------------------------ one course

    private suspend fun course(c: CourseInfo) {
        checkTime()
        val cid = c.id
        say(c.displayName)
        val bodies = Collections.synchronizedList(ArrayList<String>())
        fun body(html: String?) {
            if (!html.isNullOrBlank()) bodies += html
        }
        val attachments = Collections.synchronizedList(ArrayList<CanvasFile>())
        fun entries(list: List<DiscussionEntry>) {
            for (e in list) {
                body(e.message)
                e.attachment?.let { attachments += it }
                attachments += e.attachments
                entries(e.replies)
            }
        }

        // Home, syllabus, tabs and lists that are always refreshed.
        soft { repo.course(cid, force = true) }?.let { body(it.course.syllabusBody) }
        soft { repo.tabs(cid) }
        soft { repo.activityStream(cid) }
        soft { repo.quizzes(cid) }
        if (!store.index.isFresh("people:$cid", "", DAY, now)) {
            soft { repo.people(cid) }?.let { store.index.mark("people:$cid", "", now) }
        }

        // Assignments, descriptions, and feedback on anything handed in or graded.
        say(c.displayName, 0.1f)
        val assignments = soft { repo.assignmentGroups(cid) }.orEmpty().flatMap { it.assignments }
        assignments.par(4) { a ->
            val v = assignmentVersion(a)
            detail("a:$cid:${a.id}", v, 3 * DAY) { body(repo.assignment(cid, a.id).description) }
            val s = a.submission
            if (s != null && (s.isSubmitted || s.isGraded || s.score != null || s.grade != null)) {
                detail("s:$cid:${a.id}", v, DAY) {
                    val sub = repo.submission(cid, a.id)
                    sub.comments.forEach { attachments += it.attachments }
                }
            }
        }

        // Announcements and discussions with their replies.
        say(c.displayName, 0.25f)
        val topics = soft { repo.announcements(cid) }.orEmpty().take(40) + soft { repo.discussions(cid) }.orEmpty().take(80)
        topics.forEach { attachments += it.attachments }
        topics.par(4) { t ->
            detail("t:$cid:${t.id}", topicVersion(t), 3 * DAY) {
                body(repo.topic(cid, t.id).message)
                soft { repo.topicView(cid, t.id) }?.let { v -> entries(v.view); entries(v.newEntries) }
            }
        }

        // Pages and the front page.
        say(c.displayName, 0.4f)
        val pages = soft { repo.pages(cid) }.orEmpty()
        detail("front:$cid", "", 6 * HOUR) { body(repo.frontPage(cid).body) }
        pages.par(4) { p ->
            detail("p:$cid:${p.url}", p.updatedAt.orEmpty(), 7 * DAY) { body(repo.page(cid, p.url).body) }
        }

        // Modules, plus anything they link to that the lists above didn't include.
        say(c.displayName, 0.5f)
        val items = soft { repo.modules(cid) }.orEmpty().flatMap { it.items.orEmpty() }
        val pageUrls = pages.map { it.url }.toSet()
        val assignmentIds = assignments.map { it.id }.toSet()
        val topicIds = topics.map { it.id }.toSet()
        val moduleFiles = Collections.synchronizedSet(HashSet<Long>())
        items.par(4) { item ->
            when (item.type) {
                "Page" -> item.pageUrl?.takeIf { it !in pageUrls }?.let { u ->
                    detail("p:$cid:$u", "", 3 * DAY) { body(repo.page(cid, u).body) }
                }
                "Assignment" -> item.contentId?.takeIf { it !in assignmentIds }?.let { id ->
                    detail("a:$cid:$id", "", 3 * DAY) { body(repo.assignment(cid, id).description) }
                }
                "Discussion" -> item.contentId?.takeIf { it !in topicIds }?.let { id ->
                    detail("t:$cid:$id", "", 3 * DAY) {
                        body(repo.topic(cid, id).message)
                        soft { repo.topicView(cid, id) }?.let { v -> entries(v.view) }
                    }
                }
                "File" -> item.contentId?.let { moduleFiles += it }
            }
        }

        // Files linked from the HTML we just saved; remembered so later runs keep them.
        bodies.toList().flatMap { Resources.linkedFileIds(it, api.base) }.forEach { store.index.mark("link:$cid:$it", "", now) }
        val linked = store.index.keys("link:$cid:").mapNotNull { it.substringAfterLast(':').toLongOrNull() }

        if (settings.downloadFiles) files(c, moduleFiles + linked, attachments.toList())

        say(c.displayName, 0.95f)
        bodies.toList().flatMap { Resources.imageUrls(it, api.base) }.distinct().take(300).par(4) { u ->
            checkTime()
            runCatching { Resources.prefetch(api, store.resources, u) }
        }
    }

    // ------------------------------------------------------------------ files

    private suspend fun files(c: CourseInfo, extraIds: Set<Long>, attachments: List<CanvasFile>) {
        val cid = c.id
        val folders = soft { repo.courseFolders(cid) }
        val list = if (folders != null) soft { repo.courseFiles(cid) } else null
        if (folders != null && list != null) repo.saveFolderTree(cid, folders, list)
        val folderPath = folders.orEmpty().associate { it.id to folderPathOf(it) }
        val byId = folders.orEmpty().associateBy { it.id }
        // Only what the student can browse to: not inside hidden or locked folders.
        fun browsable(folderId: Long?): Boolean {
            var f = folderId?.let(byId::get)
            var depth = 0
            while (f != null && depth++ < 64) {
                if (f.hiddenForUser || f.lockedForUser) return false
                f = f.parentFolderId?.let(byId::get)
            }
            return true
        }

        val wanted = LinkedHashMap<Long, Pair<CanvasFile, String>>()
        list.orEmpty().filter { usable(it) && browsable(it.folderId) }.forEach { wanted[it.id] = it to folderPath[it.folderId].orEmpty() }
        attachments.filter { usable(it) && it.id !in wanted }.forEach { wanted[it.id] = it to "Attachments" }
        (extraIds - wanted.keys).toList().take(300).par(4) { id ->
            checkTime()
            val saved = store.files.entry(id)
            val f = if (saved != null && store.index.isFresh("fmeta:$id", "", 3 * DAY, now)) saved.file
            else soft { repo.file(id, cid) }?.also { store.index.mark("fmeta:$id", "", now) }
            if (f != null && usable(f)) synchronized(wanted) { wanted.putIfAbsent(id, f to (folderPath[f.folderId] ?: "Linked files")) }
        }

        val limit = settings.maxFileMb.toLong() * 1024 * 1024
        val keep = wanted.filterValues { (f, _) -> limit <= 0 || (f.size ?: 0L) <= limit }
        val courseDir = safeSegment(c.displayName).ifEmpty { "Course $cid" }
        var done = 0
        keep.values.toList().par(2) { (f, folder) ->
            checkTime()
            val n = synchronized(this) { ++done }
            say("${c.displayName} · ${f.name}", 0.6f + 0.35f * n / keep.size)
            val parts = listOf(courseDir) + folder.split('/').filter { it.isNotEmpty() } + safeSegment(f.name).ifEmpty { "file-${f.id}" }
            soft { ensureFile(f, cid, c.displayName, parts.joinToString("/")) }
        }
        // Copies of files deleted on Canvas (or now over the size limit) go too, but only
        // when the full file list came through.
        if (list != null) {
            store.files.all().filter { it.courseId == cid && it.file.id !in keep.keys }.forEach { store.files.remove(it.file.id) }
        }
    }

    /** A free path for file [id]: "name (id).ext" if another file already has the name. */
    private fun claimPath(wanted: String, id: Long): String = synchronized(reserved) {
        fun free(p: String) = (reserved[p] == null || reserved[p] == id) && !store.files.pathTaken(p, id)
        val path = if (free(wanted)) wanted else {
            val dot = wanted.lastIndexOf('.').takeIf { it > wanted.lastIndexOf('/') } ?: wanted.length
            wanted.substring(0, dot) + " ($id)" + wanted.substring(dot)
        }
        reserved[path] = id
        path
    }

    private suspend fun ensureFile(f: CanvasFile, courseId: Long, courseName: String, wantedPath: String) {
        val files = store.files
        val version = OfflineFiles.versionOf(f)
        val existing = files.entry(f.id)
        val path = claimPath(wantedPath, f.id)
        val updated = LocalFile(f, courseId, courseName, path, version)
        if (existing != null && existing.version == version && files.fileOf(existing).isFile) {
            when {
                existing.path != path -> if (!files.move(existing, path, updated)) files.put(updated.copy(path = existing.path))
                existing != updated -> files.put(updated)
            }
            return
        }
        download(f, File(files.root, path))
        if (existing != null && existing.path != path) files.fileOf(existing).delete()
        files.put(updated)
    }

    private suspend fun download(f: CanvasFile, dest: File) {
        val url = f.url ?: return
        val resp = api.execute(Request.Builder().url(url).get().build())
        withContext(Dispatchers.IO) {
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".part")
            try {
                resp.use { r ->
                    r.body.byteStream().use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                tmp.delete()
                throw CanvasApi.networkError(e, resp.request.url.host)
            } catch (e: CancellationException) {
                tmp.delete()
                throw e
            }
            if (!tmp.renameTo(dest)) {
                dest.delete()
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
            }
        }
    }
}

/** Runs the offline sync in the background, on Wi-Fi unless the user allows mobile data. */
class OfflineSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CanvosApp
        val repo = app.repo.value ?: return Result.success()
        val store = app.offline
        if (!store.prefs.state.value.enabled) return Result.success()
        // Background jobs get about ten minutes; stop a little earlier and continue in a new job.
        val deadline = System.currentTimeMillis() + 8 * 60_000L
        return try {
            val anyNetwork = inputData.getBoolean(ANY_NETWORK, false)
            if (!OfflineSync.run(store, app, repo, deadline)) continueLater(app, anyNetwork)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnauthorizedException) {
            store.prefs.update { it.copy(lastError = "Canvas rejected your access token. Sign in again.") }
            Result.success()
        } catch (e: NetworkException) {
            store.prefs.update { it.copy(lastError = e.message) }
            Result.retry()
        } catch (e: Exception) {
            store.prefs.update { it.copy(lastError = e.message ?: e.javaClass.simpleName) }
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "offline-sync"
        private const val NOW = "offline-sync-now"
        private const val CONTINUE = "offline-sync-continue"
        private const val ANY_NETWORK = "any_network"

        private fun constraints(ctx: Context, anyNetwork: Boolean): Constraints {
            val wifiOnly = (ctx.applicationContext as CanvosApp).offline.prefs.state.value.wifiOnly
            return Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly && !anyNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(!anyNetwork)
                .setRequiresStorageNotLow(true)
                .build()
        }

        /** Keep the periodic update in line with the settings. */
        fun schedule(ctx: Context) {
            val app = ctx.applicationContext as CanvosApp
            val wm = WorkManager.getInstance(ctx)
            if (app.repo.value == null || !app.offline.prefs.state.value.enabled) {
                cancel(ctx)
                return
            }
            val req = PeriodicWorkRequestBuilder<OfflineSyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(constraints(ctx, anyNetwork = false))
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        /**
         * Update soon: right away when [userAsked] (any network), otherwise as soon as Wi-Fi
         * is available, e.g. when the app is opened and the last update is old.
         */
        fun syncNow(ctx: Context, userAsked: Boolean) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                NOW, if (userAsked) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                    .setConstraints(constraints(ctx, userAsked))
                    .setInputData(workDataOf(ANY_NETWORK to userAsked))
                    .build(),
            )
        }

        /** Called on app start: catch up if the last update is more than an hour old. */
        fun onAppStart(ctx: Context) {
            val app = ctx.applicationContext as CanvosApp
            val s = app.offline.prefs.state.value
            schedule(ctx)
            if (app.repo.value != null && s.enabled && System.currentTimeMillis() - s.lastSuccess > HOUR) syncNow(ctx, userAsked = false)
        }

        private fun continueLater(ctx: Context, anyNetwork: Boolean) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                CONTINUE, ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                    .setConstraints(constraints(ctx, anyNetwork))
                    .setInputData(workDataOf(ANY_NETWORK to anyNetwork))
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .build(),
            )
        }

        fun cancel(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            listOf(PERIODIC, NOW, CONTINUE).forEach { wm.cancelUniqueWork(it) }
        }
    }
}
