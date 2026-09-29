package com.mangapill.app.data.repository

import com.mangapill.app.data.local.AppDatabase
import com.mangapill.app.data.local.BookmarkEntity
import com.mangapill.app.data.local.MangaEntity
import com.mangapill.app.data.local.ReadingHistoryEntity
import com.mangapill.app.data.model.BookmarkStatus
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.ReadingHistory
import com.mangapill.app.data.model.SourceInfo
import com.mangapill.app.data.scraper.BaseScraper
import com.mangapill.app.data.scraper.MangaDexScraper
import com.mangapill.app.data.scraper.MangapillScraper
import com.mangapill.app.data.scraper.ComickScraper
import com.mangapill.app.data.scraper.MangaKakalotScraper
import com.mangapill.app.data.scraper.AsuraScansScraper
import com.mangapill.app.data.scraper.FlameComicsScraper
import com.mangapill.app.data.scraper.ManhuaPlusScraper
import com.mangapill.app.data.scraper.ReaperScansScraper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** A row of the reading-history screen. */
data class HistoryEntry(
    val manga: Manga,
    val chapterId: String,
    val chapterNumber: Float,
    val lastReadPage: Int,
    val progress: Float,
    val lastReadAt: Long
)

class MangaRepository(private val db: AppDatabase) {

    private val scrapers: Map<String, BaseScraper> =
        listOf<BaseScraper>(
            MangapillScraper(), 
            MangaDexScraper(), 
            ComickScraper(), 
            MangaKakalotScraper(),
            AsuraScansScraper(),
            ReaperScansScraper(),
            FlameComicsScraper(),
            ManhuaPlusScraper()
        ).associateBy { it.sourceId }

    val sources: List<SourceInfo> = scrapers.values.map { SourceInfo(it.sourceId, it.sourceName) }

    private class CacheEntry(val at: Long, val value: Any)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, ttlMs: Long, force: Boolean = false, loader: suspend () -> T): T {
        val hit = cache[key]
        if (!force && hit != null && System.currentTimeMillis() - hit.at < ttlMs) return hit.value as T
        val value = loader()
        cache[key] = CacheEntry(System.currentTimeMillis(), value)
        return value
    }

    private fun scraper(sourceId: String): BaseScraper =
        scrapers[sourceId] ?: throw IOException("Unknown source: $sourceId")

    /**
     * Runs [block] against one source, or every source in parallel when [sourceId] is null. A failing
     * source is tolerated as long as another one answers; if all fail, the errors are reported together.
     */
    private suspend fun gather(sourceId: String?, block: suspend (BaseScraper) -> List<Manga>): List<Manga> = coroutineScope {
        val targets = if (sourceId == null) scrapers.values.toList() else listOf(scraper(sourceId))
        val outcomes = targets.map { s ->
            async {
                try {
                    s to Result.success(block(s))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    s to Result.failure<List<Manga>>(e)
                }
            }
        }.awaitAll()

        val ok = outcomes.mapNotNull { it.second.getOrNull() }
        if (ok.isEmpty()) {
            val message = outcomes.joinToString("\n") { (s, r) -> "${s.sourceName}: ${r.exceptionOrNull()?.message}" }
            throw IOException(message)
        }
        interleave(ok)
    }

    private fun interleave(lists: List<List<Manga>>): List<Manga> {
        if (lists.size == 1) return lists[0]
        val out = ArrayList<Manga>()
        val seenTitles = HashSet<String>()
        var i = 0
        while (lists.any { i < it.size }) {
            for (list in lists) {
                val m = list.getOrNull(i) ?: continue
                if (seenTitles.add(m.title.lowercase().filter { it.isLetterOrDigit() })) out.add(m)
            }
            i++
        }
        return out
    }

    suspend fun search(filters: com.mangapill.app.data.model.SearchFilters, sourceId: String? = null): List<Manga> {
        val q = filters.query.trim()
        val cacheKey = "search:$sourceId:${q.lowercase()}:${filters.type}:${filters.status}:${filters.genre}"
        val results = cached(cacheKey, 5 * 60_000L) {
            gather(sourceId) { it.search(filters.copy(query = q)) }
        }
        persist(results)
        return results
    }

    suspend fun trending(sourceId: String? = null, force: Boolean = false): List<Manga> {
        val results = cached("trending:$sourceId", 10 * 60_000L, force) { gather(sourceId) { it.getPopular() } }
        persist(results)
        return results
    }

    suspend fun latest(sourceId: String? = null, force: Boolean = false): List<Manga> {
        val results = cached("latest:$sourceId", 10 * 60_000L, force) { gather(sourceId) { it.getLatestUpdates() } }
        persist(results)
        return results
    }

    suspend fun getMangaDetails(sourceId: String, mangaId: String): Manga {
        return try {
            val details = cached("details:$sourceId:$mangaId", 30 * 60_000L) { scraper(sourceId).getMangaDetails(mangaId) }
            db.mangaDao().insertManga(details.toEntity())
            details
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline / blocked: fall back to whatever we cached last time.
            db.mangaDao().getMangaById(Manga(mangaId, "", "", "", sourceId = sourceId).key)?.toManga() ?: throw e
        }
    }

    suspend fun getChapterList(sourceId: String, mangaId: String, force: Boolean = false): List<Chapter> =
        cached("chapters:$sourceId:$mangaId", 10 * 60_000L, force) { scraper(sourceId).getChapterList(mangaId) }

    suspend fun getChapterPages(sourceId: String, chapterId: String): List<PageImage> =
        cached("pages:$sourceId:$chapterId", 20 * 60_000L) {
            val pages = scraper(sourceId).getChapterPages(chapterId)
            if (pages.isEmpty()) throw IOException("This chapter has no readable pages")
            pages
        }

    private suspend fun persist(mangas: List<Manga>) {
        runCatching { db.mangaDao().insertMangas(mangas.map { it.toEntity() }) }
    }

    // --- Reading history ------------------------------------------------------------------------

    fun getReadingHistory(sourceId: String, mangaId: String): Flow<ReadingHistory?> =
        db.historyDao().getHistoryForManga(key(sourceId, mangaId)).map { it?.toModel(mangaId) }

    suspend fun getReadingHistoryOnce(sourceId: String, mangaId: String): ReadingHistory? =
        db.historyDao().getHistoryOnce(key(sourceId, mangaId))?.toModel(mangaId)

    suspend fun saveReadingHistory(sourceId: String, history: ReadingHistory) {
        db.historyDao().upsertHistory(
            ReadingHistoryEntity(
                mangaId = key(sourceId, history.mangaId),
                chapterId = history.chapterId,
                chapterNumber = history.chapterNumber,
                lastReadPage = history.lastReadPage,
                scrollPercentage = history.scrollPercentage,
                lastReadAt = history.lastReadAt
            )
        )
    }

    fun historyEntries(): Flow<List<HistoryEntry>> = db.historyDao().getHistoryRows().map { rows ->
        rows.map {
            HistoryEntry(it.manga.toManga(), it.chapterId, it.chapterNumber, it.lastReadPage, it.scrollPercentage, it.lastReadAt)
        }
    }

    suspend fun clearHistory() = db.historyDao().clearHistory()

    // --- Bookmarks ------------------------------------------------------------------------------

    fun isBookmarked(sourceId: String, mangaId: String): Flow<Boolean> =
        db.bookmarkDao().getBookmark(key(sourceId, mangaId)).map { it != null }

    suspend fun toggleBookmark(manga: Manga) {
        val dao = db.bookmarkDao()
        val k = manga.key
        if (dao.getBookmarkOnce(k) != null) {
            dao.deleteBookmark(k)
        } else {
            db.mangaDao().insertManga(manga.toEntity())
            dao.upsertBookmark(BookmarkEntity(k, BookmarkStatus.READING.name, System.currentTimeMillis()))
        }
    }

    fun bookmarkedManga(): Flow<List<Manga>> = db.bookmarkDao().getBookmarkedMangas().map { list -> list.map { it.toManga() } }

    // --- Mapping --------------------------------------------------------------------------------

    private fun key(sourceId: String, mangaId: String) = "$sourceId:$mangaId"

    private fun ReadingHistoryEntity.toModel(rawMangaId: String) = ReadingHistory(
        mangaId = rawMangaId,
        chapterId = chapterId,
        chapterNumber = chapterNumber,
        lastReadPage = lastReadPage,
        scrollPercentage = scrollPercentage,
        lastReadAt = lastReadAt
    )

    private fun Manga.toEntity() = MangaEntity(
        id = key,
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

    private fun MangaEntity.toManga() = Manga(
        id = id.removePrefix("$sourceId:"),
        title = title,
        slug = slug,
        coverImage = coverImage,
        synopsis = synopsis,
        status = runCatching { MangaStatus.valueOf(status) }.getOrDefault(MangaStatus.UNKNOWN),
        rating = rating,
        genres = if (genresCsv.isBlank()) emptyList() else genresCsv.split(","),
        sourceId = sourceId,
        latestChapterNumber = latestChapterNumber
    )
}
