package com.mangapill.app.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.mangapill.app.data.local.AppDatabase
import com.mangapill.app.data.local.BookmarkEntity
import com.mangapill.app.data.local.MangaEntity
import com.mangapill.app.data.local.ReadingHistoryEntity
import com.mangapill.app.data.prefs.AppPrefs
import com.mangapill.app.data.model.ReaderMode
import com.mangapill.app.ui.theme.AppTheme
import com.mangapill.app.data.scraper.BaseScraper
import org.json.JSONArray
import org.json.JSONObject

/**
 * What Mangapill syncs: reading progress per title, My List, the titles those two refer to (so a
 * title read on another device shows with its name and cover here) and the reader/appearance
 * settings. OPDS servers and their passwords stay on the device.
 */
object LibrarySync {
    val collections: Map<String, CollectionSpec> = linkedMapOf(
        "history" to CollectionSpec.Records({ it.optString("mangaId") }, { it.optLong("lastReadAt") }),
        "bookmarks" to CollectionSpec.Records({ it.optString("mangaId") }),
        "titles" to CollectionSpec.Lookup,
        "preferences" to CollectionSpec.Blob
    )

    fun create(context: Context, url: String, publicKey: String, db: AppDatabase, prefs: AppPrefs): CloudSync {
        val sp = context.getSharedPreferences("mangapill_cloud", Context.MODE_PRIVATE)
        val store = object : KeyValueStore {
            override fun get(key: String): String? = sp.getString(key, null)
            override fun set(key: String, value: String?) {
                sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
            }
        }
        return CloudSync(
            url, publicKey, "mangapill", collections, store, BaseScraper.sharedClient,
            read = { read(db, prefs) },
            write = { write(db, prefs, it) }
        )
    }

    private suspend fun read(db: AppDatabase, prefs: AppPrefs): Map<String, Any?> {
        val history = db.historyDao().getAllHistory()
        val bookmarks = db.bookmarkDao().getAllBookmarks()
        val ids = (history.map { it.mangaId } + bookmarks.map { it.mangaId }).distinct()
        val titles = JSONObject()
        for (chunk in ids.chunked(500)) db.mangaDao().getMangasByIds(chunk).forEach { titles.put(it.id, it.toJson()) }
        return mapOf(
            "history" to JSONArray(history.map { it.toJson() }),
            "bookmarks" to JSONArray(bookmarks.map { it.toJson() }),
            "titles" to titles,
            "preferences" to JSONObject()
                .put("readerMode", prefs.readerMode.value.name)
                .put("theme", prefs.theme.value.name)
                .put("clearGlass", prefs.clearGlass.value)
                .put("keepScreenOn", prefs.keepScreenOn.value)
                .put("tapToTurn", prefs.tapToTurn.value)
        )
    }

    private suspend fun write(db: AppDatabase, prefs: AppPrefs, state: Map<String, Any?>) {
        val history = (state["history"] as? JSONArray).objects().mapNotNull { it.toHistory() }
        val bookmarks = (state["bookmarks"] as? JSONArray).objects().mapNotNull { it.toBookmark() }
        val titles = state["titles"] as? JSONObject
        db.withTransaction {
            // Titles first: history and My List are shown through them. Ones already here are kept
            // as they are (they may be fresher than the synced copy).
            val wanted = (history.map { it.mangaId } + bookmarks.map { it.mangaId }).distinct()
            val have = wanted.chunked(500).flatMap { db.mangaDao().getMangasByIds(it) }.map { it.id }.toSet()
            val missing = wanted.filter { it !in have }.mapNotNull { titles?.optJSONObject(it)?.toManga() }
            if (missing.isNotEmpty()) db.mangaDao().insertMangas(missing)

            val keepHistory = history.map { it.mangaId }.toSet()
            db.historyDao().getAllHistory().filter { it.mangaId !in keepHistory }.forEach { db.historyDao().deleteHistory(it.mangaId) }
            db.historyDao().upsertHistories(history)

            val keepBookmarks = bookmarks.map { it.mangaId }.toSet()
            db.bookmarkDao().getAllBookmarks().filter { it.mangaId !in keepBookmarks }.forEach { db.bookmarkDao().deleteBookmark(it.mangaId) }
            db.bookmarkDao().upsertBookmarks(bookmarks)
        }
        (state["preferences"] as? JSONObject)?.let { p ->
            ReaderMode.entries.firstOrNull { it.name == p.optString("readerMode") }?.let { if (it != prefs.readerMode.value) prefs.setReaderMode(it) }
            AppTheme.entries.firstOrNull { it.name == p.optString("theme") }?.let { if (it != prefs.theme.value) prefs.setTheme(it) }
            if (p.has("clearGlass") && p.optBoolean("clearGlass") != prefs.clearGlass.value) prefs.setClearGlass(p.optBoolean("clearGlass"))
            if (p.has("keepScreenOn") && p.optBoolean("keepScreenOn") != prefs.keepScreenOn.value) prefs.setKeepScreenOn(p.optBoolean("keepScreenOn"))
            if (p.has("tapToTurn") && p.optBoolean("tapToTurn") != prefs.tapToTurn.value) prefs.setTapToTurn(p.optBoolean("tapToTurn"))
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun ReadingHistoryEntity.toJson() = JSONObject()
        .put("mangaId", mangaId).put("chapterId", chapterId).put("chapterNumber", chapterNumber.toDouble())
        .put("lastReadPage", lastReadPage).put("scrollPercentage", scrollPercentage.toDouble()).put("lastReadAt", lastReadAt)

    private fun JSONObject.toHistory(): ReadingHistoryEntity? {
        val id = optString("mangaId")
        val chapter = optString("chapterId")
        if (id.isEmpty() || chapter.isEmpty()) return null
        return ReadingHistoryEntity(
            mangaId = id, chapterId = chapter, chapterNumber = optDouble("chapterNumber", 0.0).toFloat(),
            lastReadPage = optInt("lastReadPage", 1), scrollPercentage = optDouble("scrollPercentage", 0.0).toFloat(),
            lastReadAt = optLong("lastReadAt")
        )
    }

    private fun BookmarkEntity.toJson() = JSONObject().put("mangaId", mangaId).put("status", status).put("addedAt", addedAt)

    private fun JSONObject.toBookmark(): BookmarkEntity? {
        val id = optString("mangaId")
        if (id.isEmpty()) return null
        return BookmarkEntity(id, optString("status", "READING"), optLong("addedAt"))
    }

    private fun MangaEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("slug", slug).put("coverImage", coverImage)
        .put("synopsis", synopsis.take(600)).put("status", status).put("rating", rating.toDouble())
        .put("genresCsv", genresCsv).put("sourceId", sourceId).put("latestChapterNumber", latestChapterNumber.toDouble())

    private fun JSONObject.toManga(): MangaEntity? {
        val id = optString("id")
        val title = optString("title")
        if (id.isEmpty() || title.isEmpty()) return null
        return MangaEntity(
            id = id, title = title, slug = optString("slug"), coverImage = optString("coverImage"),
            synopsis = optString("synopsis"), status = optString("status", "UNKNOWN"),
            rating = optDouble("rating", 0.0).toFloat(), genresCsv = optString("genresCsv"),
            sourceId = optString("sourceId"), latestChapterNumber = optDouble("latestChapterNumber", 0.0).toFloat()
        )
    }
}
