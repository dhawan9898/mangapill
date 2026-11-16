package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.SearchFilters
import com.mangapill.app.data.opds.OpdsAuthInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScraperException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Base class for every provider. Subclasses only describe URLs and parsing; this class owns the
 * networking: a shared HTTP client, per-source throttling, retries, Cloudflare detection and
 * cancellation (a cancelled coroutine cancels the underlying HTTP call, so timeouts are real).
 */
abstract class BaseScraper {
    /**
     * False for catalogue-only sources (search, details and chapter lists, but no page images).
     * Their chapters are read from another source that has the same title (see ChapterMirror).
     */
    open val hostsPages: Boolean = true

    abstract val sourceId: String
    abstract val sourceName: String
    abstract val baseUrl: String

    /** Sources that could not be verified end-to-end are off by default and can be enabled in Settings. */
    open val isExperimental: Boolean = false

    /** Referer the source's image CDN expects (hotlink protection), or null if it needs none. */
    open val imageReferer: String? get() = "$baseUrl/"

    protected open val userAgent: String = DEFAULT_USER_AGENT
    protected open val maxRetries = 2
    protected open val minDelayBetweenRequestsMs = 250L

    private val gate = Mutex()
    private var lastRequestTimestamp = 0L

    // ---- Capabilities -------------------------------------------------------------------------

    /** Whether [search] honours every filter in [filters]. Unsupported sources are skipped. */
    open fun supports(filters: SearchFilters): Boolean = !filters.hasFilters

    open val supportsHiddenGems: Boolean = false

    // ---- Source API -----------------------------------------------------------------------------

    abstract suspend fun search(filters: SearchFilters): List<Manga>
    abstract suspend fun getPopular(): List<Manga>
    abstract suspend fun getLatestUpdates(): List<Manga>
    abstract suspend fun getMangaDetails(sourceMangaId: String): Manga
    abstract suspend fun getChapterList(sourceMangaId: String): List<Chapter>
    abstract suspend fun getChapterPages(sourceChapterId: String): List<PageImage>

    /** Highly rated titles that are not in the mainstream lists. */
    open suspend fun getHiddenGems(): List<Manga> = emptyList()

    // ---- Networking -----------------------------------------------------------------------------

    private suspend fun throttle() {
        gate.withLock {
            val wait = minDelayBetweenRequestsMs - (System.currentTimeMillis() - lastRequestTimestamp)
            if (wait > 0) delay(wait)
            lastRequestTimestamp = System.currentTimeMillis()
        }
    }

    protected fun absolute(url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> "$baseUrl/$url"
    }

    /**
     * GET (or POST when [form] is given) [url] and return the body. Transient failures are retried;
     * 4xx responses (other than 429) fail immediately.
     */
    protected suspend fun fetchText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        form: Map<String, String>? = null
    ): String {
        val fullUrl = absolute(url)
        var lastError: Exception? = null

        for (attempt in 1..maxRetries) {
            throttle()
            try {
                val builder = Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Referer", "$baseUrl/")
                headers.forEach { (k, v) -> builder.header(k, v) }
                if (form != null) {
                    builder.post(FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build())
                    builder.header("X-Requested-With", "XMLHttpRequest")
                }

                val response = sharedClient.newCall(builder.build()).await()
                val (code, body) = withContext(Dispatchers.IO) {
                    response.use { it.code to it.body?.string().orEmpty() }
                }
                if (code !in 200..299) {
                    val blocked = (code == 403 || code == 503) &&
                        (body.contains("Just a moment", true) || body.contains("cf-chl", true))
                    throw HttpFailure(
                        code,
                        if (blocked) "$sourceName is protected by Cloudflare and blocked the request"
                        else "$sourceName returned HTTP $code"
                    )
                }
                return body
            } catch (e: HttpFailure) {
                lastError = e
                if (e.code in 400..499 && e.code != 429) throw ScraperException(e.message ?: "HTTP ${e.code}", e)
            } catch (e: IOException) {
                lastError = e
            }
            if (attempt < maxRetries) delay(600L * attempt)
        }
        throw ScraperException(lastError?.message ?: "Failed to fetch $fullUrl", lastError)
    }

    protected suspend fun fetchDocument(url: String, form: Map<String, String>? = null): Document {
        val fullUrl = absolute(url)
        val html = fetchText(fullUrl, form = form)
        // Parse against the real URL so relative links resolve correctly even on other domains.
        return withContext(Dispatchers.Default) { Jsoup.parse(html, fullUrl) }
    }

    // ---- Parsing helpers ------------------------------------------------------------------------

    /** Best image URL of an <img>, handling lazy-loading attributes, srcset and relative URLs. */
    protected fun Element.imageSrc(): String {
        for (attr in IMAGE_ATTRS) {
            val raw = attr(attr).trim()
            if (raw.isEmpty() || raw.startsWith("data:")) continue
            return absUrl(attr).ifEmpty { absolute(raw) }
        }
        val srcset = attr("data-srcset").ifBlank { attr("srcset") }.trim()
        if (srcset.isNotEmpty()) {
            val first = srcset.split(",").first().trim().substringBefore(" ")
            if (first.isNotEmpty() && !first.startsWith("data:")) return absolute(first)
        }
        return ""
    }

    protected fun prettifySlug(id: String): String =
        id.trimEnd('/').substringAfterLast('/').split('-', '_').filter { it.isNotBlank() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    private class HttpFailure(val code: Int, message: String) : IOException(message)

    private class MemoryCookieJar : CookieJar {
        private val store = ConcurrentHashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val kept = store[url.host].orEmpty().filter { old -> cookies.none { it.name == old.name } }
            store[url.host] = kept + cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return store[url.host].orEmpty().filter { it.expiresAt > now }
        }
    }

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        /** Sent to hosts that turn away browser User-Agents coming from something that is not a browser. */
        const val APP_USER_AGENT = "MangapillApp/1.0 (Android)"

        /**
         * User-Agent for an image request to [host]. Most CDNs want a browser one; MangaDex answers a
         * browser User-Agent with a redirect to its bot challenge page, so its hosts get the app's own.
         */
        fun imageUserAgent(host: String): String {
            val h = host.lowercase()
            val mangaDex = h == "mangadex.org" || h.endsWith(".mangadex.org") || h.endsWith(".mangadex.network")
            return if (mangaDex) APP_USER_AGENT else DEFAULT_USER_AGENT
        }

        private val IMAGE_ATTRS = listOf("data-src", "data-lazy-src", "data-original", "data-cfsrc", "src")

        /** One client for all sources so connections and DNS are shared. */
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .cookieJar(MemoryCookieJar())
                .addInterceptor(OpdsAuthInterceptor)
                .build()
        }

        private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { runCatching { cancel() } }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response) else response.close()
                }
            })
        }
    }
}
