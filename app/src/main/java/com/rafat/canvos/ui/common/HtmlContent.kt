package com.rafat.canvos.ui.common

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rafat.canvos.CanvosApp
import com.rafat.canvos.data.api.CanvasApi
import com.rafat.canvos.data.api.NetworkState
import com.rafat.canvos.data.offline.Resources
import com.rafat.canvos.ui.nav.LocalNav
import com.rafat.canvos.ui.theme.isDark
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream

private fun Color.css() = String.format("#%06X", 0xFFFFFF and toArgb())

/**
 * Renders Canvas HTML (assignment descriptions, pages, announcements...). The
 * WebView sizes itself to its content so it can sit inside a scrolling screen.
 * Links go through the app's router; Canvas-hosted images load with the token.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlContent(html: String?, modifier: Modifier = Modifier, horizontalPadding: Int = 16) {
    val nav = LocalNav.current
    val appSettings by CanvosApp.instance.settings.state.collectAsState()
    val dark = isDark(appSettings)
    val cs = MaterialTheme.colorScheme
    val doc = remember(html, dark, cs.onSurface, cs.primary) {
        wrapHtml(html.orEmpty(), cs.onSurface.css(), cs.primary.css(), cs.outlineVariant.css(), cs.surfaceVariant.css(), dark, horizontalPadding)
    }
    var heightDp by remember { mutableIntStateOf(0) }
    val onLink by rememberUpdatedState<(String) -> Unit> { nav.openLink(it) }
    val api = remember { CanvosApp.instance.repo.value?.api }
    val base = api?.base?.toString() ?: "https://localhost/"

    AndroidView(
        modifier = modifier.fillMaxWidth().let { if (heightDp > 0) it.height(heightDp.dp) else it.heightIn(min = 24.dp) },
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(AColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = false
                settings.setSupportZoom(false)
                // Offline, let the WebView use whatever it has cached for resources we don't handle.
                if (!NetworkState.hasNetwork.value) settings.cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
                val main = Handler(Looper.getMainLooper())
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun height(h: Float) {
                        main.post { if (h > 0) heightDp = h.toInt() + 2 }
                    }
                }, "CanvosBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        onLink(request.url.toString())
                        return true
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        if (api != null && !request.isForMainFrame) authorizedFetch(api, request) else null
                }
            }
        },
        update = { wv ->
            if (wv.tag != doc) {
                wv.tag = doc
                wv.loadDataWithBaseURL(base, doc, "text/html", "UTF-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

/**
 * Load sub-resources: Canvas-hosted ones with the token, and images from anywhere,
 * through the offline resource cache so they also show without a connection.
 */
internal fun authorizedFetch(api: CanvasApi, request: WebResourceRequest): WebResourceResponse? {
    if (request.method != "GET") return null
    val url = request.url.toString().toHttpUrlOrNull() ?: return null
    val headers = request.requestHeaders.orEmpty()
    if (!api.isCanvasHost(url) && !Resources.looksLikeImage(url, headers["Accept"])) return null
    val cache = CanvosApp.instance.offline.resources
    return try {
        when (val r = Resources.fetch(api, cache, url, headers, NetworkState.hasNetwork.value)) {
            null -> null
            is Resources.Buffered -> response(r.res.mime, r.res.charset, r.res.code, emptyMap(), ByteArrayInputStream(r.res.bytes))
            is Resources.Streamed -> response(r.mime, r.charset, r.code, r.headers, r.stream)
        }
    } catch (_: Exception) {
        null
    }
}

private fun response(mime: String, charset: String?, code: Int, headers: Map<String, String>, body: java.io.InputStream): WebResourceResponse {
    // WebResourceResponse rejects 3xx codes; redirects were already followed.
    val c = code.coerceIn(100, 599).let { if (it in 300..399) 200 else it }
    return WebResourceResponse(mime, charset, c, if (c in 200..299) "OK" else "Error", headers, body)
}

fun wrapHtml(body: String, fg: String, link: String, border: String, codeBg: String, dark: Boolean, padX: Int): String {
    // In dark mode, author-chosen colours (often black text) would be unreadable.
    val darkFix = if (dark) """
        body * { color: $fg !important; background-color: transparent !important; border-color: $border !important; }
        a, a * { color: $link !important; }
        mark { background-color: #5a4a00 !important; }
    """ else ""
    return """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<style>
html, body { margin:0; padding:0; background: transparent; }
body { padding: 0 ${padX}px; font-family: Roboto, 'Noto Sans', sans-serif; font-size: 16px; line-height: 1.55;
  color: $fg; overflow-wrap: anywhere; word-wrap: break-word; -webkit-text-size-adjust: 100%; }
#canvos-content { display: flow-root; padding-bottom: 2px; }
#canvos-content > :first-child { margin-top: 0; }
#canvos-content > :last-child { margin-bottom: 0; }
img, video, canvas, svg { max-width: 100% !important; height: auto !important; }
iframe, embed, object { max-width: 100% !important; border: 0; }
a { color: $link; }
table { display: block; overflow-x: auto; border-collapse: collapse; max-width: 100%; }
td, th { border: 1px solid $border; padding: 6px 8px; vertical-align: top; }
pre, code { background: $codeBg; border-radius: 6px; }
pre { padding: 10px; white-space: pre-wrap; overflow-x: auto; }
blockquote { margin: 8px 0; padding-left: 12px; border-left: 3px solid $border; }
hr { border: 0; border-top: 1px solid $border; }
p:empty { display: none; }
$darkFix
</style></head><body><div id="canvos-content">$body</div>
<script>
(function(){
  function report(){
    var el = document.getElementById('canvos-content');
    var h = Math.max(el.getBoundingClientRect().height, el.scrollHeight, el.offsetHeight);
    CanvosBridge.height(Math.ceil(h) + 6);
  }
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(report);
  setTimeout(report, 300); setTimeout(report, 1200);
  window.addEventListener('load', report);
  if (window.ResizeObserver) new ResizeObserver(report).observe(document.getElementById('canvos-content'));
  var imgs = document.images;
  for (var i = 0; i < imgs.length; i++) {
    imgs[i].addEventListener('load', report);
    // Images the student can't access (e.g. copied from another course) would show as broken icons.
    imgs[i].addEventListener('error', function(){ this.style.display = 'none'; report(); });
  }
  report();
})();
</script></body></html>"""
}
