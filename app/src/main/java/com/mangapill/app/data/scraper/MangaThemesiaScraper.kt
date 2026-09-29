package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.SearchFilters
import org.jsoup.nodes.Document
import java.net.URLEncoder

abstract class MangaThemesiaScraper : BaseScraper() {
    
    protected open val mangaListPath = "/manga"

    override suspend fun search(filters: SearchFilters): List<Manga> {
        val q = filters.query.trim()
        val url = if (q.isNotBlank()) {
            "/?s=${URLEncoder.encode(q, "UTF-8")}"
        } else {
            val status = when(filters.status) { "ongoing" -> "ongoing"; "completed" -> "completed"; "hiatus" -> "hiatus"; else -> "" }
            "$mangaListPath/?order=update&status=$status"
        }
        return parseCards(fetchDocument(url))
    }

    override suspend fun getPopular(): List<Manga> {
        return parseCards(fetchDocument("$mangaListPath/?order=popular")).take(20)
    }

    override suspend fun getLatestUpdates(): List<Manga> {
        return parseCards(fetchDocument("$mangaListPath/?order=update"))
    }

    override suspend fun getMangaDetails(sourceMangaId: String): Manga {
        val url = "$mangaListPath/$sourceMangaId/"
        val doc = fetchDocument(url)
        
        val title = doc.select(".infox h1, .post-title h1").text().trim()
        val cover = doc.select(".thumb img, .summary_image img").attr("src").ifBlank { 
            doc.select(".thumb img, .summary_image img").attr("data-lazy-src") 
        }
        val synopsis = doc.select(".entry-content, .summary__content").text().trim()
        
        val statusText = doc.select(".imptdt:contains(Status) i, .post-content_item:contains(Status) .summary-content").text().trim()
        val status = when {
            statusText.contains("Ongoing", true) -> MangaStatus.ONGOING
            statusText.contains("Completed", true) -> MangaStatus.COMPLETED
            statusText.contains("Hiatus", true) -> MangaStatus.HIATUS
            else -> MangaStatus.UNKNOWN
        }

        val genres = doc.select(".mgen a, .genres-content a").map { it.text().trim() }

        return Manga(
            id = sourceMangaId,
            title = title,
            slug = sourceMangaId,
            coverImage = cover,
            synopsis = synopsis,
            status = status,
            genres = genres,
            sourceId = sourceId
        )
    }

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> {
        val url = "$mangaListPath/$sourceMangaId/"
        val doc = fetchDocument(url)
        val chapters = ArrayList<Chapter>()
        
        val chapterElements = doc.select("#chapterlist li, .wp-manga-chapter")
        for (el in chapterElements) {
            val a = el.selectFirst("a") ?: continue
            val href = a.attr("href")
            val id = href.removeSuffix("/").substringAfterLast("/")
            val title = a.select(".chapternum, .chapter-num").text().ifBlank { a.text() }.trim()
            val number = Regex("(\\d+(?:\\.\\d+)?)").find(title)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            
            chapters.add(Chapter(id = id, mangaId = sourceMangaId, chapterNumber = number, title = title))
        }
        return chapters
    }

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> {
        val doc = fetchDocument("/$sourceChapterId/")
        val images = doc.select("#readerarea img, .reading-content img")
        
        return images.mapIndexedNotNull { i, img ->
            val src = img.attr("src").ifBlank { img.attr("data-src") }.ifBlank { img.attr("data-lazy-src") }
            if (src.isNotBlank()) {
                PageImage(
                    pageNumber = i + 1,
                    imageUrl = src,
                    referer = baseUrl
                )
            } else null
        }
    }

    private fun parseCards(doc: Document): List<Manga> {
        val list = ArrayList<Manga>()
        val els = doc.select(".bs, .c-tabs-item__content, .page-item-detail")
        for (el in els) {
            val a = el.selectFirst("a") ?: continue
            val href = a.attr("href")
            val id = href.removeSuffix("/").substringAfterLast("/")
            
            val title = el.select(".tt, .post-title h3, h3 a").text().trim()
            val cover = el.select("img").attr("src").ifBlank { el.select("img").attr("data-lazy-src") }
            
            list.add(Manga(id = id, title = title, slug = id, coverImage = cover, sourceId = sourceId))
        }
        return list
    }
}