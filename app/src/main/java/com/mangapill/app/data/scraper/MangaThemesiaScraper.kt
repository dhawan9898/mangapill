package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.SearchFilters
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder

/**
 * Shared scraper for sites running the WordPress "MangaThemesia" theme or the "Madara" theme
 * ([isMadara]). Manga and chapter ids are site-relative paths (e.g. `manga/solo-leveling`,
 * `series/foo/chapter-12`) so any URL layout round-trips correctly.
 */
abstract class MangaThemesiaScraper : BaseScraper() {

    protected open val mangaListPath = "/manga"
    protected open val isMadara = false


    override fun supports(filters: SearchFilters): Boolean {
        if (!filters.hasFilters) return true
        return if (isMadara) {
            // Madara search takes genre + status together with a query, but has no type filter.
            filters.type == null && (filters.status == null || filters.status in MADARA_STATUS)
        } else {
            // Themesia filters only apply to the browse list, not to text search.
            filters.query.isBlank()
        }
    }

    override suspend fun search(filters: SearchFilters): List<Manga> {
        val q = enc(filters.query.trim())
        val url = if (isMadara) {
            val sb = StringBuilder("/?s=$q&post_type=wp-manga")
            filters.genre?.let { sb.append("&genre[]=").append(it) }
            MADARA_STATUS[filters.status]?.let { sb.append("&status[]=").append(it) }
            sb.toString()
        } else if (filters.hasFilters) {
            val sb = StringBuilder("$mangaListPath/?order=update")
            filters.status?.let { sb.append("&status=").append(it) }
            filters.type?.let { sb.append("&type=").append(it) }
            filters.genre?.let { sb.append("&genre[]=").append(it) }
            sb.toString()
        } else {
            "/?s=$q"
        }
        return parseCards(fetchDocument(url))
    }

    override suspend fun getPopular(): List<Manga> =
        parseCards(fetchDocument(if (isMadara) "$mangaListPath/?m_orderby=views" else "$mangaListPath/?order=popular")).take(20)

    override suspend fun getLatestUpdates(): List<Manga> =
        parseCards(fetchDocument(if (isMadara) "$mangaListPath/?m_orderby=latest" else "$mangaListPath/?order=update"))

    override suspend fun getMangaDetails(sourceMangaId: String): Manga {
        val doc = fetchDocument("/$sourceMangaId/")

        val title = doc.selectFirst(".entry-title, .post-title h1, h1")?.text()?.trim().orEmpty()
            .ifBlank { prettifySlug(sourceMangaId) }
        val cover = doc.selectFirst(".thumb img, .summary_image img, .thumbook img")?.imageSrc().orEmpty()
            .ifBlank { doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty() }
        val synopsis = doc.selectFirst(".entry-content[itemprop=description], .summary__content, .description-summary, .entry-content")
            ?.text()?.trim().orEmpty()
            .ifBlank { doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim().orEmpty() }
        val statusText = doc.select(".imptdt:contains(Status), .tsinfo, .post-status, .post-content_item:contains(Status)").text()
        val genres = doc.select(".mgen a, .genres-content a, .seriestugenre a").map { it.text().trim() }.filter { it.isNotBlank() }

        return Manga(
            id = sourceMangaId,
            title = title,
            slug = sourceMangaId.substringAfterLast('/'),
            coverImage = cover,
            synopsis = synopsis,
            status = when {
                statusText.contains("Ongoing", true) || statusText.contains("On going", true) -> MangaStatus.ONGOING
                statusText.contains("Completed", true) -> MangaStatus.COMPLETED
                statusText.contains("Hiatus", true) || statusText.contains("On hold", true) -> MangaStatus.HIATUS
                else -> MangaStatus.UNKNOWN
            },
            genres = genres,
            sourceId = sourceId
        )
    }

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> {
        val mangaUrl = absolute("/$sourceMangaId/")
        var doc = fetchDocument(mangaUrl)
        var rows = doc.select("#chapterlist li, li.wp-manga-chapter")
        if (rows.isEmpty() && isMadara) {
            // Newer Madara versions load the chapter list over AJAX.
            doc = fetchDocument("${mangaUrl}ajax/chapters/", form = emptyMap())
            rows = doc.select("li.wp-manga-chapter")
        }
        return parseChapters(sourceMangaId, rows)
    }

    internal fun parseChapters(sourceMangaId: String, rows: org.jsoup.select.Elements): List<Chapter> {
        return rows.mapNotNull { row ->
            val a = row.selectFirst("a[href]") ?: return@mapNotNull null
            val path = pathOf(a.absUrl("href")) ?: return@mapNotNull null
            val label = row.selectFirst(".chapternum")?.text()?.trim().orEmpty().ifBlank { a.ownText().trim() }.ifBlank { a.text().trim() }
            val number = row.attr("data-num").toFloatOrNull()
                ?: NUMBER_IN_TEXT.find(label)?.groupValues?.get(1)?.toFloatOrNull()
                ?: NUMBER_IN_URL.find(path)?.groupValues?.get(1)?.toFloatOrNull()
                ?: 0f
            Chapter(
                id = path,
                mangaId = sourceMangaId,
                chapterNumber = number,
                title = label.ifBlank { null },
                releaseDate = row.selectFirst(".chapterdate, .chapter-release-date")?.text()?.trim()?.ifBlank { null }
            )
        }.distinctBy { it.id }.sortedByDescending { it.chapterNumber }
    }

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> {
        return parsePages(fetchDocument("/$sourceChapterId/"))
    }

    internal fun parsePages(doc: Document): List<PageImage> {
        var urls = doc.select("#readerarea img, .reading-content img, .page-break img")
            .map { it.imageSrc() }
            .filter { it.isNotBlank() }

        if (urls.isEmpty()) {
            // Themesia often injects pages from a script: ts_reader.run({... "sources":[{"images":[...]}]})
            val script = doc.select("script").map { it.data() }.firstOrNull { it.contains("ts_reader.run") }
            val json = script?.let { TS_READER.find(it)?.groupValues?.get(1) }
            if (json != null) {
                val images = runCatching {
                    JSONObject(json).getJSONArray("sources").getJSONObject(0).getJSONArray("images")
                }.getOrNull()
                if (images != null) urls = (0 until images.length()).map { images.getString(it) }.filter { it.isNotBlank() }
            }
        }

        return urls.distinct().mapIndexed { i, url -> PageImage(pageNumber = i + 1, imageUrl = url, referer = "$baseUrl/") }
    }

    // ---------------------------------------------------------------------------------------------

    internal fun parseCards(doc: Document): List<Manga> {
        val cards = doc.select(".bs .bsx, .listupd .utao, .page-item-detail, .c-tabs-item__content, .row.c-tabs-item")
        return cards.mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val path = pathOf(a.absUrl("href")) ?: return@mapNotNull null
            val title = el.selectFirst(".tt, .post-title h3, .post-title h5, h3 a, h4 a")?.text()?.trim().orEmpty()
                .ifBlank { a.attr("title").trim() }
                .ifBlank { el.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty() }
                .ifBlank { prettifySlug(path) }
            Manga(
                id = path,
                title = title,
                slug = path.substringAfterLast('/'),
                coverImage = el.selectFirst("img")?.imageSrc().orEmpty(),
                sourceId = sourceId
            )
        }.distinctBy { it.id }
    }

    /** Site-relative path without leading/trailing slashes, or null for links to other hosts. */
    private fun pathOf(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = runCatching { URI(baseUrl).host }.getOrNull()
        if (uri.host != null && host != null && !uri.host.endsWith(host.removePrefix("www."))) return null
        return uri.path?.trim('/')?.ifBlank { null }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        val MADARA_STATUS = mapOf("ongoing" to "on-going", "completed" to "end", "hiatus" to "on-hold")
        val NUMBER_IN_TEXT = Regex("(\\d+(?:\\.\\d+)?)")
        val NUMBER_IN_URL = Regex("chapter-(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val TS_READER = Regex("ts_reader\\.run\\((\\{.*?\\})\\);", RegexOption.DOT_MATCHES_ALL)
    }
}
