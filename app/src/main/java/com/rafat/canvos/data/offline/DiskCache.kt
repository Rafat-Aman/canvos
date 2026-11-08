package com.rafat.canvos.data.offline

import okhttp3.HttpUrl
import java.io.File
import java.security.MessageDigest

/**
 * Cache key for a Canvas API URL: path plus query in the order the app builds it.
 * Params that don't change the returned data (e.g. whether opening a message marks it read)
 * are left out so the background sync and the screens share one saved copy.
 */
fun cacheKey(url: HttpUrl): String {
    val b = StringBuilder(url.encodedPath)
    var first = true
    for (i in 0 until url.querySize) {
        val name = url.queryParameterName(i)
        if (name in IGNORED_PARAMS) continue
        b.append(if (first) '?' else '&').append(name).append('=').append(url.queryParameterValue(i).orEmpty())
        first = false
    }
    return b.toString()
}

private val IGNORED_PARAMS = setOf("auto_mark_as_read")

internal fun sha1(s: String): String =
    MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

private val tmpCounter = java.util.concurrent.atomic.AtomicLong()

/** Write via a temp file and rename, so a crash never leaves half a file behind. */
internal fun atomicWrite(target: File, bytes: ByteArray) {
    target.parentFile?.mkdirs()
    val tmp = File(target.parentFile, target.name + ".tmp" + tmpCounter.incrementAndGet())
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(target)) {
        target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}

/** Saved Canvas API responses (JSON text), one file per request. */
class ResponseCache(private val dir: File) {
    private fun file(key: String) = File(dir, sha1(key) + ".json")

    fun get(key: String): String? = file(key).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    fun has(key: String): Boolean = file(key).isFile

    fun put(key: String, text: String) = atomicWrite(file(key), text.toByteArray())

    fun remove(key: String) {
        file(key).delete()
    }

    fun clear() {
        dir.deleteRecursively()
    }

    fun size(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Drop responses nobody has refreshed for [maxAgeMs] (deleted items, old courses). */
    fun trim(maxAgeMs: Long, now: Long = System.currentTimeMillis()) {
        dir.listFiles()?.forEach { if (now - it.lastModified() > maxAgeMs) it.delete() }
    }
}

/** Saved sub-resources of Canvas HTML (mostly images), so pages look right offline. */
class ResourceCache(private val dir: File) {
    class Res(val mime: String, val charset: String?, val bytes: ByteArray, val code: Int = 200)

    private fun data(url: String) = File(dir, sha1(url) + ".bin")
    private fun meta(url: String) = File(dir, sha1(url) + ".meta")

    fun has(url: String): Boolean = data(url).isFile && meta(url).isFile

    fun get(url: String): Res? {
        val d = data(url)
        val m = meta(url)
        if (!d.isFile || !m.isFile) return null
        return runCatching {
            val lines = m.readText().split('\n')
            d.setLastModified(System.currentTimeMillis()) // keep recently used images when trimming
            Res(lines.getOrElse(0) { "application/octet-stream" }, lines.getOrNull(1)?.ifBlank { null }, d.readBytes())
        }.getOrNull()
    }

    fun put(url: String, res: Res) {
        atomicWrite(data(url), res.bytes)
        atomicWrite(meta(url), (res.mime + "\n" + (res.charset ?: "")).toByteArray())
    }

    fun clear() {
        dir.deleteRecursively()
    }

    fun size(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Keep the most recently used [maxBytes]. */
    fun trim(maxBytes: Long) {
        val all = dir.listFiles()?.filter { it.name.endsWith(".bin") }?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        for (f in all) {
            total += f.length()
            if (total > maxBytes) {
                f.delete()
                File(dir, f.name.removeSuffix(".bin") + ".meta").delete()
            }
        }
    }
}
