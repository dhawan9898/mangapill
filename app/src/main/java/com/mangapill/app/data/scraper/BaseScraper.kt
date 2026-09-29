package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.PageImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit

abstract class BaseScraper {
    abstract val sourceId: String
    abstract val sourceName: String
    abstract val baseUrl: String

    protected open val maxRetries = 3
    protected open val minDelayBetweenRequestsMs = 400L
    private var lastRequestTimestamp = 0L

    protected val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    )

    protected fun getRandomUserAgent(): String = userAgents.random()

    protected suspend fun fetchDocument(url: String, extraHeaders: Map<String, String> = emptyMap()): Document {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTimestamp
            if (elapsed < minDelayBetweenRequestsMs) {
                delay(minDelayBetweenRequestsMs - elapsed)
            }

            var attempt = 0
            var lastException: Exception? = null

            val fullUrl = if (url.startsWith("http")) url else "$baseUrl$url"

            while (attempt < maxRetries) {
                attempt++
                try {
                    lastRequestTimestamp = System.currentTimeMillis()
                    val requestBuilder = Request.Builder()
                        .url(fullUrl)
                        .header("User-Agent", getRandomUserAgent())
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Referer", baseUrl)

                    extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

                    val response = okHttpClient.newCall(requestBuilder.build()).execute()
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} for $fullUrl")
                    }

                    val bodyString = response.body?.string() ?: throw IOException("Empty body for $fullUrl")
                    return@withContext Jsoup.parse(bodyString, baseUrl)
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < maxRetries) {
                        delay(1000L * attempt)
                    }
                }
            }
            throw IOException("Failed to fetch $fullUrl after $maxRetries attempts: ${lastException?.message}", lastException)
        }
    }

    abstract suspend fun search(query: String): List<Manga>
    abstract suspend fun getMangaDetails(sourceMangaId: String): Manga
    abstract suspend fun getChapterList(sourceMangaId: String): List<Chapter>
    abstract suspend fun getChapterPages(sourceChapterId: String): List<PageImage>
    abstract suspend fun getLatestUpdates(): List<Manga>
}
