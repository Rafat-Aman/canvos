package com.rafat.canvos.ui.nav

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.rafat.canvos.CanvosApp
import com.rafat.canvos.data.api.CanvasException
import com.rafat.canvos.data.api.CanvasFile
import com.rafat.canvos.data.api.NetworkException
import com.rafat.canvos.data.api.NetworkState
import com.rafat.canvos.data.api.NotSavedException
import com.rafat.canvos.data.offline.LocalFile
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

class Navigator(private val nav: NavController, private val context: Context) {
    fun go(route: Any) {
        nav.navigate(route) { launchSingleTop = true }
    }

    fun back() {
        if (!nav.popBackStack()) (context as? Activity)?.finish()
    }

    /** Handle a link from Canvas content (HTML, notifications, planner...). */
    fun openLink(url: String?) {
        if (url.isNullOrBlank()) return
        if (url.startsWith("mailto:") || url.startsWith("tel:")) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            return
        }
        when (val a = CanvasLinks.resolve(url, repo().api.base)) {
            is LinkAction.Navigate -> go(a.route)
            is LinkAction.OpenFile -> FileActions.openById(context, a.fileId, a.courseId)
            is LinkAction.External -> openBrowser(a.url)
        }
    }

    fun openBrowser(url: String) = openInBrowser(context, url)
}

fun openInBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }
}

val LocalNav = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

/** Downloading and opening Canvas files, with a global progress dialog. */
object FileActions {
    data class Progress(val name: String, val fraction: Float?, val error: String? = null)

    private val _state = MutableStateFlow<Progress?>(null)
    val state: StateFlow<Progress?> = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    fun dismiss() {
        job?.cancel()
        _state.value = null
    }

    private val offline get() = CanvosApp.instance.offline.files

    fun openById(context: Context, fileId: Long, courseId: Long?) {
        run(context, "Opening file") {
            val saved = offline.entry(fileId)
            val f = if (saved != null && !NetworkState.hasNetwork.value) saved.file else {
                try {
                    repo().file(fileId, courseId)
                } catch (e: NetworkException) {
                    saved?.file ?: throw e
                }
            }
            openNow(context, f)
        }
    }

    /** Open a downloaded file straight from offline storage. */
    fun openSaved(context: Context, saved: LocalFile) {
        run(context, saved.file.name) {
            val local = offline.fileOf(saved).takeIf { it.isFile } ?: throw CanvasException("This file is no longer on the phone.")
            view(context, saved.file, local)
        }
    }

    fun open(context: Context, file: CanvasFile) {
        run(context, file.name) { openNow(context, file) }
    }

    fun share(context: Context, file: CanvasFile) {
        run(context, file.name) {
            val local = localOrDownload(context, file)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", local)
            val send = Intent(Intent.ACTION_SEND).setType(mimeOf(file)).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Share ${file.name}"))
        }
    }

    /** Hand the file to the system download manager (lands in Downloads, shows a notification). */
    fun saveToDownloads(context: Context, file: CanvasFile) {
        // Already on the phone: copy it, no network needed.
        val saved = offline.current(file) ?: offline.local(file.id)?.takeIf { !NetworkState.hasNetwork.value }
        if (saved != null && Build.VERSION.SDK_INT >= 29) {
            run(context, file.name) {
                copyToDownloads(context, saved, safeName(file.name), mimeOf(file))
                Toast.makeText(context, "Saved ${file.name} to Downloads", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val url = file.url
        if (url.isNullOrBlank() || file.lockedForUser) {
            Toast.makeText(context, file.lockExplanation.orLocked(), Toast.LENGTH_LONG).show()
            return
        }
        try {
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(file.name)
                .setMimeType(mimeOf(file))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName(file.name))
            // File URLs normally carry a "verifier" and need no token. DownloadManager keeps
            // headers across redirects, so only add the token when there is no verifier.
            val parsed = Uri.parse(url)
            if (parsed.host.equals(repo().api.host, ignoreCase = true) && parsed.getQueryParameter("verifier") == null) {
                req.addRequestHeader("Authorization", "Bearer " + tokenFor(context))
            }
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(context, "Downloading ${file.name}…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't start download: ${e.userMessage()}", Toast.LENGTH_LONG).show()
        }
    }

    private fun tokenFor(context: Context): String =
        com.rafat.canvos.data.SessionStore(context).load()?.token.orEmpty()

    private fun run(context: Context, name: String, block: suspend () -> Unit) {
        job?.cancel()
        _state.value = Progress(name, null)
        job = scope.launch {
            try {
                block()
                _state.value = null
            } catch (e: CancellationException) {
                _state.value = null
                throw e
            } catch (e: Exception) {
                _state.value = Progress(name, null, e.userMessage())
            }
        }
    }

    private suspend fun openNow(context: Context, file: CanvasFile) {
        if (file.lockedForUser && offline.local(file.id) == null) throw CanvasException(file.lockExplanation.orLocked())
        _state.value = Progress(file.name, null)
        view(context, file, localOrDownload(context, file))
    }

    /** The offline copy when it's current (or when offline), else a fresh download. */
    private suspend fun localOrDownload(context: Context, file: CanvasFile): File {
        offline.current(file)?.let { return it }
        val saved = offline.local(file.id)
        if (saved != null && !NetworkState.hasNetwork.value) return saved
        return try {
            download(context, file)
        } catch (e: NetworkException) {
            saved ?: throw if (e is NotSavedException || !NetworkState.hasNetwork.value) {
                CanvasException("You're offline, and this file hasn't been downloaded for offline use yet.")
            } else e
        }
    }

    private suspend fun copyToDownloads(context: Context, src: File, name: String, mime: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw CanvasException("Couldn't save to Downloads.")
        try {
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                ?: throw CanvasException("Couldn't save to Downloads.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun view(context: Context, file: CanvasFile, local: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", local)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(view)
        } catch (_: ActivityNotFoundException) {
            val send = Intent(Intent.ACTION_SEND).setType(mimeOf(file)).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "No app can open this. Share ${file.name} to…"))
        }
    }

    /** Download into the cache (reused if already there and unchanged). */
    private suspend fun download(context: Context, file: CanvasFile): File = withContext(Dispatchers.IO) {
        val url = file.url ?: throw CanvasException("This file can't be downloaded.")
        val dir = File(context.cacheDir, "downloads/${file.id}").apply { mkdirs() }
        val out = File(dir, safeName(file.name))
        val stamp = File(dir, ".stamp")
        val version = "${file.size}|${file.updatedAt}"
        if (out.exists() && stamp.exists() && stamp.readText() == version && (file.size == null || out.length() == file.size)) {
            return@withContext out
        }
        val api = repo().api
        val resp = api.execute(Request.Builder().url(url).get().build())
        resp.use { r ->
            val body = r.body
            val total = body.contentLength().takeIf { it > 0 } ?: file.size ?: -1L
            val tmp = File(dir, out.name + ".part")
            body.byteStream().use { input ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                _state.value = Progress(file.name, done.toFloat() / total)
                            }
                        }
                    }
                }
            }
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true)
                tmp.delete()
            }
        }
        stamp.writeText(version)
        out
    }

    fun mimeOf(file: CanvasFile): String {
        val ct = file.contentType
        if (!ct.isNullOrBlank() && ct != "application/octet-stream" && ct != "unknown/unknown") return ct
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().ifBlank { "file" }.take(150)

    private fun String?.orLocked() = this?.takeIf { it.isNotBlank() } ?: "This file is locked."
}
