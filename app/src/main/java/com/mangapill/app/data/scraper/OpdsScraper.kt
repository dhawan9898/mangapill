package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.SearchFilters
import com.mangapill.app.data.opds.OpdsEntry
import com.mangapill.app.data.opds.OpdsFeed
import com.mangapill.app.data.opds.OpdsLink
import com.mangapill.app.data.opds.OpdsParser
import com.mangapill.app.data.opds.OpdsServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder

/**
 * Provider for any OPDS catalog (OPDS 1.2 Atom or OPDS 2.0 JSON). Unlike the site scrapers it relies
 * only on an open standard, so it does not break when a website changes its HTML, and it works with
 * the comic servers people host themselves (Komga, Kavita, Suwayomi, Stump, Codex...).
 *
 * Mapping to the app's model:
 * * a feed of books (a series, a volume list) is a [Manga]; its readable entries are its [Chapter]s;
 * * a book is readable when it offers the Page Streaming Extension (one URL per page) or a
 *   Readium/DiViNa manifest; archive-only downloads (CBZ/PDF) are not streamable and are skipped.
 *
 * Ids: manga `s:<feed url>` (series) or `b:<chapter id>` (a lone book listed outside its series);
 * chapters `p:<page count>:<page URL template>` or `m:<manifest url>`.
 */
class OpdsScraper(val server: OpdsServer) : BaseScraper() {
    override val sourceId = server.sourceId
    override val sourceName = server.name
    override val baseUrl: String = origin(server.url)

    // Self-hosted servers need no Referer and are not rate limited like public sites.
    override val imageReferer: String? = null
    override val minDelayBetweenRequestsMs = 50L

    private val rootLock = Mutex()
    private var rootFeed: OpdsFeed? = null
    private var rootFetchedAt = 0L
    private var searchTemplate: String? = null

    override fun supports(filters: SearchFilters): Boolean = !filters.hasFilters

    // ---- Lists ----------------------------------------------------------------------------------

    override suspend fun search(filters: SearchFilters): List<Manga> {
        val q = filters.query.trim()
        if (q.isEmpty()) return getPopular()
        val template = searchTemplate() ?: throw ScraperException("$sourceName does not offer search")
        return list(expandSearch(template, q))
    }

    override suspend fun getPopular(): List<Manga> {
        val root = root()
        val link = root.link(OpdsLink.REL_SORT_POPULAR)?.href ?: pickNavigation(root, POPULAR_HINTS, exclude = LATEST_HINTS)
        return link?.let { list(it) } ?: rootTitles(root)
    }

    override suspend fun getLatestUpdates(): List<Manga> {
        val root = root()
        val link = root.link(OpdsLink.REL_SORT_NEW)?.href ?: pickNavigation(root, LATEST_HINTS, exclude = listOf("book"))
            ?: pickNavigation(root, LATEST_HINTS, exclude = emptyList())
        return link?.let { list(it) } ?: rootTitles(root)
    }

    /** The root feed's own entries, minus the category shortcuts ("Latest", "Keep reading"...). */
    private fun rootTitles(root: OpdsFeed): List<Manga> = root.entries
        .filterNot { e -> e.feedLink != null && CATEGORY_HINTS.any { e.title.contains(it, ignoreCase = true) } }
        .mapNotNull { it.toManga() }

    private suspend fun list(url: String): List<Manga> = toMangas(feed(url))

    private fun toMangas(feed: OpdsFeed): List<Manga> = feed.entries.mapNotNull { it.toManga() }

    private fun OpdsEntry.toManga(): Manga? {
        val id = chapterId(this)?.let { "b:$it" } ?: feedLink?.let { "s:${it.href}" } ?: return null
        return Manga(
            id = id,
            title = title,
            slug = id,
            coverImage = cover,
            synopsis = summary,
            status = MangaStatus.UNKNOWN,
            genres = categories,
            sourceId = sourceId
        )
    }

    // ---- Title ----------------------------------------------------------------------------------

    override suspend fun getMangaDetails(sourceMangaId: String): Manga {
        // A lone book has no feed of its own; the repository falls back to the row saved from the list.
        if (!sourceMangaId.startsWith("s:")) throw ScraperException("No details available for this book")
        val feed = feed(sourceMangaId.removePrefix("s:"))
        val readable = feed.entries.filter { it.isReadable }
        return Manga(
            id = sourceMangaId,
            title = feed.title.ifBlank { "Untitled" },
            slug = sourceMangaId,
            coverImage = (readable.firstOrNull() ?: feed.entries.firstOrNull())?.cover.orEmpty(),
            synopsis = feed.subtitle.ifBlank { feed.entries.firstOrNull()?.summary.orEmpty() },
            status = MangaStatus.UNKNOWN,
            genres = feed.entries.take(5).flatMap { it.categories }.distinct(),
            sourceId = sourceId,
            latestChapterNumber = readable.size.toFloat()
        )
    }

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> {
        if (sourceMangaId.startsWith("b:")) {
            return listOf(Chapter(id = sourceMangaId.removePrefix("b:"), mangaId = sourceMangaId, chapterNumber = 1f))
        }
        val url = sourceMangaId.removePrefix("s:")
        var (entries, downloadsOnly) = readableEntries(url)

        // Some servers nest one more level (series → volumes → books). Descend once.
        if (entries.isEmpty()) {
            val children = feed(url).entries.mapNotNull { it.feedLink?.href }.distinct().take(MAX_CHILD_FEEDS)
            val nested = children.map { readableEntries(it) }
            entries = nested.flatMap { it.entries }
            downloadsOnly = downloadsOnly || nested.any { it.hasDownloads }
        }
        if (entries.isEmpty()) {
            throw ScraperException(
                if (downloadsOnly) "$sourceName only offers downloads for this title; enable OPDS page streaming on the server"
                else "No readable books in this feed"
            )
        }

        // Feeds list books in reading order; the app shows newest first.
        return entries.distinctBy { chapterId(it) }.mapIndexed { i, e ->
            Chapter(id = chapterId(e)!!, mangaId = sourceMangaId, chapterNumber = (i + 1).toFloat(), title = e.title)
        }.reversed()
    }

    private data class Readable(val entries: List<OpdsEntry>, val hasDownloads: Boolean)

    /**
     * Readable entries of [url], following "next" links through every page of the feed, and whether
     * the feed had books that can only be downloaded as an archive.
     */
    private suspend fun readableEntries(url: String): Readable {
        val out = ArrayList<OpdsEntry>()
        val visited = HashSet<String>()
        var next: String? = url
        var downloads = false
        while (next != null && visited.add(next) && visited.size <= MAX_PAGES) {
            val page = feed(next)
            out += page.entries.filter { it.isReadable }
            downloads = downloads || page.entries.any { e -> !e.isReadable && e.links.any { it.isAcquisition } }
            next = page.next?.href
        }
        return Readable(out, downloads)
    }

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> = when {
        sourceChapterId.startsWith("p:") -> {
            val count = sourceChapterId.removePrefix("p:").substringBefore(':').toInt()
            val template = sourceChapterId.removePrefix("p:").substringAfter(':')
            (0 until count).map { PageImage(pageNumber = it + 1, imageUrl = OpdsParser.pageUrl(template, it), referer = null) }
        }
        sourceChapterId.startsWith("m:") -> {
            val url = sourceChapterId.removePrefix("m:")
            val body = fetchText(url, mapOf("Accept" to "application/divina+json, application/webpub+json, application/json"))
            withContext(Dispatchers.Default) { OpdsParser.parseManifestPages(body, url) }
                .mapIndexed { i, img -> PageImage(pageNumber = i + 1, imageUrl = img, referer = null) }
        }
        else -> throw ScraperException("Unknown chapter id")
    }

    private fun chapterId(entry: OpdsEntry): String? {
        entry.pageStream?.let { return "p:${it.pageCount}:${it.href}" }
        entry.manifest?.let { return "m:${it.href}" }
        return null
    }

    // ---- Catalog plumbing -----------------------------------------------------------------------

    /** Fetches the root feed; used by Settings to validate a server before saving it. */
    suspend fun testConnection(): OpdsFeed = root(force = true)

    private suspend fun feed(url: String): OpdsFeed {
        val body = fetchText(url, mapOf("Accept" to ACCEPT_FEED))
        return withContext(Dispatchers.Default) {
            runCatching { OpdsParser.parseFeed(body, url) }
                .getOrElse { throw ScraperException("$sourceName did not return an OPDS feed", it) }
        }
    }

    private suspend fun root(force: Boolean = false): OpdsFeed = rootLock.withLock {
        val cached = rootFeed
        if (!force && cached != null && System.currentTimeMillis() - rootFetchedAt < ROOT_TTL_MS) return cached
        feed(server.url).also {
            rootFeed = it
            rootFetchedAt = System.currentTimeMillis()
            searchTemplate = null
        }
    }

    private suspend fun searchTemplate(): String? {
        searchTemplate?.let { return it }
        val link = root().link("search") ?: return null
        val template = if (link.type.startsWith(OpdsLink.TYPE_OPENSEARCH)) {
            val body = fetchText(link.href, mapOf("Accept" to "application/opensearchdescription+xml, */*"))
            withContext(Dispatchers.Default) { OpdsParser.parseOpenSearch(body, link.href) }
        } else {
            link.href
        }
        return template?.also { searchTemplate = it }
    }

    private fun pickNavigation(root: OpdsFeed, hints: List<String>, exclude: List<String>): String? {
        val nav = root.entries.filter { it.feedLink != null && !it.isReadable }
        for (hint in hints) {
            nav.firstOrNull { e ->
                e.title.contains(hint, ignoreCase = true) && exclude.none { e.title.contains(it, ignoreCase = true) }
            }?.let { return it.feedLink!!.href }
        }
        return null
    }

    companion object {
        private const val ACCEPT_FEED =
            "application/atom+xml;profile=opds-catalog, application/opds+json, application/atom+xml;q=0.9, application/json;q=0.8, */*;q=0.5"
        private const val ROOT_TTL_MS = 5 * 60_000L
        private const val MAX_PAGES = 50
        private const val MAX_CHILD_FEEDS = 40

        // Komga, Kavita, Stump and Codex name their root entries this way.
        private val POPULAR_HINTS = listOf("all series", "popular", "series", "browse", "all", "librar")
        private val LATEST_HINTS = listOf("latest series", "recently updated", "recently added", "latest", "recent", "new")
        private val CATEGORY_HINTS = LATEST_HINTS + listOf("keep reading", "on deck", "continue", "want to read", "reading list")

        /**
         * Fills a search template: OpenSearch `{searchTerms}` (optional `{x?}` parameters dropped) or an
         * RFC 6570 form query such as `{?query}` used by OPDS 2.0.
         */
        fun expandSearch(template: String, query: String): String {
            val enc = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
            if (template.contains("{searchTerms}")) {
                return template.replace("{searchTerms}", enc).replace(Regex("\\{[^}]*\\?\\}"), "")
            }
            val form = Regex("\\{([?&])([^}]+)\\}").find(template)
            if (form != null) {
                val name = form.groupValues[2].split(',').first().trim()
                return template.replaceRange(form.range, "${form.groupValues[1]}$name=$enc")
            }
            // Not a template: append a conventional parameter.
            return template + (if (template.contains('?')) "&" else "?") + "query=$enc"
        }

        fun origin(url: String): String = runCatching {
            val u = URI(url)
            val port = if (u.port == -1) "" else ":${u.port}"
            "${u.scheme}://${u.host}$port"
        }.getOrDefault(url.trimEnd('/'))
    }
}
