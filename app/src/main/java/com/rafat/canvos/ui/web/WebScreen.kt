package com.rafat.canvos.ui.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.rafat.canvos.ui.common.CanvosTopBar
import com.rafat.canvos.ui.common.repo
import com.rafat.canvos.ui.common.userMessage
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.nav.openInBrowser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Full-screen web view logged into Canvas (via a one-time session URL), for
 * quizzes, external tools and anything without a native screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, title: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var pageTitle by remember { mutableStateOf(title) }
    var progress by remember { mutableIntStateOf(0) }
    var startUrl by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
        fileCallback = null
    }

    LaunchedEffect(url) {
        val api = repo().api
        val target = url.toHttpUrlOrNull()
        startUrl = if (target != null && api.isCanvasHost(target)) {
            // Fall back to the plain URL if this fails; the page may then ask to log in.
            runCatching { repo().sessionUrl(url) }.getOrNull()?.ifBlank { null } ?: url
        } else url
    }
    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Scaffold(topBar = {
        CanvosTopBar(pageTitle.ifBlank { "Canvas" }, onBack = { nav.back() }, actions = {
            IconButton(onClick = { webView?.reload() }) { Icon(Icons.Outlined.Refresh, "Reload") }
            IconButton(onClick = { openInBrowser(ctx, webView?.url?.takeIf { !it.contains("session_token") } ?: url) }) {
                Icon(Icons.Outlined.OpenInBrowser, "Open in browser")
            }
        })
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val start = startUrl
            when {
                start == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> Column(Modifier.fillMaxSize()) {
                    if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c ->
                            WebView(c).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.setSupportMultipleWindows(false)
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val u = request.url
                                        return when (u.scheme) {
                                            "http", "https" -> false
                                            else -> { runCatching { c.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, u)) }; true }
                                        }
                                    }

                                    override fun onPageFinished(view: WebView, u: String?) {
                                        canGoBack = view.canGoBack()
                                        view.title?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { pageTitle = it }
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, p: Int) { progress = p }

                                    override fun onShowFileChooser(
                                        view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams,
                                    ): Boolean {
                                        fileCallback?.onReceiveValue(null)
                                        fileCallback = callback
                                        return try {
                                            chooser.launch(params.createIntent())
                                            true
                                        } catch (e: Exception) {
                                            fileCallback = null
                                            Toast.makeText(c, "Can't open file picker", Toast.LENGTH_SHORT).show()
                                            false
                                        }
                                    }
                                }
                                setDownloadListener { dlUrl, userAgent, contentDisposition, mime, _ ->
                                    download(c, dlUrl, userAgent, contentDisposition, mime)
                                }
                                loadUrl(start)
                                webView = this
                            }
                        },
                        onRelease = { it.destroy(); webView = null },
                    )
                }
            }
        }
    }
}

private fun download(ctx: Context, url: String, userAgent: String?, disposition: String?, mime: String?) {
    try {
        val name = URLUtil.guessFileName(url, disposition, mime)
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setMimeType(mime)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
        userAgent?.let { req.addRequestHeader("User-Agent", it) }
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(ctx, "Downloading $name…", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Download failed: ${e.userMessage()}", Toast.LENGTH_LONG).show()
    }
}
