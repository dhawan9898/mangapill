package com.mangapill.app.data.repository

import com.mangapill.app.data.local.AppDatabase
import com.mangapill.app.data.local.BookmarkEntity
import com.mangapill.app.data.local.MangaEntity
import com.mangapill.app.data.local.ReadingHistoryEntity
import com.mangapill.app.data.model.BookmarkStatus
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Genres
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.ReadingHistory
import com.mangapill.app.data.model.SearchFilters
import com.mangapill.app.data.model.SourceInfo
import com.mangapill.app.data.prefs.AppPrefs
import com.mangapill.app.data.scraper.BaseScraper
import com.mangapill.app.data.scraper.SourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
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

/** Results merged from several sources, emitted every time one more source answers. */
data class SourceResults(
    val items: List<Manga>,
    /** Sources still loading. */
    val pending: Int,
    /** "Source: reason" for every source that failed or timed out. */
    val failures: List<String>
) {
    val done: Boolean get() = pending == 0
}

data class Recommendation(val seed: Manga, val genre: String, val items: List<Manga>)

class MangaRepository(private val db: AppDatabase, val prefs: AppPrefs) {

    val sources: List<SourceInfo> =
        SourceRegistry.all.map { SourceInfo(it.sourceId, it.sourceName, it.isExperimental) }

    private class CacheEntry(val at: Long, val value: Any)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /** sourceId -> time until which the source is skipped in "all sources" mode after a failure. */
    private val cooldown = ConcurrentHashMap<String, Long>()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cacheGet(key: String, ttlMs: Long): T? {
        val hit = cache[key] ?: return null
        return if (System.currentTimeMillis() - hit.at < ttlMs) hit.value as T else null
    }

    private fun cachePut(key: String, value: Any) {
        cache[key] = CacheEntry(System.currentTimeMillis(), value)
    }

    private suspend fun <T : Any> cached(key: String, ttlMs: Long, force: Boolean = false, loader: suspend () -> T): T {
        if (!force) cacheGet<T>(key, ttlMs)?.let { return it }
        return loader().also { cachePut(key, it) }
    }

    private fun scraper(sourceId: String): BaseScraper =
        SourceRegistry[sourceId] ?: throw IOException("Unknown source: $sourceId")

    private fun enabledScrapers(): List<BaseScraper> {
        val enabled = prefs.enabledSources.value
        return SourceRegistry.all.filter { it.sourceId in enabled }
    }

    /** Sources that will be queried for [filters]: one explicit source, or every enabled one that supports them. */
    fun sourcesFor(filters: SearchFilters, sourceId: String?): List<BaseScraper> {
        val base = if (sourceId != null) listOfNotNull(SourceRegistry[sourceId]) else enabledScrapers()
        return base.filter { it.supports(filters) }
    }

    // --- Multi-source aggregation ---------------------------------------------------------------

    /**
     * Queries [targets] concurrently. Each source gets its own time budget, and a partial result is
     * emitted as soon as any source answers, so one slow or dead site never blocks the screen.
     */
    private fun gather(
        cacheKey: String,
        ttlMs: Long,
        force: Boolean,
        targets: List<BaseScraper>,
        singleSource: Boolean,
        block: suspend (BaseScraper) -> List<Manga>
    ): Flow<SourceResults> = channelFlow {
        if (!force) {
            cacheGet<List<Manga>>(cacheKey, ttlMs)?.let {
                send(SourceResults(it, 0, emptyList()))
                return@channelFlow
            }
        }
        if (targets.isEmpty()) {
            send(SourceResults(emptyList(), 0, listOf("No enabled source supports this combination of filters")))
            return@channelFlow
        }

        val now = System.currentTimeMillis()
        val healthy = if (singleSource) targets else targets.filter { (cooldown[it.sourceId] ?: 0L) < now }.ifEmpty { targets }
        val budget = if (singleSource) SINGLE_SOURCE_TIMEOUT_MS else MULTI_SOURCE_TIMEOUT_MS

        val lock = Mutex()
        val answered = LinkedHashMap<String, List<Manga>>()
        val failures = ArrayList<String>()
        var pending = healthy.size
        send(SourceResults(emptyList(), pending, emptyList()))

        for (source in healthy) {
            launch {
                val outcome: Result<List<Manga>> = try {
                    Result.success(withTimeout(budget) { block(source) })
                } catch (e: TimeoutCancellationException) {
                    Result.failure(IOException("timed out"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }

                lock.withLock {
                    pending--
                    outcome.onSuccess {
                        answered[source.sourceId] = it
                        cooldown.remove(source.sourceId)
                    }.onFailure {
                        failures += "${source.sourceName}: ${it.message ?: it.javaClass.simpleName}"
                        if (!singleSource) cooldown[source.sourceId] = System.currentTimeMillis() + COOLDOWN_MS
                    }
                    val merged = interleave(healthy.mapNotNull { answered[it.sourceId] })
                    send(SourceResults(merged, pending, failures.toList()))
                    if (pending == 0 && answered.isNotEmpty()) {
                        cachePut(cacheKey, merged)
                        persist(merged)
                    }
                }
            }
        }
    }

    /** Round-robin merge so every source is represented near the top; drops cross-source duplicates. */
    private fun interleave(lists: List<List<Manga>>): List<Manga> {
        if (lists.size == 1) return lists[0]
        val out = ArrayList<Manga>()
        val seen = HashSet<String>()
        var i = 0
        while (lists.any { i < it.size }) {
            for (list in lists) {
                val m = list.getOrNull(i) ?: continue
                if (seen.add(normalize(m.title))) out.add(m)
            }
            i++
        }
        return out
    }

    private fun normalize(title: String) = title.lowercase().filter { it.isLetterOrDigit() }

    private fun modeKey(sourceId: String?) = sourceId ?: ("all[" + prefs.enabledSources.value.sorted().joinToString(",") + "]")

    fun searchStream(filters: SearchFilters, sourceId: String? = null): Flow<SourceResults> {
        val f = filters.copy(query = filters.query.trim())
        val key = "search:${modeKey(sourceId)}:${f.query.lowercase()}:${f.type}:${f.status}:${f.genre}"
        return gather(key, 5 * 60_000L, false, sourcesFor(f, sourceId), sourceId != null) { it.search(f) }
    }

    fun trendingStream(sourceId: String? = null, force: Boolean = false): Flow<SourceResults> =
        gather("trending:${modeKey(sourceId)}", 10 * 60_000L, force, sourcesFor(SearchFilters(), sourceId), sourceId != null) {
            it.getPopular()
        }

    fun latestStream(sourceId: String? = null, force: Boolean = false): Flow<SourceResults> =
        gather("latest:${modeKey(sourceId)}", 10 * 60_000L, force, sourcesFor(SearchFilters(), sourceId), sourceId != null) {
            it.getLatestUpdates()
        }

    suspend fun hiddenGems(sourceId: String? = null): List<Manga> {
        val targets = sourcesFor(SearchFilters(), sourceId).filter { it.supportsHiddenGems }
        if (targets.isEmpty()) return emptyList()
        return gather("gems:${modeKey(sourceId)}", 30 * 60_000L, false, targets, sourceId != null) { it.getHiddenGems() }
            .last().items
    }

    /**
     * "Because you read X": weights genres by how recently they were read, then finds unread titles
     * in the strongest genre of the most recent title that has genre data.
     */
    suspend fun becauseYouRead(sourceId: String? = null): Recommendation? {
        val rows = db.historyDao().getHistoryRows().first()
        if (rows.isEmpty()) return null

        val affinity = HashMap<String, Double>()
        rows.take(20).forEachIndexed { i, row ->
            genresOf(row.manga).forEach { g -> affinity[g] = (affinity[g] ?: 0.0) + 1.0 / (1 + i) }
        }
        val readKeys = rows.map { it.manga.id }.toSet()
        val readTitles = rows.map { normalize(it.manga.title) }.toSet()

        for (row in rows.take(5)) {
            val genre = genresOf(row.manga).maxByOrNull { affinity[it] ?: 0.0 } ?: continue
            val items = searchStream(SearchFilters(genre = genre), sourceId).last().items
                .filter { it.key !in readKeys && normalize(it.title) !in readTitles }
            if (items.isNotEmpty()) {
                return Recommendation(row.manga.toManga(), Genres.bySlug(genre)?.name ?: genre, items.take(15))
            }
        }
        return null
    }

    private fun genresOf(entity: MangaEntity): List<String> =
        entity.genresCsv.split(',').mapNotNull { Genres.match(it)?.slug }.distinct()

    // --- Single-title operations ----------------------------------------------------------------

    suspend fun getMangaDetails(sourceId: String, mangaId: String): Manga {
        return try {
            val details = cached("details:$sourceId:$mangaId", 30 * 60_000L) { scraper(sourceId).getMangaDetails(mangaId) }
            // Keep the cover we already know if the details page did not expose one.
            val known = db.mangaDao().getMangaById("$sourceId:$mangaId")
            val merged = if (details.coverImage.isBlank() && known != null) details.copy(coverImage = known.coverImage) else details
            db.mangaDao().insertManga(merged.toEntity())
            merged
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline / blocked: fall back to whatever we cached last time.
            db.mangaDao().getMangaById("$sourceId:$mangaId")?.toManga() ?: throw e
        }
    }

    suspend fun getChapterList(sourceId: String, mangaId: String, force: Boolean = false): List<Chapter> =
        cached("chapters:$sourceId:$mangaId", 10 * 60_000L, force) { scraper(sourceId).getChapterList(mangaId) }

    suspend fun getChapterPages(sourceId: String, chapterId: String): List<PageImage> =
        cached("pages:$sourceId:$chapterId", 30 * 60_000L) {
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

    private companion object {
        const val MULTI_SOURCE_TIMEOUT_MS = 10_000L
        const val SINGLE_SOURCE_TIMEOUT_MS = 25_000L
        const val COOLDOWN_MS = 3 * 60_000L
    }
}
