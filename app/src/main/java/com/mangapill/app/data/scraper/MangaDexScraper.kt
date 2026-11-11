package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.SearchFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Provider backed by the official, documented MangaDex REST API (no HTML scraping needed). */
class MangaDexScraper : BaseScraper() {
    override val sourceId = "mangadex"
    override val sourceName = "MangaDex"
    override val baseUrl = "https://api.mangadex.org"
    override val userAgent = "MangapillApp/1.0 (Android)"

    private val coverBase = "https://uploads.mangadex.org/covers"
    private val listFilters = "includes[]=cover_art&contentRating[]=safe&contentRating[]=suggestive" +
        "&availableTranslatedLanguage[]=en&hasAvailableChapters=true"

    override val imageReferer: String? = null
    override val supportsHiddenGems = true

    override fun supports(filters: SearchFilters): Boolean =
        (filters.type == null || filters.type in LANGUAGES) &&
            (filters.status == null || filters.status in setOf("ongoing", "completed", "hiatus")) &&
            (filters.genre == null || filters.genre in TAGS)

    override suspend fun search(filters: SearchFilters): List<Manga> {
        val q = filters.query.trim()
        val url = StringBuilder("/manga?limit=30&$listFilters")
        if (q.isNotEmpty()) {
            url.append("&title=").append(URLEncoder.encode(q, "UTF-8")).append("&order[relevance]=desc")
        } else {
            url.append("&order[followedCount]=desc")
        }
        LANGUAGES[filters.type]?.let { url.append("&originalLanguage[]=").append(it) }
        filters.status?.let { url.append("&status[]=").append(it) }
        TAGS[filters.genre]?.let { url.append("&includedTags[]=").append(it) }
        return parseMangaList(getJson(url.toString()))
    }

    /**
     * A MangaDex list for the home screen: [query] is the ordering / filtering part of /manga
     * (e.g. "order[rating]=desc&originalLanguage[]=ko"); covers and the usual safety filters are added.
     */
    suspend fun list(query: String, limit: Int = 20): List<Manga> =
        parseMangaList(getJson("/manga?limit=$limit&$query&$listFilters"))

    override suspend fun getPopular(): List<Manga> =
        parseMangaList(getJson("/manga?limit=20&order[followedCount]=desc&$listFilters"))

    override suspend fun getLatestUpdates(): List<Manga> =
        parseMangaList(getJson("/manga?limit=30&order[latestUploadedChapter]=desc&$listFilters"))

    /** Best-rated titles created in the last two years: good, but not yet on everyone's list. */
    override suspend fun getHiddenGems(): List<Manga> {
        val since = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(System.currentTimeMillis() - 2L * 365 * 24 * 3600 * 1000))
        return parseMangaList(getJson("/manga?limit=20&order[rating]=desc&createdAtSince=$since&$listFilters"))
    }

    override suspend fun getMangaDetails(sourceMangaId: String): Manga {
        val json = getJson("/manga/$sourceMangaId?includes[]=cover_art")
        val manga = parseManga(json.getJSONObject("data"))
        val rating = runCatching {
            getJson("/statistics/manga/$sourceMangaId").getJSONObject("statistics")
                .getJSONObject(sourceMangaId).getJSONObject("rating").optDouble("bayesian", 0.0).toFloat()
        }.getOrDefault(0f)
        return manga.copy(rating = rating)
    }

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> {
        val byNumber = LinkedHashMap<String, Chapter>()
        var offset = 0
        repeat(6) {
            val json = getJson(
                "/manga/$sourceMangaId/feed?limit=500&offset=$offset&translatedLanguage[]=en&order[chapter]=desc" +
                    "&includeExternalUrl=0&contentRating[]=safe&contentRating[]=suggestive" +
                    "&contentRating[]=erotica&contentRating[]=pornographic"
            )
            val data = json.optJSONArray("data") ?: JSONArray()
            for (i in 0 until data.length()) {
                val ch = data.getJSONObject(i)
                val attrs = ch.getJSONObject("attributes")
                if (attrs.optStringOrNull("externalUrl") != null || attrs.optInt("pages", 0) == 0) continue
                val numberText = attrs.optStringOrNull("chapter")
                val key = numberText ?: ch.getString("id")
                if (byNumber.containsKey(key)) continue // several scanlation groups: keep the first
                val title = attrs.optStringOrNull("title")
                byNumber[key] = Chapter(
                    id = ch.getString("id"),
                    mangaId = sourceMangaId,
                    chapterNumber = numberText?.toFloatOrNull() ?: 0f,
                    title = title ?: if (numberText == null) "Oneshot" else null,
                    releaseDate = attrs.optStringOrNull("publishAt")?.take(10)
                )
            }
            offset += data.length()
            if (data.length() == 0 || offset >= json.optInt("total", 0)) return byNumber.values.toList()
        }
        return byNumber.values.toList()
    }

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> {
        val json = getJson("/at-home/server/$sourceChapterId")
        val host = json.getString("baseUrl")
        val chapter = json.getJSONObject("chapter")
        val hash = chapter.getString("hash")
        val files = chapter.getJSONArray("data")
        return (0 until files.length()).map { i ->
            PageImage(pageNumber = i + 1, imageUrl = "$host/data/$hash/${files.getString(i)}", referer = null)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private suspend fun getJson(path: String): JSONObject {
        val text = fetchText(path, mapOf("Accept" to "application/json"))
        return withContext(Dispatchers.Default) { JSONObject(text) }
    }

    internal fun parseMangaList(json: JSONObject): List<Manga> {
        val data = json.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).map { parseManga(data.getJSONObject(it)) }
    }

    internal fun parseManga(obj: JSONObject): Manga {
        val id = obj.getString("id")
        val attrs = obj.getJSONObject("attributes")

        var cover = ""
        val rels = obj.optJSONArray("relationships") ?: JSONArray()
        for (i in 0 until rels.length()) {
            val rel = rels.getJSONObject(i)
            if (rel.optString("type") == "cover_art") {
                val file = rel.optJSONObject("attributes")?.optStringOrNull("fileName")
                if (file != null) cover = "$coverBase/$id/$file.512.jpg"
            }
        }

        val tags = attrs.optJSONArray("tags") ?: JSONArray()
        val genres = (0 until tags.length()).mapNotNull {
            localized(tags.getJSONObject(it).optJSONObject("attributes")?.optJSONObject("name"))
        }

        // MangaDex's main title is often romanised ("Na Honjaman Level-Up"); the English name, when
        // there is one, is among the alternative titles ("Solo Leveling"). Show the English one, keep
        // the others for matching the title on other sources.
        val main = attrs.optJSONObject("title")
        val alts = attrs.optJSONArray("altTitles") ?: JSONArray()
        val altList = (0 until alts.length()).mapNotNull { alts.optJSONObject(it) }
        val english = main?.optStringOrNull("en")
            ?: altList.firstNotNullOfOrNull { it.optStringOrNull("en") }
        val title = english ?: localized(main) ?: altList.firstNotNullOfOrNull { localized(it) }
        val otherNames = (listOfNotNull(localized(main)) + altList.flatMap { o ->
            o.keys().asSequence().filter { it == "en" || it.endsWith("-ro") }.mapNotNull { o.optStringOrNull(it) }.toList()
        }).filter { it != title }.distinct().take(8)

        return Manga(
            id = id,
            title = title ?: "Untitled",
            slug = id,
            coverImage = cover,
            synopsis = localized(attrs.optJSONObject("description")).orEmpty().trim(),
            status = when (attrs.optString("status")) {
                "ongoing" -> MangaStatus.ONGOING
                "completed" -> MangaStatus.COMPLETED
                "hiatus" -> MangaStatus.HIATUS
                else -> MangaStatus.UNKNOWN
            },
            genres = genres,
            sourceId = sourceId,
            altTitles = otherNames
        )
    }

    private fun localized(map: JSONObject?): String? {
        if (map == null) return null
        map.optStringOrNull("en")?.let { return it }
        val keys = map.keys()
        while (keys.hasNext()) map.optStringOrNull(keys.next())?.let { return it }
        return null
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    companion object {
        internal val LANGUAGES = mapOf("manga" to "ja", "manhwa" to "ko", "manhua" to "zh")

        /** MangaDex tag ids for [com.mangapill.app.data.model.Genres]. */
        internal val TAGS = mapOf(
            "action" to "391b0423-d847-456f-aff0-8b0cfc03066b",
            "adventure" to "87cc87cd-a395-47af-b27a-93258283bbc6",
            "comedy" to "4d32cc48-9f00-4cca-9b5a-a839f0764984",
            "drama" to "b9af3a63-f058-46de-a9a0-e0c13906197a",
            "fantasy" to "cdc58593-87dd-415e-bbc0-2ec27bf404cc",
            "horror" to "cdad7e68-1419-41dd-bdce-27753074a640",
            "isekai" to "ace04997-f6bd-436e-b261-779182193d3d",
            "mystery" to "ee968100-4191-4968-93d3-f82d72be7e46",
            "romance" to "423e2eae-a7a2-4a8b-ac03-a8351462d71d",
            "slice-of-life" to "e5301a23-ebd9-49dd-a0cb-2add944c7fe9"
        )
    }
}
