package com.mangapill.app.data

import com.mangapill.app.data.scraper.BaseScraper
import com.mangapill.app.data.scraper.SourceRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Asks the real sites, so it only runs when LIVE_HEALTH=1 (the daily health-check workflow); a normal
 * build stays offline. For every source that is on by default: the popular list must have titles,
 * the first title's cover must download with the headers the app sends, and - for sources that host
 * pages - a chapter must list pages. A failure means the site moved, changed or started blocking.
 */
class LiveHealthTest {
    @Test
    fun everyDefaultSourceStillWorks() {
        assumeTrue("Set LIVE_HEALTH=1 to run the live checks", System.getenv("LIVE_HEALTH") == "1")
        val report = StringBuilder("## Mangapill sources\n\n| | Source | Result |\n|---|---|---|\n")
        val broken = mutableListOf<String>()
        for (source in SourceRegistry.builtIn.filter { !it.isExperimental }) {
            val result = runCatching { runBlocking { withTimeout(90_000) { check(source) } } }
            val detail = result.getOrElse { it.message ?: it.toString() }.replace("\n", " ").take(300)
            println("${if (result.isSuccess) "OK  " else "FAIL"} ${source.sourceName}: $detail")
            report.append("| ${if (result.isSuccess) "✅" else "❌"} | ${source.sourceName} | $detail |\n")
            if (result.isFailure) broken += source.sourceName
        }
        System.getenv("GITHUB_STEP_SUMMARY")?.let { runCatching { File(it).appendText(report.toString()) } }
        if (broken.isNotEmpty()) fail("Not working: ${broken.joinToString()}")
    }

    private suspend fun check(source: BaseScraper): String {
        val titles = source.getPopular()
        check(titles.isNotEmpty()) { "the popular list is empty" }
        val notes = mutableListOf("${titles.size} titles")

        val cover = titles.firstOrNull { it.coverImage.startsWith("http") }?.coverImage
        if (cover != null) {
            val request = Request.Builder().url(cover)
                .header("User-Agent", BaseScraper.imageUserAgent(java.net.URI(cover).host.orEmpty()))
                .apply { source.imageReferer?.let { header("Referer", it) } }
                .build()
            BaseScraper.sharedClient.newCall(request).execute().use { response ->
                val type = response.header("Content-Type").orEmpty()
                check(response.isSuccessful && type.startsWith("image/")) { "cover answered HTTP ${response.code} ($type)" }
            }
            notes += "cover loads"
        }

        if (source.hostsPages) {
            // Some titles are licensed and list nothing: any of the first few will do.
            val pages = titles.take(4).firstNotNullOfOrNull { title ->
                runCatching {
                    val chapter = source.getChapterList(title.id).firstOrNull() ?: return@runCatching null
                    source.getChapterPages(chapter.id).size.takeIf { it > 0 }
                }.getOrNull()
            }
            check(pages != null) { "no chapter of the first titles lists any pages" }
            notes += "a chapter has $pages pages"
        }
        return notes.joinToString(", ")
    }
}
