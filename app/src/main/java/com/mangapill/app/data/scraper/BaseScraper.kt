package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.PageImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ScraperException(message: String, cause: Throwable? = null) : IOException(message, cause)

abstract class BaseScraper {
    abstract val sourceId: String
    abstract val sourceName: String
    abstract val baseUrl: String

    protected open val userAgent: String = DEFAULT_USER_AGENT
    protected open val maxRetries = 3
    protected open val minDelayBetweenRequestsMs = 250L

    private val gate = Mutex()
    private var lastRequestTimestamp = 0L

    protected val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .cookieJar(MemoryCookieJar())
            .build()
    }

    private suspend fun throttle() {
        gate.withLock {
            val wait = minDelayBetweenRequestsMs - (System.currentTimeMillis() - lastRequestTimestamp)
            if (wait > 0) delay(wait)
            lastRequestTimestamp = System.currentTimeMillis()
        }
    }

    /** GET [url] (absolute, or relative to [baseUrl]) and return the body. Retries transient failures. */
    protected suspend fun fetchText(url: String, headers: Map<String, String> = emptyMap()): String {
        val fullUrl = if (url.startsWith("http")) url else "$baseUrl$url"
        var lastError: Exception? = null

        for (attempt in 1..maxRetries) {
            throttle()
            try {
                return withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(fullUrl)
                        .header("User-Agent", userAgent)
                        .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Referer", "$baseUrl/")
                        .apply { headers.forEach { (k, v) -> header(k, v) } }
                        .build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val blocked = (response.code == 403 || response.code == 503) &&
                                (body.contains("Just a moment", true) || body.contains("cf-chl", true))
                            throw HttpFailure(
                                response.code,
                                if (blocked) "$sourceName is protected by Cloudflare and blocked the request"
                                else "$sourceName returned HTTP ${response.code}"
                            )
                        }
                        body
                    }
                }
            } catch (e: HttpFailure) {
                lastError = e
                // Client errors (other than rate limiting) will not fix themselves.
                if (e.code in 400..499 && e.code != 429) throw e
            } catch (e: IOException) {
                lastError = e
            }
            if (attempt < maxRetries) delay(700L * attempt)
        }
        throw ScraperException(lastError?.message ?: "Failed to fetch $fullUrl", lastError)
    }

    protected suspend fun fetchDocument(url: String): Document {
        val html = fetchText(url)
        return withContext(Dispatchers.Default) { Jsoup.parse(html, baseUrl) }
    }

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

    abstract suspend fun search(query: String): List<Manga>
    abstract suspend fun getPopular(): List<Manga>
    abstract suspend fun getLatestUpdates(): List<Manga>
    abstract suspend fun getMangaDetails(sourceMangaId: String): Manga
    abstract suspend fun getChapterList(sourceMangaId: String): List<Chapter>
    abstract suspend fun getChapterPages(sourceChapterId: String): List<PageImage>

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
