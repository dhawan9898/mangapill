package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Genres
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.SearchFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Comick JSON API. Manga and chapter ids are Comick "hid"s.
 *
 * Comick stopped hosting chapter images: search, details and chapter lists still work, but every
 * chapter comes back without pages. Its chapters are therefore read from another source that has the
 * same title ([hostsPages] = false; see ChapterMirror).
 */
class ComickScraper : BaseScraper() {
    override val sourceId = "comick"
    override val sourceName = "Comick"
    override val baseUrl = "https://api.comick.dev"
    override val userAgent = "MangapillApp/1.0 (Android)"
    override val isExperimental = false
    override val imageReferer = "https://comick.dev/"
    override val supportsHiddenGems = true
    override val hostsPages = false

    private val imageHost = "https://meo.comick.pictures"

    override fun supports(filters: SearchFilters): Boolean =
        (filters.type == null || filters.type in COUNTRIES) &&
            (filters.status == null || filters.status in STATUSES) &&
            (filters.genre == null || Genres.bySlug(filters.genre) != null)

    override suspend fun search(filters: SearchFilters): List<Manga> {
        val url = StringBuilder("/v1.0/search?limit=30&tachiyomi=true")
        val q = filters.query.trim()
        if (q.isNotEmpty()) url.append("&q=").append(URLEncoder.encode(q, "UTF-8")) else url.append("&sort=follow")
        COUNTRIES[filters.type]?.let { url.append("&country=").append(it) }
        STATUSES[filters.status]?.let { url.append("&status=").append(it) }
        filters.genre?.let { url.append("&genres=").append(it) }
        return parseList(getJsonArray(url.toString()))
    }

    override suspend fun getPopular(): List<Manga> =
        parseList(getJsonArray("/v1.0/search?sort=follow&limit=20&tachiyomi=true"))

    override suspend fun getLatestUpdates(): List<Manga> =
        parseList(getJsonArray("/v1.0/search?sort=uploaded&limit=30&tachiyomi=true"))

    override suspend fun getHiddenGems(): List<Manga> =
        parseList(getJsonArray("/v1.0/search?sort=rating&limit=20&tachiyomi=true"))

    override suspend fun getMangaDetails(sourceMangaId: String): Manga {
        val json = getJsonObject("/comic/$sourceMangaId/?tachiyomi=true")
        return parseManga(json.getJSONObject("comic"))
    }

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> {
        val json = getJsonObject("/comic/$sourceMangaId/chapters?lang=en&limit=99999&tachiyomi=true")
        val list = json.optJSONArray("chapters") ?: JSONArray()
        val byNumber = LinkedHashMap<String, Chapter>()
        for (i in 0 until list.length()) {
            val ch = list.getJSONObject(i)
            val hid = ch.optStringOrNull("hid") ?: continue
            val number = ch.optStringOrNull("chap")
            val key = number ?: hid
            if (byNumber.containsKey(key)) continue // several scanlation groups: keep the first
            byNumber[key] = Chapter(
                id = hid,
                mangaId = sourceMangaId,
                chapterNumber = number?.toFloatOrNull() ?: 0f,
                title = ch.optStringOrNull("title") ?: if (number == null) "Oneshot" else null,
                releaseDate = ch.optStringOrNull("created_at")?.take(10)
            )
        }
        return byNumber.values.sortedByDescending { it.chapterNumber }
    }

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> {
        val chapter = getJsonObject("/chapter/$sourceChapterId/?tachiyomi=true").getJSONObject("chapter")
        // With tachiyomi=true the API returns ready-made URLs; otherwise only storage keys.
        val images = chapter.optJSONArray("images")?.takeIf { it.length() > 0 }
            ?: chapter.optJSONArray("md_images")
            ?: JSONArray()
        return (0 until images.length()).mapNotNull { i ->
            val img = images.getJSONObject(i)
            val url = img.optStringOrNull("url") ?: img.optStringOrNull("b2key")?.let { "$imageHost/$it" }
            url?.let { PageImage(pageNumber = i + 1, imageUrl = it, referer = imageReferer) }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private suspend fun getJsonObject(path: String): JSONObject {
        val text = fetchText(path, mapOf("Accept" to "application/json"))
        return withContext(Dispatchers.Default) { JSONObject(text) }
    }

    private suspend fun getJsonArray(path: String): JSONArray {
        val text = fetchText(path, mapOf("Accept" to "application/json"))
        return withContext(Dispatchers.Default) { JSONArray(text) }
    }

    internal fun parseList(array: JSONArray): List<Manga> =
        (0 until array.length()).mapNotNull { runCatching { parseManga(array.getJSONObject(it)) }.getOrNull() }

    internal fun parseManga(obj: JSONObject): Manga {
        val hid = obj.optStringOrNull("hid") ?: obj.getString("slug")

        // md_covers is an array of {b2key, w, h}; cover_url is only present on some endpoints.
        val cover = obj.optStringOrNull("cover_url")
            ?: obj.optJSONArray("md_covers")?.let { covers ->
                (0 until covers.length()).firstNotNullOfOrNull { covers.optJSONObject(it)?.optStringOrNull("b2key") }
            }?.let { "$imageHost/$it" }
            ?: ""

        val genres = obj.optJSONArray("md_comic_md_genres")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optJSONObject("md_genres")?.optStringOrNull("name") }
        }.orEmpty()

        // The details endpoint's "title" is often an odd alternative ("ORV", "I am the only the one
        // who levels up"); the alternative flagged is_default is the name the site shows.
        val defaultTitle = obj.optJSONArray("md_titles")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .firstOrNull { it.optBoolean("is_default") }?.optStringOrNull("title")
        }

        return Manga(
            id = hid,
            title = defaultTitle ?: obj.optStringOrNull("title") ?: prettifySlug(obj.optString("slug")),
            slug = obj.optString("slug", hid),
            coverImage = cover,
            synopsis = obj.optStringOrNull("desc")?.trim().orEmpty(),
            status = when (obj.optInt("status", 0)) {
                1 -> MangaStatus.ONGOING
                2 -> MangaStatus.COMPLETED
                4 -> MangaStatus.HIATUS
                else -> MangaStatus.UNKNOWN
            },
            rating = obj.optStringOrNull("bayesian_rating")?.toFloatOrNull() ?: 0f,
            genres = genres,
            sourceId = sourceId,
            // English / romanised alternative names help find the title on the source that serves pages.
            altTitles = obj.optJSONArray("md_titles")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.takeIf { it.optString("lang") in ALT_TITLE_LANGS }?.optStringOrNull("title")
                }
            }.orEmpty()
        )
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private companion object {
        val COUNTRIES = mapOf("manga" to "jp", "manhwa" to "kr", "manhua" to "cn")
        val STATUSES = mapOf("ongoing" to "1", "completed" to "2", "hiatus" to "4")
        val ALT_TITLE_LANGS = setOf("en", "ja-ro", "ko-ro", "zh-ro", "")
    }
}
