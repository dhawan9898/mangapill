package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.SearchFilters
import org.jsoup.nodes.Document

/**
 * MangaKakalot / Manganato. Titles live on several domains, so manga and chapter ids are full URLs.
 */
class MangaKakalotScraper : BaseScraper() {
    override val sourceId = "mangakakalot"
    override val sourceName = "MangaKakalot"
    override val baseUrl = "https://mangakakalot.com"
    override val isExperimental = true
    override val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"


    /** Only the status filter exists, and only on the browse list (not together with a query). */
    override fun supports(filters: SearchFilters): Boolean =
        filters.type == null && filters.genre == null &&
            (filters.status == null || (filters.query.isBlank() && filters.status in setOf("ongoing", "completed")))

    override suspend fun search(filters: SearchFilters): List<Manga> {
        val q = filters.query.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val url = if (q.isNotEmpty()) "/search/story/$q"
        else "/manga_list?type=topview&category=all&state=${filters.status ?: "all"}&page=1"
        return parseCards(fetchDocument(url))
    }

    override suspend fun getPopular(): List<Manga> =
        parseCards(fetchDocument("/manga_list?type=topview&category=all&state=all&page=1")).take(20)

    override suspend fun getLatestUpdates(): List<Manga> =
        parseCards(fetchDocument("/manga_list?type=latest&category=all&state=all&page=1"))

    override suspend fun getMangaDetails(sourceMangaId: String): Manga {
        val doc = fetchDocument(sourceMangaId)
        val title = doc.selectFirst(".story-info-right h1, .manga-info-text h1")?.text()?.trim().orEmpty()
            .ifBlank { prettifySlug(sourceMangaId) }
        val cover = doc.selectFirst(".info-image img, .manga-info-pic img")?.imageSrc().orEmpty()
        val synopsis = doc.selectFirst("#panel-story-info-description, #noidungm")?.text().orEmpty()
            .substringAfter("Description :").trim()
        val statusText = doc.select(".story-info-right td, .manga-info-text li").text()
        val genres = doc.select(".story-info-right td a[href*=genre], .manga-info-text li a[href*=genre]")
            .map { it.text().trim() }.filter { it.isNotBlank() }

        return Manga(
            id = sourceMangaId,
            title = title,
            slug = sourceMangaId.trimEnd('/').substringAfterLast('/'),
            coverImage = cover,
            synopsis = synopsis,
            status = when {
                statusText.contains("Ongoing", true) -> MangaStatus.ONGOING
                statusText.contains("Completed", true) -> MangaStatus.COMPLETED
                else -> MangaStatus.UNKNOWN
            },
            genres = genres,
            sourceId = sourceId
        )
    }

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> {
        val doc = fetchDocument(sourceMangaId)
        val rows = doc.select(".row-content-chapter li, .chapter-list .row")
        return rows.mapNotNull { row ->
            val a = row.selectFirst("a") ?: return@mapNotNull null
            val href = a.absUrl("href").ifBlank { return@mapNotNull null }
            val text = a.text().trim()
            val number = CHAPTER_NUMBER.find(text)?.groupValues?.get(1)?.toFloatOrNull()
                ?: CHAPTER_NUMBER.find(href)?.groupValues?.get(1)?.toFloatOrNull()
                ?: 0f
            Chapter(
                id = href,
                mangaId = sourceMangaId,
                chapterNumber = number,
                title = text.ifBlank { null },
                releaseDate = row.selectFirst(".chapter-time, span[title]")?.text()?.trim()
            )
        }.distinctBy { it.id }.sortedByDescending { it.chapterNumber }
    }

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> {
        val doc = fetchDocument(sourceChapterId)
        // The image CDN checks that the referer is the chapter's own site.
        val referer = sourceChapterId.substringBefore("://") + "://" +
            sourceChapterId.substringAfter("://").substringBefore('/') + "/"
        return doc.select(".container-chapter-reader img").map { it.imageSrc() }
            .filter { it.isNotBlank() }
            .distinct()
            .mapIndexed { i, url -> PageImage(pageNumber = i + 1, imageUrl = url, referer = referer) }
    }

    internal fun parseCards(doc: Document): List<Manga> {
        return doc.select(".list-truyen-item-wrap, .story_item, .search-story-item").mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val url = a.absUrl("href").ifBlank { return@mapNotNull null }
            val title = el.selectFirst(".story_name, h3 a, h3")?.text()?.trim().orEmpty()
                .ifBlank { a.attr("title").trim() }
                .ifBlank { prettifySlug(url) }
            Manga(
                id = url,
                title = title,
                slug = url.trimEnd('/').substringAfterLast('/'),
                coverImage = el.selectFirst("img")?.imageSrc().orEmpty(),
                sourceId = sourceId
            )
        }.distinctBy { it.id }
    }

    private companion object {
        val CHAPTER_NUMBER = Regex("chapter[\\s_-]*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    }
}
