package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.SearchFilters
import org.jsoup.nodes.Document
import java.net.URLEncoder

class MangaKakalotScraper : BaseScraper() {
    override val sourceId = "mangakakalot"
    override val sourceName = "MangaKakalot"
    override val baseUrl = "https://mangakakalot.com"

    override suspend fun search(filters: SearchFilters): List<Manga> {
        val q = filters.query.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_")
        if (q.isBlank() && filters.type == null && filters.status == null) return getPopular()
        
        val url = if (q.isNotBlank()) {
            "/search/story/$q"
        } else {
            // Mangakakalot filter URL is complex, using manga_list
            val status = when(filters.status) { "completed" -> "completed"; "ongoing" -> "ongoing"; else -> "all" }
            "/manga_list?type=topview&category=all&state=$status&page=1"
        }
        
        return parseSearchCards(fetchDocument(url))
    }

    override suspend fun getPopular(): List<Manga> {
        return parseSearchCards(fetchDocument("/manga_list?type=topview&category=all&state=all&page=1")).take(20)
    }

    override suspend fun getLatestUpdates(): List<Manga> {
        return parseSearchCards(fetchDocument("/manga_list?type=latest&category=all&state=all&page=1"))
    }

    override suspend fun getMangaDetails(sourceMangaId: String): Manga {
        val url = if (sourceMangaId.startsWith("manga-")) "https://chapmanganato.com/$sourceMangaId" else "/manga/$sourceMangaId"
        val doc = fetchDocument(url)
        val title = doc.select(".story-info-right h1").text().trim().ifBlank { doc.select(".manga-info-text h1").text().trim() }
        val cover = doc.select(".info-image img").attr("src").ifBlank { doc.select(".manga-info-pic img").attr("src") }
        val synopsis = doc.select("#panel-story-info-description").text().removePrefix("Description :").trim()
        val statusText = doc.select(".story-info-right li:contains(Status)").text()
        
        return Manga(
            id = sourceMangaId,
            title = title,
            slug = sourceMangaId,
            coverImage = cover,
            synopsis = synopsis,
            status = if (statusText.contains("Ongoing", true)) MangaStatus.ONGOING else if (statusText.contains("Completed", true)) MangaStatus.COMPLETED else MangaStatus.UNKNOWN,
            sourceId = sourceId
        )
    }

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> {
        val url = if (sourceMangaId.startsWith("manga-")) "https://chapmanganato.com/$sourceMangaId" else "/manga/$sourceMangaId"
        val doc = fetchDocument(url)
        val chapters = ArrayList<Chapter>()
        
        val els = doc.select(".row-content-chapter li a").ifEmpty { doc.select(".chapter-list .row a") }
        for (a in els) {
            val href = a.attr("href")
            // Pass the full absolute URL as the chapter ID since MangaKakalot uses different domains
            val id = href
            val title = a.text().trim()
            val number = Regex("chapter[- ]?(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(href)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            
            chapters.add(Chapter(id = id, mangaId = sourceMangaId, chapterNumber = number, title = title))
        }
        return chapters
    }

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> {
        // sourceChapterId is the full URL
        val doc = fetchDocument(sourceChapterId)
        val images = doc.select(".container-chapter-reader img")
        return images.mapIndexed { i, img ->
            PageImage(
                pageNumber = i + 1,
                imageUrl = img.attr("src"),
                referer = "https://chapmanganato.com/"
            )
        }
    }

    private fun parseSearchCards(doc: Document): List<Manga> {
        val list = ArrayList<Manga>()
        val els = doc.select(".list-truyen-item-wrap, .story_item")
        for (el in els) {
            val a = el.selectFirst("a") ?: continue
            val href = a.attr("href")
            val id = href.substringAfterLast("/")
            val title = el.select(".story_name, h3 a").text().trim()
            val cover = el.select("img").attr("src")
            list.add(Manga(id = id, title = title, slug = id, coverImage = cover, sourceId = sourceId))
        }
        return list
    }
}