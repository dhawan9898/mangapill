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

class ComickScraper : BaseScraper() {
    override val sourceId = "comick"
    override val sourceName = "Comick"
    override val baseUrl = "https://api.comick.fun"
    override val userAgent = "MangapillApp/1.0"

    private val staticBaseUrl = "https://meo.comick.pictures"

    override suspend fun search(filters: SearchFilters): List<Manga> {
        var url = "/v1.0/search?limit=30"
        if (filters.query.isNotBlank()) url += "&q=${URLEncoder.encode(filters.query, "UTF-8")}"
        filters.type?.let { type ->
            val country = when(type) { "manga" -> "jp"; "manhwa" -> "kr"; "manhua" -> "cn"; else -> null }
            if (country != null) url += "&country=$country"
        }
        filters.status?.let { status ->
            val s = when(status) { "ongoing" -> "1"; "completed" -> "2"; else -> null }
            if (s != null) url += "&status=$s"
        }
        filters.genre?.let { genre ->
            url += "&genres=${genre.lowercase()}"
        }
        val jsonArray = getJsonArray(url)
        return (0 until jsonArray.length()).map { parseManga(jsonArray.getJSONObject(it)) }
    }

    override suspend fun getPopular(): List<Manga> {
        return search(SearchFilters()).take(20) // Simple fallback for popular since search is sorted by relevance/hot by default
    }

    override suspend fun getLatestUpdates(): List<Manga> {
        // Comick chapter feed is different, so we will use search sorted by update.
        val jsonArray = getJsonArray("/v1.0/search?sort=uploaded&limit=30")
        return (0 until jsonArray.length()).map { parseManga(jsonArray.getJSONObject(it)) }
    }

    override suspend fun getMangaDetails(sourceMangaId: String): Manga {
        val json = getJsonObject("/comic/$sourceMangaId")
        val comic = json.getJSONObject("comic")
        return parseManga(comic, detailed = true)
    }

    override suspend fun getChapterList(sourceMangaId: String): List<Chapter> {
        val json = getJsonObject("/comic/$sourceMangaId/chapters?limit=500&lang=en")
        val chapters = json.optJSONArray("chapters") ?: JSONArray()
        val byNumber = LinkedHashMap<String, Chapter>()
        
        for (i in 0 until chapters.length()) {
            val ch = chapters.getJSONObject(i)
            val chapNumStr = ch.optString("chap").takeIf { it.isNotBlank() } ?: continue
            val id = ch.getString("hid")
            if (byNumber.containsKey(chapNumStr)) continue // Skip duplicates

            byNumber[chapNumStr] = Chapter(
                id = id,
                mangaId = sourceMangaId,
                chapterNumber = chapNumStr.toFloatOrNull() ?: 0f,
                title = ch.optString("title").takeIf { it.isNotBlank() },
                releaseDate = ch.optString("created_at")?.take(10)
            )
        }
        return byNumber.values.toList().sortedByDescending { it.chapterNumber }
    }

    override suspend fun getChapterPages(sourceChapterId: String): List<PageImage> {
        val json = getJsonObject("/chapter/$sourceChapterId")
        val chapter = json.getJSONObject("chapter")
        val images = chapter.optJSONArray("images") ?: JSONArray()
        
        return (0 until images.length()).map { i ->
            val img = images.getJSONObject(i)
            val url = img.getString("url")
            PageImage(
                pageNumber = i + 1,
                imageUrl = "$url",
                referer = "https://comick.app/"
            )
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

    private fun parseManga(obj: JSONObject, detailed: Boolean = false): Manga {
        val slug = obj.getString("slug")
        val hid = obj.optString("hid", slug)
        
        val title = obj.optString("title")
        val cover = obj.optString("md_covers")?.let { 
            if (it.isNotBlank()) return@let "$it"
            null
        } ?: obj.optJSONArray("md_covers")?.let {
            if (it.length() > 0) it.getJSONObject(0).optString("b2key") else null
        } ?: ""

        val coverUrl = if (cover.isNotBlank() && !cover.startsWith("http")) "$staticBaseUrl/$cover" else cover
        
        val synopsis = obj.optString("desc").trim()
        val statusVal = obj.optInt("status", 1)

        return Manga(
            id = hid,
            title = title,
            slug = slug,
            coverImage = coverUrl,
            synopsis = synopsis,
            status = when(statusVal) {
                1 -> MangaStatus.ONGOING
                2 -> MangaStatus.COMPLETED
                else -> MangaStatus.UNKNOWN
            },
            sourceId = sourceId
        )
    }
}
