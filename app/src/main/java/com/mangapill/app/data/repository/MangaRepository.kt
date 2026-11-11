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
import com.mangapill.app.data.scraper.MangaDexScraper
import com.mangapill.app.data.scraper.ScraperException
import com.mangapill.app.data.scraper.SourceRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
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

    /** Built-in sources plus the user's OPDS servers; re-read after servers are added or removed. */
    val sources: List<SourceInfo>
        get() = SourceRegistry.all.map { SourceInfo(it.sourceId, it.sourceName, it.isExperimental) }

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
        // Bounded: a long session of browsing and searching must not grow memory without limit.
        if (cache.size > MAX_CACHE_ENTRIES) {
            cache.entries.sortedBy { it.value.at }.take(cache.size - MAX_CACHE_ENTRIES + CACHE_TRIM_BATCH)
                .forEach { cache.remove(it.key) }
        }
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
                        failures += "${source.sourceName}: ${com.mangapill.app.data.ErrorText.describe(it)}"
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

    private fun interleave(lists: List<List<Manga>>): List<Manga> = interleaveSources(lists)

    private fun normalize(title: String) = normalizeTitle(title)

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

    /**
     * A title's chapters. When its own source lists none (MangaDex has no chapters for licensed
     * titles), the list comes from the same title on another source; those chapters carry that
     * source in their id ([MIRROR_PREFIX]) and open there.
     */
    suspend fun getChapterList(sourceId: String, mangaId: String, force: Boolean = false): List<Chapter> =
        cached("chapters:$sourceId:$mangaId", 10 * 60_000L, force) {
            val own = try {
                Result.success(scraper(sourceId).getChapterList(mangaId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            val list = own.getOrNull().orEmpty()
            if (list.isNotEmpty()) return@cached list
            mirrorChapterList(sourceId, mangaId) ?: own.getOrThrow()
        }

    /** The chapters a source itself lists (no fallback), for looking a title up elsewhere. */
    private suspend fun ownChapterList(sourceId: String, mangaId: String): List<Chapter> =
        cached("own-chapters:$sourceId:$mangaId", 10 * 60_000L) { scraper(sourceId).getChapterList(mangaId) }

    private suspend fun mirrorChapterList(sourceId: String, mangaId: String): List<Chapter>? {
        val (src, id) = findMirror(sourceId, mangaId) { s, m -> ownChapterList(s, m).isNotEmpty() } ?: return null
        val chapters = runCatching { ownChapterList(src, id) }.getOrNull().orEmpty()
        if (chapters.isEmpty()) return null
        return chapters.map { it.copy(id = "$MIRROR_PREFIX$src|${it.id}", mangaId = mangaId) }
    }

    // --- Home rails (MangaDex) ---------------------------------------------------------------------

    /** One home-screen rail, straight from the MangaDex API (cached for 20 minutes). */
    suspend fun homeRail(rail: HomeRail, force: Boolean = false): List<Manga> =
        cached("home:${rail.name}", 20 * 60_000L, force) {
            val dex = SourceRegistry["mangadex"] as? MangaDexScraper ?: throw ScraperException("MangaDex is not available")
            dex.list(rail.query(), rail.limit).also { persist(it) }
        }

    suspend fun getChapterPages(sourceId: String, chapterId: String): List<PageImage> =
        cached("pages:$sourceId:$chapterId", 30 * 60_000L) {
            val pages = scraper(sourceId).getChapterPages(chapterId)
            if (pages.isEmpty()) throw ScraperException("${SourceRegistry.name(sourceId)} has no pages for this chapter")
            pages
        }

    /** Title (sourceId:mangaId) -> where its chapters were last found on another source. */
    private val mirrors = ConcurrentHashMap<String, Pair<String, String>>()

    /**
     * The pages of a chapter, from the title's own source when it serves them, otherwise from another
     * source that has the same title and chapter number. Catalogue-only sources (Comick) always go
     * to another source; a source that fails or has no pages for the chapter does too.
     */
    suspend fun loadChapter(sourceId: String, mangaId: String, chapterId: String, chapterNumber: Float): ChapterPages {
        // A chapter from a borrowed chapter list (see getChapterList): open it where it came from.
        if (chapterId.startsWith(MIRROR_PREFIX)) {
            val rest = chapterId.removePrefix(MIRROR_PREFIX)
            val src = rest.substringBefore('|')
            val realId = rest.substringAfter('|')
            return ChapterPages(getChapterPages(src, realId), servedBy = SourceRegistry.name(src))
        }
        val own = SourceRegistry[sourceId]
        var ownError: Exception? = null
        if (own == null || own.hostsPages) {
            try {
                return ChapterPages(getChapterPages(sourceId, chapterId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ownError = e
            }
        }
        mirrorPages(sourceId, mangaId, chapterNumber)?.let { return it }
        val name = SourceRegistry.name(sourceId)
        throw when {
            own != null && !own.hostsPages -> ScraperException(
                "$name doesn't host chapter pages, and none of the other sources has chapter " +
                    "${formatNumber(chapterNumber)} of this title yet."
            )
            else -> ownError ?: ScraperException("$name has no pages for this chapter")
        }
    }

    private suspend fun mirrorPages(sourceId: String, mangaId: String, number: Float): ChapterPages? {
        val titleKey = "$sourceId:$mangaId"
        // The source that worked for the previous chapter usually has this one too.
        mirrors[titleKey]?.let { (src, id) -> pagesOn(src, id, number)?.let { return it } }

        val (src, id) = findMirror(sourceId, mangaId) { s, m -> pagesOn(s, m, number) != null } ?: return null
        return pagesOn(src, id, number)
    }

    /**
     * The same title on another source that passes [accept] (has the chapter, has chapters...):
     * the one used last time if it still passes, otherwise the best match among the sources that
     * serve pages, searched in parallel by the title and its alternative names.
     */
    private suspend fun findMirror(
        sourceId: String,
        mangaId: String,
        accept: suspend (sourceId: String, mangaId: String) -> Boolean
    ): Pair<String, String>? {
        val titleKey = "$sourceId:$mangaId"
        mirrors[titleKey]?.let { known -> if (runCatching { accept(known.first, known.second) }.getOrDefault(false)) return known }

        val seed = runCatching { getMangaDetails(sourceId, mangaId) }.getOrNull() ?: return null
        val keys = ChapterMirror.titleKeys(seed)
        val queries = ChapterMirror.queries(seed)
        if (keys.isEmpty() || queries.isEmpty()) return null

        val matches = coroutineScope {
            mirrorCandidates(exclude = sourceId).map { source ->
                async {
                    withTimeoutOrNull(MIRROR_SEARCH_TIMEOUT_MS) {
                        for (q in queries) {
                            val hits = runCatching { source.search(SearchFilters(q)) }.getOrNull().orEmpty()
                            ChapterMirror.bestMatch(hits, keys)?.let { return@withTimeoutOrNull Triple(source, it.first, it.second) }
                        }
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        // Best match first; equal scores keep the candidate order (enabled sources first).
        for ((source, manga, _) in matches.sortedByDescending { it.third }) {
            if (!runCatching { accept(source.sourceId, manga.id) }.getOrDefault(false)) continue
            mirrors[titleKey] = source.sourceId to manga.id
            return source.sourceId to manga.id
        }
        return null
    }

    private suspend fun pagesOn(sourceId: String, mangaId: String, number: Float): ChapterPages? = try {
        val chapter = ChapterMirror.sameChapter(ownChapterList(sourceId, mangaId), number)
        chapter?.let { ChapterPages(getChapterPages(sourceId, it.id), servedBy = SourceRegistry.name(sourceId)) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Sources that serve pages: the enabled ones first, then the stable built-ins that are switched off. */
    private fun mirrorCandidates(exclude: String): List<BaseScraper> {
        val enabled = enabledScrapers()
        val stable = SourceRegistry.builtIn.filter { !it.isExperimental }
        return (enabled + stable).distinctBy { it.sourceId }
            .filter { it.sourceId != exclude && it.hostsPages }
            .filter { (cooldown[it.sourceId] ?: 0L) < System.currentTimeMillis() }
    }

    private fun formatNumber(n: Float): String = if (n % 1f == 0f) n.toInt().toString() else n.toString()

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

    /** Forgets one title's reading progress (the History list's remove button). */
    suspend fun removeFromHistory(manga: Manga) = db.historyDao().deleteHistory(manga.key)

    suspend fun clearLibrary() = db.bookmarkDao().clearBookmarks()

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
        const val MIRROR_SEARCH_TIMEOUT_MS = 12_000L
        /** Chapter ids borrowed from another source: "m|<sourceId>|<chapterId>". */
        const val MIRROR_PREFIX = "m|"
        const val MAX_CACHE_ENTRIES = 300
        const val CACHE_TRIM_BATCH = 30
    }
}
