package com.rafat.canvos.data.offline

import com.rafat.canvos.data.api.CanvasApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException
import java.io.InputStream

/** Images and other sub-resources of Canvas HTML, saved so pages still look right offline. */
object Resources {
    private const val MAX_SAVED = 20L * 1024 * 1024

    sealed interface Fetched
    class Buffered(val res: ResourceCache.Res) : Fetched

    /** Too big (or partial, e.g. video) to keep in memory; passed through as a stream. */
    class Streamed(val mime: String, val charset: String?, val code: Int, val headers: Map<String, String>, val stream: InputStream) : Fetched

    private val imageExt = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "avif", "heic")

    fun looksLikeImage(url: HttpUrl, accept: String?): Boolean =
        accept?.startsWith("image/") == true ||
            url.pathSegments.lastOrNull()?.substringAfterLast('.', "")?.lowercase() in imageExt ||
            url.encodedPath.endsWith("/preview")

    /**
     * Blocking fetch (the WebView calls this on its own IO thread). The token is only
     * added for the Canvas host by the api client's interceptor. Falls back to the saved copy.
     */
    fun fetch(api: CanvasApi, cache: ResourceCache, url: HttpUrl, headers: Map<String, String>, online: Boolean): Fetched? {
        val key = url.toString()
        if (!online) return cache.get(key)?.let(::Buffered)
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> if (!k.equals("Authorization", true)) header(k, v) }
        }.build()
        val resp = try {
            api.http.newCall(req).execute()
        } catch (_: IOException) {
            return cache.get(key)?.let(::Buffered)
        }
        val type = resp.header("Content-Type") ?: "application/octet-stream"
        val mime = type.substringBefore(';').trim().ifBlank { "application/octet-stream" }
        val charset = Regex("charset=([^;]+)").find(type)?.groupValues?.get(1)?.trim()
        val length = resp.body.contentLength()
        if (resp.code == 206 || mime.startsWith("video/") || mime.startsWith("audio/") || length > MAX_SAVED) {
            val pass = listOf("Content-Range", "Content-Length", "Accept-Ranges")
                .mapNotNull { h -> resp.header(h)?.let { h to it } }.toMap()
            return Streamed(mime, charset, resp.code, pass, resp.body.byteStream())
        }
        val bytes = try {
            resp.use { it.body.bytes() }
        } catch (_: IOException) {
            return cache.get(key)?.let(::Buffered)
        }
        if (resp.isSuccessful) {
            val res = ResourceCache.Res(mime, charset, bytes, resp.code)
            // HTML here is usually a login or error page, not content worth keeping.
            if (mime != "text/html") runCatching { cache.put(key, res) }
            return Buffered(res)
        }
        return cache.get(key)?.let(::Buffered) ?: Buffered(ResourceCache.Res(mime, charset, bytes, resp.code))
    }

    /** Save [url] if it isn't saved yet. */
    suspend fun prefetch(api: CanvasApi, cache: ResourceCache, url: HttpUrl) = withContext(Dispatchers.IO) {
        if (cache.has(url.toString())) return@withContext
        val r = fetch(api, cache, url, mapOf("Accept" to "image/*"), online = true)
        if (r is Streamed) runCatching { r.stream.close() }
    }

    private val imgSrc = Regex("""<img\b[^>]*?\ssrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val fileHref = Regex("""href\s*=\s*["']([^"']*?/files/(\d+)[^"']*)["']""", RegexOption.IGNORE_CASE)

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")

    /** Images embedded in Canvas HTML. */
    fun imageUrls(html: String, base: HttpUrl): List<HttpUrl> =
        imgSrc.findAll(html).mapNotNull { base.resolve(unescape(it.groupValues[1]).trim()) }
            .filter { it.scheme == "http" || it.scheme == "https" }.distinct().toList()

    /** Canvas files linked from HTML (`/courses/1/files/2`, `/files/2/download`...). */
    fun linkedFileIds(html: String, base: HttpUrl): Set<Long> =
        fileHref.findAll(html).mapNotNull { m ->
            val u = base.resolve(unescape(m.groupValues[1]).trim()) ?: return@mapNotNull null
            if (!u.host.equals(base.host, ignoreCase = true)) return@mapNotNull null
            m.groupValues[2].toLongOrNull()
        }.toSet()
}
