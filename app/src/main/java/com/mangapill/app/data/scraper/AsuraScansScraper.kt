package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.SearchFilters
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder

/**
 * Scraper for asurascans.com (custom Astro application).
 */
class AsuraScansScraper : BaseScraper() {
    override val sourceId = "asurascans"
    override val sourceName = "Asura Scans"
    override val baseUrl = "https://asurascans.com"
    override val isExperimental = false
    override val imageReferer = "https://asurascans.com/"

    override fun supports(filters: SearchFilters): Boolean {
        // AsuraScans search currently only supports text query on browse/comics page.
        return filters.type == null && filters.status == null && filters.genre == null
    }

    override suspend fun search(filters: SearchFilters): List<Manga> {
        val q = filters.query.trim()
        val url = if (q.isNotEmpty()) "/browse/comics?search=${enc(q)}" else "/browse/comics"
        return parseCards(fetchDocument(url))
    }

    override suspend fun getPopular(): List<Manga> =
        parseCards(fetchDocument("/browse/comics")).take(20)

    override suspend fun getLatestUpdates(): List<Manga> =
        parseCards(fetchDocument("/comics"))

    override suspend fun getMangaDetails(sourceMangaId: String): Manga {
        val path = normalizeMangaPath(sourceMangaId)
        val doc = fetchDocument("/$path")

        val title = doc.selectFirst("h1, h2, meta[property=og:title]")?.let {
            if (it.tagName() == "meta") it.attr("content").substringBefore(" |") else it.text()
        }?.trim().orEmpty().ifBlank { prettifySlug(sourceMangaId) }

        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            .ifBlank { doc.selectFirst("img[src*=/covers/]")?.imageSrc().orEmpty() }

        val synopsis = doc.selectFirst("meta[name=description]")?.attr("content")?.trim().orEmpty()
            .ifBlank { doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim().orEmpty() }

        val statusText = doc.body().text().lowercase()
        val status = when {
            "ongoing" in statusText -> MangaStatus.ONGOING
            "completed" in statusText -> MangaStatus.COMPLETED
            "hiatus" in statusText -> MangaStatus.HIATUS
            else -> MangaStatus.UNKNOWN
        }

        val genres = doc.select("a[href*=genre], meta[articleSection]").map {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }.flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        return Manga(
            id = path,
            title = title,
            slug = path.substringAfterLast('/'),
            coverImage = cover,
            synopsis = synopsis,
            status = status,
            genres = genres,
            sourceId = sourceId
        )
    }

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> {
        val path = normalizeMangaPath(sourceMangaId)
        val doc = fetchDocument("/$path")
        val seen = HashSet<String>()
        val chapters = ArrayList<Chapter>()

        for (a in doc.select("a[href*=/chapter/]")) {
            val href = a.attr("href")
            val chapterPath = pathOf(href) ?: continue
            if (!seen.add(chapterPath)) continue

            val text = a.text().trim()
            val number = CHAPTER_REGEX.find(chapterPath)?.groupValues?.get(1)?.toFloatOrNull()
                ?: CHAPTER_REGEX.find(text)?.groupValues?.get(1)?.toFloatOrNull()
                ?: 0f

            chapters.add(
                Chapter(
                    id = chapterPath,
                    mangaId = path,
                    chapterNumber = number,
                    title = text.ifBlank { "Chapter $number" }
                )
            )
        }

        return chapters.sortedByDescending { it.chapterNumber }
    }

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> {
        val doc = fetchDocument("/$sourceChapterId")
        val imgs = doc.select("[data-page] img, img[data-page-index], img[src*=/asura-images/chapters/]")
        val urls = imgs.map { it.imageSrc() }.filter { it.isNotBlank() }.distinct()

        return urls.mapIndexed { i, url ->
            PageImage(pageNumber = i + 1, imageUrl = url, referer = imageReferer)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun parseCards(doc: Document): List<Manga> {
        val cards = ArrayList<Manga>()
        val seen = HashSet<String>()

        for (el in doc.select(".series-card, a[href*=/comics/]")) {
            val a = if (el.tagName() == "a") el else el.selectFirst("a[href]") ?: continue
            val path = pathOf(a.attr("href")) ?: continue
            if (path.contains("/chapter/")) continue
            if (!seen.add(path)) continue

            val title = el.selectFirst("h3, .title, img[alt]")?.let {
                if (it.hasAttr("alt")) it.attr("alt") else it.text()
            }?.trim().orEmpty().ifBlank { prettifySlug(path) }

            val cover = el.selectFirst("img")?.imageSrc().orEmpty()

            cards.add(
                Manga(
                    id = path,
                    title = title,
                    slug = path.substringAfterLast('/'),
                    coverImage = cover,
                    sourceId = sourceId
                )
            )
        }

        return cards
    }

    private fun normalizeMangaPath(rawId: String): String =
        if (rawId.startsWith("comics/")) rawId else "comics/$rawId"

    private fun pathOf(url: String): String? {
        val path = runCatching { URI(url).path }.getOrNull() ?: url
        return path.trim('/').ifBlank { null }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        val CHAPTER_REGEX = Regex("chapter/(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    }
}
