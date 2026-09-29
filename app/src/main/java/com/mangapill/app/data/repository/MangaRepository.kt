package com.mangapill.app.data.repository

import com.mangapill.app.data.local.AppDatabase
import com.mangapill.app.data.local.BookmarkEntity
import com.mangapill.app.data.local.MangaEntity
import com.mangapill.app.data.local.ReadingHistoryEntity
import com.mangapill.app.data.model.Bookmark
import com.mangapill.app.data.model.BookmarkStatus
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.ReadingHistory
import com.mangapill.app.data.scraper.BaseScraper
import com.mangapill.app.data.scraper.MangapillScraper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MangaRepository(private val db: AppDatabase) {

    private val scrapers: Map<String, BaseScraper> = mapOf(
        "mangapill" to MangapillScraper()
    )

    private val defaultScraper: BaseScraper get() = scrapers["mangapill"]!!

    suspend fun searchManga(query: String, sourceId: String = "mangapill"): List<Manga> {
        val scraper = scrapers[sourceId] ?: defaultScraper
        val remoteResults = scraper.search(query)
        
        db.mangaDao().insertMangas(remoteResults.map { it.toEntity() })
        return remoteResults
    }

    suspend fun getMangaDetails(mangaId: String, sourceId: String = "mangapill"): Manga {
        val scraper = scrapers[sourceId] ?: defaultScraper
        val details = scraper.getMangaDetails(mangaId)
        db.mangaDao().insertManga(details.toEntity())
        return details
    }

    suspend fun getChapterList(mangaId: String, sourceId: String = "mangapill"): List<Chapter> {
        val scraper = scrapers[sourceId] ?: defaultScraper
        return scraper.getChapterList(mangaId)
    }

    suspend fun getChapterPages(chapterId: String, sourceId: String = "mangapill"): List<PageImage> {
        val scraper = scrapers[sourceId] ?: defaultScraper
        return scraper.getChapterPages(chapterId)
    }

    suspend fun getLatestUpdates(sourceId: String = "mangapill"): List<Manga> {
        val scraper = scrapers[sourceId] ?: defaultScraper
        val updates = scraper.getLatestUpdates()
        db.mangaDao().insertMangas(updates.map { it.toEntity() })
        return updates
    }

    fun getReadingHistory(mangaId: String): Flow<ReadingHistory?> {
        return db.historyDao().getHistoryForManga(mangaId).map { entity ->
            entity?.let {
                ReadingHistory(
                    mangaId = it.mangaId,
                    chapterId = it.chapterId,
                    chapterNumber = it.chapterNumber,
                    lastReadPage = it.lastReadPage,
                    scrollPercentage = it.scrollPercentage,
                    lastReadAt = it.lastReadAt
                )
            }
        }
    }

    suspend fun saveReadingHistory(history: ReadingHistory) {
        db.historyDao().upsertHistory(
            ReadingHistoryEntity(
                mangaId = history.mangaId,
                chapterId = history.chapterId,
                chapterNumber = history.chapterNumber,
                lastReadPage = history.lastReadPage,
                scrollPercentage = history.scrollPercentage,
                lastReadAt = history.lastReadAt
            )
        )
    }

    fun isBookmarked(mangaId: String): Flow<Boolean> {
        return db.bookmarkDao().getBookmark(mangaId).map { it != null }
    }

    suspend fun toggleBookmark(mangaId: String) {
        val current = db.bookmarkDao().getBookmark(mangaId)
        // We do a one-shot check or insert/delete
        db.bookmarkDao().upsertBookmark(
            BookmarkEntity(
                mangaId = mangaId,
                status = BookmarkStatus.READING.name,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeBookmark(mangaId: String) {
        db.bookmarkDao().deleteBookmark(mangaId)
    }

    private fun Manga.toEntity(): MangaEntity {
        return MangaEntity(
            id = id,
            title = title,
            slug = slug,
            coverImage = coverImage,
            synopsis = synopsis,
            status = status.name,
            rating = rating,
            genresCsv = genres.joinToString(","),
            sourceId = sourceId,
            latestChapterNumber = latestChapterNumber
        )
    }
}
