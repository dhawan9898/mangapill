package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.Genres
import com.mangapill.app.data.model.SearchFilters
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Scraper for mangapill.com.
 *
 * The parsing is intentionally structure-agnostic (it keys off URL shapes such as `/manga/<id>/<slug>`
 * and `/chapters/<id>/<slug>` rather than brittle CSS class chains) so that small layout tweaks on the
 * site do not break the app.
 */
class MangapillScraper : BaseScraper() {
    override val sourceId = "mangapill"
    override val sourceName = "Mangapill"
    override val baseUrl = "https://mangapill.com"

    override val imageReferer = "https://mangapill.com/"

    override fun supports(filters: SearchFilters): Boolean =
        (filters.type == null || filters.type in TYPES) &&
            (filters.status == null || filters.status in STATUSES) &&
            (filters.genre == null || Genres.bySlug(filters.genre) != null)

    override suspend fun search(filters: SearchFilters): List<Manga> {
        val url = StringBuilder("/search?q=").append(enc(filters.query.trim()))
            .append("&type=").append(filters.type.orEmpty())
            .append("&status=").append(enc(STATUSES[filters.status].orEmpty()))
        Genres.bySlug(filters.genre)?.let { url.append("&genre=").append(enc(it.name)) }
        return parseMangaCards(fetchDocument(url.toString()))
    }

    override suspend fun getPopular(): List<Manga> {
        val cards = parseMangaCards(fetchDocument("/"))
        return cards.filter { it.coverImage.isNotBlank() }.take(20)
    }

    override suspend fun getLatestUpdates(): List<Manga> {
        val cards = runCatching { parseMangaCards(fetchDocument("/chapters")) }.getOrDefault(emptyList())
        return cards.ifEmpty { parseMangaCards(fetchDocument("/")) }.filter { it.coverImage.isNotBlank() }
    }

    override suspend fun getMangaDetails(sourceMangaId: String): Manga = parseDetails(sourceMangaId, fetchDocument("/manga/$sourceMangaId"))

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> =
        parseChapters(sourceMangaId, fetchDocument("/manga/$sourceMangaId"))

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> =
        parsePages(fetchDocument("/chapters/$sourceChapterId"))

    // ---------------------------------------------------------------------------------------------
    // Parsing (internal so it can be exercised by fixtures)
    // ---------------------------------------------------------------------------------------------

    private class Card(var title: String = "", var titleScore: Int = 0, var cover: String = "")

    internal fun parseMangaCards(doc: Document): List<Manga> {
        val cards = LinkedHashMap<String, Card>()

        for (a in doc.select("a[href*=/manga/]")) {
            val id = mangaIdFromHref(a.attr("href")) ?: continue
            val card = cards.getOrPut(id) { Card() }

            if (card.cover.isEmpty()) {
                val img = a.selectFirst("img") ?: soleMangaImage(a, id)
                if (img != null) card.cover = img.imageSrc()
            }

            val (title, score) = titleCandidate(a)
            if (title.isNotBlank() && score > card.titleScore) {
                card.title = title
                card.titleScore = score
            }
        }

        return cards.map { (id, card) ->
            Manga(
                id = id,
                title = card.title.ifBlank { prettifySlug(id) },
                slug = id.substringAfterLast('/'),
                coverImage = card.cover,
                sourceId = sourceId
            )
        }
    }

    /** If the anchor has no image, use the sibling image but only when its container holds a single manga. */
    private fun soleMangaImage(a: Element, id: String): Element? {
        val parent = a.parent() ?: return null
        val ids = parent.select("a[href*=/manga/]").mapNotNull { mangaIdFromHref(it.attr("href")) }.distinct()
        return if (ids.size == 1 && ids[0] == id) parent.selectFirst("img") else null
    }

    private fun titleCandidate(a: Element): Pair<String, Int> {
        a.selectFirst(".font-black, .font-bold")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let { return it to 3 }
        a.selectFirst("img[alt]")?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }?.let { return it to 2 }
        val text = a.text().trim()
        if (text.isNotBlank() && !text.startsWith("Chapter", ignoreCase = true)) return text to 1
        return "" to 0
    }

    internal fun parseDetails(id: String, doc: Document): Manga {
        val title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
            .ifBlank { doc.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" Manga")?.trim().orEmpty() }
            .ifBlank { prettifySlug(id) }

        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            .ifBlank {
                doc.select("img").map { it.imageSrc() }
                    .firstOrNull { it.isNotBlank() && !it.endsWith(".svg") && !it.contains("logo", true) }
                    .orEmpty()
            }

        val synopsis = doc.select("p").map { it.text().trim() }
            .firstOrNull { it.length > 40 }
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim().orEmpty()

        val genres = doc.select("a[href*=genre=]").map { it.text().trim() }.filter { it.isNotBlank() }.distinct()

        return Manga(
            id = id,
            title = title,
            slug = id.substringAfterLast('/'),
            coverImage = cover,
            synopsis = synopsis,
            status = parseStatus(doc),
            genres = genres,
            sourceId = sourceId
        )
    }

    private fun parseStatus(doc: Document): MangaStatus {
        val labelled = doc.select("label").firstOrNull { it.text().contains("Status", ignoreCase = true) }
            ?.parent()?.text()
        val text = (labelled ?: Regex("Status\\s*[:\\-]?\\s*([A-Za-z ]{3,20})", RegexOption.IGNORE_CASE)
            .find(doc.body().text())?.value.orEmpty()).lowercase()
        return when {
            "publishing" in text || "ongoing" in text -> MangaStatus.ONGOING
            "finished" in text || "completed" in text -> MangaStatus.COMPLETED
            "hiatus" in text -> MangaStatus.HIATUS
            else -> MangaStatus.UNKNOWN
        }
    }

    internal fun parseChapters(mangaId: String, doc: Document): List<Chapter> {
        val seen = HashSet<String>()
        val chapters = ArrayList<Chapter>()
        for (a in doc.select("a[href*=/chapters/]")) {
            val id = chapterIdFromHref(a.attr("href")) ?: continue
            if (!seen.add(id)) continue
            val text = a.text().trim()
            val number = CHAPTER_IN_TEXT.find(text)?.groupValues?.get(1)?.toFloatOrNull()
                ?: CHAPTER_IN_SLUG.find(id)?.groupValues?.get(1)?.toFloatOrNull()
                ?: 0f
            chapters.add(Chapter(id = id, mangaId = mangaId, chapterNumber = number, title = text.ifBlank { null }))
        }
        return chapters.sortedByDescending { it.chapterNumber }
    }

    internal fun parsePages(doc: Document): List<PageImage> {
        var images = doc.select("chapter-page img, img.js-page")
        if (images.isEmpty()) images = doc.select("picture img")
        var urls = images.map { it.imageSrc() }.filter { it.isNotBlank() }
        if (urls.isEmpty()) {
            urls = doc.select("img").map { it.imageSrc() }
                .filter { it.isNotBlank() && !it.endsWith(".svg") && !it.contains("logo", true) }
        }
        return urls.distinct().mapIndexed { i, url -> PageImage(pageNumber = i + 1, imageUrl = url, referer = "$baseUrl/") }
    }

    // ---------------------------------------------------------------------------------------------

    private fun pathOf(href: String): String =
        href.substringBefore('#').substringBefore('?').removePrefix(baseUrl).removePrefix("http://mangapill.com")

    private fun mangaIdFromHref(href: String): String? {
        val path = pathOf(href)
        if (!path.startsWith("/manga/")) return null
        return path.removePrefix("/manga/").trim('/').ifBlank { null }
    }

    private fun chapterIdFromHref(href: String): String? {
        val path = pathOf(href)
        if (!path.startsWith("/chapters/")) return null
        return path.removePrefix("/chapters/").trim('/').ifBlank { null }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        val TYPES = setOf("manga", "manhwa", "manhua")
        val STATUSES = mapOf("ongoing" to "publishing", "completed" to "finished", "hiatus" to "on hiatus")
        val CHAPTER_IN_TEXT = Regex("Chapter\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val CHAPTER_IN_SLUG = Regex("chapter-(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    }
}
