package com.rafat.canvos.data.api

import com.rafat.canvos.data.offline.ResponseCache
import com.rafat.canvos.data.offline.cacheKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Any failure talking to Canvas, with a message fit for showing to the user. */
open class CanvasException(message: String, val status: Int = 0, cause: Throwable? = null) :
    IOException(message, cause)

/** 401: the token is wrong, revoked or expired. */
class UnauthorizedException(message: String) : CanvasException(message, 401)

/** Write attempted while the debug read-only switch is on. */
class ReadOnlyException : CanvasException("Read-only mode is on; nothing was changed.")

/** The request never got an answer: no connection, DNS failure, timeout... */
open class NetworkException(message: String, cause: Throwable? = null) : CanvasException(message, 0, cause)

/** Offline, and there is no saved copy of what was asked for. */
class NotSavedException(cause: Throwable? = null) :
    NetworkException("You're offline, and this hasn't been saved for offline use yet.", cause)

/**
 * Put in the coroutine context to skip saved copies (background jobs must never mistake
 * old data for new). Saving fresh responses still happens.
 */
class CachePolicy(val networkOnly: Boolean) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CachePolicy>
}

val NetworkOnly = CachePolicy(networkOnly = true)

/** App-wide connectivity, fed by the system's network callback. */
object NetworkState {
    /** Some network with internet capability is up (not necessarily working). */
    val hasNetwork = MutableStateFlow(true)
    private val savedHits = AtomicLong()

    /** Increases every time a saved copy is served instead of live data. */
    val savedServed: Long get() = savedHits.get()
    internal fun servedSaved() {
        savedHits.incrementAndGet()
    }
}

val CanvasJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

/**
 * Minimal Canvas REST client. The bearer token is only ever sent to the Canvas host
 * itself (checked on every network hop, so redirects to file storage never see it).
 *
 * Every successful GET is saved to [cache]; when there is no connection (or Canvas is
 * down) the saved copy is returned instead, which is what makes the app work offline.
 */
class CanvasApi(
    baseUrl: String,
    private val token: String,
    /** Debug safety switch: when true every non-GET request is refused. */
    val readOnly: Boolean = false,
    /** Saved responses for offline use; null turns offline support off (tests). */
    private val cache: ResponseCache? = null,
    private val hasNetwork: () -> Boolean = { NetworkState.hasNetwork.value },
) {
    val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()
    val host: String get() = base.host

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addNetworkInterceptor(authInterceptor())
        .build()

    fun authInterceptor() = Interceptor { chain ->
        val req = chain.request()
        val b = req.newBuilder().header("User-Agent", USER_AGENT)
        if (isCanvasHost(req.url)) b.header("Authorization", "Bearer $token") else b.removeHeader("Authorization")
        chain.proceed(b.build())
    }

    fun isCanvasHost(url: HttpUrl) =
        url.host.equals(base.host, ignoreCase = true) && url.scheme == base.scheme && url.port == base.port

    /** Absolute URL for an API path ("/api/v1/...") or pass through a full URL. */
    fun url(path: String, params: List<Pair<String, String>> = emptyList()): HttpUrl {
        val u = if (path.startsWith("http")) path.toHttpUrl() else base.resolve(path)
            ?: throw CanvasException("Bad path $path")
        if (params.isEmpty()) return u
        val b = u.newBuilder()
        for ((k, v) in params) b.addQueryParameter(k, v)
        return b.build()
    }

    suspend fun <T> get(path: String, ser: DeserializationStrategy<T>, params: List<Pair<String, String>> = emptyList()): T {
        val u = url(path, params)
        val text = load(u) { readBody(execute(Request.Builder().url(u).get().build())) }
        return decodeText(text, ser)
    }

    /** The URL [getAll] requests first (and saves the whole list under). */
    fun listUrl(path: String, params: List<Pair<String, String>> = emptyList()): HttpUrl =
        url(path, if (params.none { it.first == "per_page" }) params + ("per_page" to "100") else params)

    /** GET every page of a list endpoint by following `Link: rel="next"`. */
    suspend fun <T> getAll(
        path: String,
        ser: kotlinx.serialization.KSerializer<T>,
        params: List<Pair<String, String>> = emptyList(),
        maxPages: Int = 40,
    ): List<T> {
        val first = listUrl(path, params)
        val text = load(first) {
            val pages = ArrayList<String>()
            var next: HttpUrl? = first
            while (next != null && pages.size < maxPages) {
                val resp = execute(Request.Builder().url(next).get().build())
                val linkNext = nextLink(resp.header("Link"))
                pages += readBody(resp)
                next = linkNext?.toHttpUrlOrNull()
            }
            mergeJsonArrays(pages)
        }
        return decodeText(text, ListSerializer(ser))
    }

    private fun cacheFor(url: HttpUrl): ResponseCache? =
        cache?.takeIf { isCanvasHost(url) && NOT_SAVED.none { p -> url.encodedPath.startsWith(p) } }

    /**
     * Live data when possible (saving it), otherwise the saved copy. HTTP errors other than
     * server failures are real answers and are passed on, not hidden behind old data.
     */
    private suspend fun load(url: HttpUrl, network: suspend () -> String): String {
        val c = cacheFor(url) ?: return network()
        val key = cacheKey(url)
        val allowSaved = currentCoroutineContext()[CachePolicy]?.networkOnly != true
        if (allowSaved && !hasNetwork()) {
            return saved(c, key) ?: throw NotSavedException()
        }
        val text = try {
            network()
        } catch (e: CanvasException) {
            if (allowSaved && (e is NetworkException || e.status >= 500)) saved(c, key)?.let { return it }
            if (e.status == 404) withContext(Dispatchers.IO) { c.remove(key) }
            throw e
        }
        withContext(Dispatchers.IO) { runCatching { c.put(key, text) } }
        return text
    }

    private suspend fun saved(c: ResponseCache, key: String): String? =
        withContext(Dispatchers.IO) { c.get(key) }?.also { NetworkState.servedSaved() }

    /** Save [json] as the answer to a GET the app makes, e.g. folder lists built from one flat list. */
    suspend fun saveForOffline(url: HttpUrl, json: String) {
        val c = cacheFor(url) ?: return
        withContext(Dispatchers.IO) { c.put(cacheKey(url), json) }
    }

    /** A saved answer for [url], or null. Never touches the network. */
    suspend fun <T> savedCopy(url: HttpUrl, ser: DeserializationStrategy<T>): T? {
        val c = cacheFor(url) ?: return null
        val text = withContext(Dispatchers.IO) { c.get(cacheKey(url)) } ?: return null
        return runCatching { decodeText(text, ser) }.getOrNull()
    }

    fun isSaved(url: HttpUrl): Boolean = cacheFor(url)?.has(cacheKey(url)) == true

    suspend fun <T> send(
        method: String,
        path: String,
        form: List<Pair<String, String>>,
        ser: DeserializationStrategy<T>,
    ): T = decode(sendRaw(method, path, form), ser)

    suspend fun sendRaw(method: String, path: String, form: List<Pair<String, String>> = emptyList()): Response {
        if (readOnly) throw ReadOnlyException()
        val body: RequestBody = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(url(path)).method(method, body).build())
    }

    suspend fun execute(request: Request, client: OkHttpClient = http): Response {
        val resp = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw networkError(e, request.url.host)
        }
        if (!resp.isSuccessful) {
            val text = runCatching { resp.body.string() }.getOrDefault("")
            resp.close()
            val msg = errorMessage(text)
            when (resp.code) {
                // Canvas also answers 401 to a valid token that may not see something; only
                // a token problem (WWW-Authenticate, or no "not authorized" message) means logging in again.
                401 -> if (resp.header("WWW-Authenticate") == null && msg?.contains("not authorized", ignoreCase = true) == true) {
                    throw CanvasException("You don't have permission to see this.", 401)
                } else {
                    throw UnauthorizedException(msg ?: "Your access token is invalid or has expired.")
                }
                403 -> throw CanvasException(msg ?: "You don't have permission to see this.", 403)
                404 -> throw CanvasException(msg ?: "Not found. It may have been deleted or unpublished.", 404)
                else -> throw CanvasException(msg ?: "Canvas error ${resp.code}", resp.code)
            }
        }
        return resp
    }

    suspend fun <T> decode(resp: Response, ser: DeserializationStrategy<T>): T = decodeText(readBody(resp), ser)

    /** Read (and close) a response body off the main thread. */
    suspend fun readBody(resp: Response): String = withContext(Dispatchers.IO) {
        try {
            resp.use { it.body.string() }
        } catch (e: IOException) {
            throw networkError(e, resp.request.url.host)
        }
    }

    suspend fun <T> decodeText(text: String, ser: DeserializationStrategy<T>): T = withContext(Dispatchers.Default) {
        try {
            CanvasJson.decodeFromString(ser, text.ifBlank { "null" })
        } catch (e: Exception) {
            throw CanvasException("Unexpected response from Canvas (${e.message?.take(120)})", cause = e)
        }
    }

    companion object {
        const val USER_AGENT = "Canvos/1.0 (Android; Canvas student client)"

        /** One-time or per-search answers that must never be replayed from disk. */
        private val NOT_SAVED = listOf("/login/", "/api/v1/search/")

        fun networkError(e: IOException, host: String): CanvasException = when (e) {
            is CanvasException -> e
            is UnknownHostException -> NetworkException("Can't reach $host. Check your internet connection.", e)
            is SocketTimeoutException -> NetworkException("Canvas took too long to respond. Try again.", e)
            else -> NetworkException("Network error: ${e.message ?: e.javaClass.simpleName}", e)
        }

        /** Join the JSON arrays of several pages into one array. */
        fun mergeJsonArrays(pages: List<String>): String {
            if (pages.size == 1) return pages[0]
            val parts = pages.map { p ->
                val t = p.trim()
                if (!t.startsWith("[") || !t.endsWith("]")) throw CanvasException("Unexpected response from Canvas (not a list)")
                t.substring(1, t.length - 1).trim()
            }.filter { it.isNotEmpty() }
            return parts.joinToString(",", "[", "]")
        }

        /** Pull the human message out of Canvas' various error shapes. */
        fun errorMessage(body: String): String? = runCatching {
            val el = CanvasJson.parseToJsonElement(body)
            val obj = el as? JsonObject ?: return null
            obj["message"]?.jsonPrimitive?.contentOrNull?.let { return it }
            when (val errs = obj["errors"]) {
                is JsonArray -> errs.firstNotNullOfOrNull { e ->
                    (e as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                }
                is JsonObject -> errs.values.firstNotNullOfOrNull { v ->
                    when (v) {
                        is JsonArray -> v.firstOrNull()?.let { m ->
                            (m as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                                ?: (m as? JsonPrimitive)?.contentOrNull
                        }
                        is JsonPrimitive -> v.contentOrNull
                        else -> null
                    }
                }
                else -> null
            }
        }.getOrNull()

        /** `<url>; rel="current",<url>; rel="next"` -> next url */
        fun nextLink(header: String?): String? {
            if (header.isNullOrBlank()) return null
            for (part in header.split(',')) {
                val segs = part.split(';').map { it.trim() }
                if (segs.size < 2) continue
                if (segs.drop(1).any { it.replace(" ", "") == "rel=\"next\"" }) {
                    return segs[0].removePrefix("<").removeSuffix(">")
                }
            }
            return null
        }
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, r, _ -> r.close() }
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
