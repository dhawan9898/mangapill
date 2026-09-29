package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage

class MangapillScraper : BaseScraper() {
    override val sourceId = "mangapill"
    override val sourceName = "Mangapill"
    override val baseUrl = "https://mangapill.com"

    override suspend fun search(query: String): List<Manga> {
        val doc = fetchDocument("/search?q=${encodeURIComponent(query)}")
        val results = mutableListOf<Manga>()

        doc.select("div.grid > div").forEach { el ->
            val link = el.select("a").first()
            val href = link?.attr("href") ?: ""
            val sourceMangaId = href.removePrefix("/manga/")
            val title = el.select("div.font-bold, a.font-bold").text().trim()
            val coverImg = el.select("img").attr("data-src").ifEmpty { el.select("img").attr("src") }

            if (sourceMangaId.isNotBlank() && title.isNotBlank()) {
                results.add(
                    Manga(
                        id = sourceMangaId,
                        title = title,
                        slug = sourceMangaId.substringAfter("/"),
                        coverImage = coverImg,
                        sourceId = sourceId
                    )
                )
            }
        }
        return results
    }

    override suspend fun getMangaDetails(sourceMangaId: String): Manga {
        val doc = fetchDocument("/manga/$sourceMangaId")
        val title = doc.select("h1").text().trim()
        val coverImage = doc.select("div.flex img").attr("data-src")
            .ifEmpty { doc.select("div.flex img").attr("src") }
        val synopsis = doc.select("p.text-sm, div.text-sm").text().trim()

        val genres = doc.select("a[href*=/manga?genre=]").map { it.text().trim() }

        return Manga(
            id = sourceMangaId,
            title = title,
            slug = sourceMangaId.substringAfter("/"),
            coverImage = coverImage,
            synopsis = synopsis,
            status = MangaStatus.ONGOING,
            rating = 4.8f,
            genres = genres,
            sourceId = sourceId
        )
    }

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> {
        val doc = fetchDocument("/manga/$sourceMangaId")
        val chapters = mutableListOf<Chapter>()

        doc.select("#chapters a").forEach { el ->
            val href = el.attr("href")
            val sourceChapterId = href.removePrefix("/chapters/")
            val rawText = el.text().trim()

            val chapterNumMatch = Regex("Chapter\\s+([\\d.]+)").find(rawText)
            val chapterNumber = chapterNumMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

            chapters.add(
                Chapter(
                    id = sourceChapterId,
                    mangaId = sourceMangaId,
                    chapterNumber = chapterNumber,
                    title = rawText,
                    url = href
                )
            )
        }
        return chapters
    }

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> {
        val doc = fetchDocument("/chapters/$sourceChapterId")
        val pages = mutableListOf<PageImage>()

        doc.select("chapter-page img, div.relative img").forEachIndexed { index, img ->
            val imageUrl = img.attr("data-src").ifEmpty { img.attr("src") }
            if (imageUrl.isNotBlank()) {
                pages.add(
                    PageImage(
                        pageNumber = index + 1,
                        imageUrl = imageUrl,
                        referer = baseUrl
                    )
                )
            }
        }
        return pages
    }

    override suspend fun getLatestUpdates(): List<Manga> {
        val doc = fetchDocument("/")
        val results = mutableListOf<Manga>()

        doc.select("div.grid > div").forEach { el ->
            val link = el.select("a").first()
            val href = link?.attr("href") ?: ""
            val sourceMangaId = href.removePrefix("/manga/")
            val title = el.select("a.font-bold, div.font-bold").text().trim()
            val coverImg = el.select("img").attr("data-src").ifEmpty { el.select("img").attr("src") }

            if (sourceMangaId.isNotBlank() && title.isNotBlank()) {
                results.add(
                    Manga(
                        id = sourceMangaId,
                        title = title,
                        slug = sourceMangaId,
                        coverImage = coverImg,
                        sourceId = sourceId
                    )
                )
            }
        }
        return results
    }

    private fun encodeURIComponent(s: String): String {
        return java.net.URLEncoder.encode(s, "UTF-8")
    }
}
