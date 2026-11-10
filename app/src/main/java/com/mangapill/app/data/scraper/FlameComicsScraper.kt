package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.SearchFilters
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder

/**
 * Scraper for flamecomics.xyz (Next.js application).
 */
class FlameComicsScraper : BaseScraper() {
    override val sourceId = "flamecomics"
    override val sourceName = "Flame Comics"
    override val baseUrl = "https://flamecomics.xyz"
    override val isExperimental = false
    override val imageReferer = "https://flamecomics.xyz/"

    private val cdnHost = "https://cdn.flamecomics.xyz"

    override fun supports(filters: SearchFilters): Boolean = filters.type == null && filters.status == null && filters.genre == null

    override suspend fun search(filters: SearchFilters): List<Manga> {
        val q = filters.query.trim()
        val url = if (q.isNotEmpty()) "/browse?search=${enc(q)}" else "/browse"
        val cards = parseCards(fetchDocument(url))
        // The page lists every series and filters in the browser, so filter here: every word of the
        // query must appear in the title.
        return if (q.isEmpty()) cards else filterByQuery(cards, q)
    }

    internal fun filterByQuery(cards: List<Manga>, query: String): List<Manga> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        return cards.filter { m -> val t = m.title.lowercase(); words.all { it in t } }
    }

    override suspend fun getPopular(): List<Manga> = parseCards(fetchDocument("/browse"))

    override suspend fun getLatestUpdates(): List<Manga> = parseCards(fetchDocument("/browse"))

    override suspend fun getMangaDetails(sourceMangaId: String): Manga {
        val seriesId = normalizeSeriesId(sourceMangaId)
        val doc = fetchDocument("/series/$seriesId")

        var title = ""
        var cover = ""
        var synopsis = ""
        var status = MangaStatus.UNKNOWN
        val genres = ArrayList<String>()

        val nextData = getNextData(doc)
        val seriesJson = nextData?.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("series")

        if (seriesJson != null) {
            title = seriesJson.optString("title")
            val coverName = seriesJson.optString("cover")
            if (coverName.isNotBlank()) cover = "$cdnHost/uploads/images/series/$seriesId/$coverName"
            synopsis = seriesJson.optString("description").replace(Regex("<[^>]*>"), "").trim()
            val statusStr = seriesJson.optString("status").lowercase()
            status = when {
                "ongoing" in statusStr -> MangaStatus.ONGOING
                "completed" in statusStr -> MangaStatus.COMPLETED
                "hiatus" in statusStr -> MangaStatus.HIATUS
                else -> MangaStatus.UNKNOWN
            }
            val tags = seriesJson.optJSONArray("tags")
            if (tags != null) {
                for (i in 0 until tags.length()) genres.add(tags.getString(i))
            }
        } else {
            title = doc.selectFirst("title, meta[property=og:title]")?.let {
                if (it.tagName() == "meta") it.attr("content") else it.text().substringBefore(" -")
            }?.trim().orEmpty().ifBlank { prettifySlug(seriesId) }

            cover = doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            synopsis = doc.selectFirst("meta[name=description]")?.attr("content")?.trim().orEmpty()
        }

        return Manga(
            id = "series/$seriesId",
            title = title.ifBlank { prettifySlug(seriesId) },
            slug = seriesId,
            coverImage = cover,
            synopsis = synopsis,
            status = status,
            genres = genres,
            sourceId = sourceId
        )
    }

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> {
        val seriesId = normalizeSeriesId(sourceMangaId)
        val doc = fetchDocument("/series/$seriesId")
        val chapters = ArrayList<Chapter>()

        val nextData = getNextData(doc)
        val chaptersArray = nextData?.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONArray("chapters")

        if (chaptersArray != null && chaptersArray.length() > 0) {
            for (i in 0 until chaptersArray.length()) {
                val ch = chaptersArray.getJSONObject(i)
                val token = ch.optString("token")
                if (token.isBlank()) continue
                val numStr = ch.optString("chapter")
                val number = numStr.toFloatOrNull() ?: 0f
                val chTitle = ch.optString("title")

                chapters.add(
                    Chapter(
                        id = "series/$seriesId/$token",
                        mangaId = "series/$seriesId",
                        chapterNumber = number,
                        title = if (chTitle.isNotBlank()) "Chapter $numStr - $chTitle" else "Chapter $numStr"
                    )
                )
            }
        } else {
            val seen = HashSet<String>()
            for (a in doc.select("a[href*=/series/$seriesId/]")) {
                val href = a.attr("href")
                val path = pathOf(href) ?: continue
                if (!seen.add(path)) continue
                val text = a.text().trim()
                val number = CHAPTER_REGEX.find(path)?.groupValues?.get(1)?.toFloatOrNull()
                    ?: CHAPTER_REGEX.find(text)?.groupValues?.get(1)?.toFloatOrNull()
                    ?: 0f
                chapters.add(
                    Chapter(
                        id = path,
                        mangaId = "series/$seriesId",
                        chapterNumber = number,
                        title = text.ifBlank { "Chapter $number" }
                    )
                )
            }
        }

        return chapters.sortedByDescending { it.chapterNumber }
    }

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> {
        val path = if (sourceChapterId.startsWith("series/")) sourceChapterId else "series/$sourceChapterId"
        val doc = fetchDocument("/$path")

        val urls = ArrayList<String>()
        val nextData = getNextData(doc)
        val chapterObj = nextData?.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("chapter")

        if (chapterObj != null) {
            val imagesObj = chapterObj.optJSONObject("images")
            val seriesId = chapterObj.optInt("series_id", 0)
            val token = chapterObj.optString("token")

            if (imagesObj != null && seriesId > 0 && token.isNotBlank()) {
                val keys = imagesObj.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted().toList()
                for (key in keys) {
                    val imgData = imagesObj.optJSONObject(key.toString())
                    val imageName = imgData?.optString("name") ?: continue
                    urls.add("$cdnHost/uploads/images/series/$seriesId/$token/$imageName")
                }
            }
        }

        if (urls.isEmpty()) {
            val htmlImgs = doc.select("img[src*=/uploads/images/series/]")
            urls.addAll(htmlImgs.map { it.imageSrc() }.filter { it.isNotBlank() })
        }

        return urls.distinct().mapIndexed { i, url ->
            PageImage(pageNumber = i + 1, imageUrl = url, referer = imageReferer)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun parseCards(doc: Document): List<Manga> {
        val cards = ArrayList<Manga>()
        val seen = HashSet<String>()

        for (a in doc.select("a[href*=/series/]")) {
            val href = a.attr("href")
            val path = pathOf(href) ?: continue
            val seriesId = normalizeSeriesId(path)
            if (seriesId.contains("/")) continue // chapter link
            if (!seen.add(seriesId)) continue

            val parent = a.parent() ?: a
            val title = parent.selectFirst("p, h3, h2, .title, img[alt]")?.let {
                if (it.hasAttr("alt")) it.attr("alt") else it.text()
            }?.trim().orEmpty().ifBlank { a.text().trim() }.ifBlank { prettifySlug(seriesId) }

            val cover = parent.selectFirst("img")?.imageSrc().orEmpty()

            cards.add(
                Manga(
                    id = "series/$seriesId",
                    title = title,
                    slug = seriesId,
                    coverImage = cover,
                    sourceId = sourceId
                )
            )
        }

        return cards
    }

    private fun getNextData(doc: Document): JSONObject? {
        val jsonText = doc.selectFirst("script#__NEXT_DATA__")?.data() ?: return null
        return runCatching { JSONObject(jsonText) }.getOrNull()
    }

    private fun normalizeSeriesId(rawId: String): String =
        rawId.removePrefix("series/").trim('/')

    private fun pathOf(url: String): String? {
        val path = runCatching { URI(url).path }.getOrNull() ?: url
        return path.trim('/').ifBlank { null }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        val CHAPTER_REGEX = Regex("(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    }
}
