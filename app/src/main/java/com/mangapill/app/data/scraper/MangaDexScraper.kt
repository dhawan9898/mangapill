package com.mangapill.app.data.scraper

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.PageImage
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

    override suspend fun search(filters: com.mangapill.app.data.model.SearchFilters): List<Manga> {
        var url = "/manga?limit=30&order[relevance]=desc&$listFilters"
        if (filters.query.isNotBlank()) url += "&title=${URLEncoder.encode(filters.query, "UTF-8")}"
        filters.type?.let { type ->
            val lang = when(type) { "manga" -> "ja"; "manhwa" -> "ko"; "manhua" -> "zh"; else -> null }
            if (lang != null) url += "&originalLanguage[]=$lang"
        }
        filters.status?.let { status -> url += "&status[]=${status.lowercase()}" }
        // Note: MangaDex genre requires tag UUIDs, omitted for simplicity
        return parseMangaList(getJson(url))
    }

    override suspend fun getPopular(): List<Manga> =
        parseMangaList(getJson("/manga?limit=20&order[followedCount]=desc&$listFilters"))

    override suspend fun getLatestUpdates(): List<Manga> =
        parseMangaList(getJson("/manga?limit=30&order[latestUploadedChapter]=desc&$listFilters"))

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
                if (attrs.optInt("pages", 1) == 0) continue
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

        var title = localized(attrs.optJSONObject("title"))
        if (title == null) {
            val alts = attrs.optJSONArray("altTitles") ?: JSONArray()
            title = (0 until alts.length()).firstNotNullOfOrNull { localized(alts.getJSONObject(it)) }
        }

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
            sourceId = sourceId
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
}
