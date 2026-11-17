package com.mangapill.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManga(manga: MangaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMangas(mangas: List<MangaEntity>)

    @Query("SELECT * FROM mangas WHERE id = :mangaId")
    suspend fun getMangaById(mangaId: String): MangaEntity?

    @Query("SELECT * FROM mangas WHERE title LIKE '%' || :query || '%'")
    suspend fun searchMangas(query: String): List<MangaEntity>

    /** At most 999 ids per call (SQLite's limit on query arguments). */
    @Query("SELECT * FROM mangas WHERE id IN (:ids)")
    suspend fun getMangasByIds(ids: List<String>): List<MangaEntity>

    @Query("SELECT * FROM mangas ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun getRecentManga(limit: Int): List<MangaEntity>
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: ReadingHistoryEntity)

    @Query("SELECT * FROM reading_history WHERE mangaId = :mangaId")
    fun getHistoryForManga(mangaId: String): Flow<ReadingHistoryEntity?>

    @Query("SELECT * FROM reading_history WHERE mangaId = :mangaId")
    suspend fun getHistoryOnce(mangaId: String): ReadingHistoryEntity?

    @Query(
        "SELECT m.*, h.chapterId AS chapterId, h.chapterNumber AS chapterNumber, " +
            "h.lastReadPage AS lastReadPage, h.scrollPercentage AS scrollPercentage, h.lastReadAt AS lastReadAt " +
            "FROM reading_history h INNER JOIN mangas m ON m.id = h.mangaId ORDER BY h.lastReadAt DESC"
    )
    fun getHistoryRows(): Flow<List<HistoryRow>>

    @Query("SELECT * FROM reading_history")
    suspend fun getAllHistory(): List<ReadingHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistories(history: List<ReadingHistoryEntity>)

    @Query("DELETE FROM reading_history")
    suspend fun clearHistory()

    @Query("DELETE FROM reading_history WHERE mangaId = :mangaId")
    suspend fun deleteHistory(mangaId: String)
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE mangaId = :mangaId")
    suspend fun deleteBookmark(mangaId: String)

    @Query("SELECT * FROM bookmarks WHERE mangaId = :mangaId")
    fun getBookmark(mangaId: String): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks WHERE mangaId = :mangaId")
    suspend fun getBookmarkOnce(mangaId: String): BookmarkEntity?

    @Query("SELECT m.* FROM mangas m INNER JOIN bookmarks b ON m.id = b.mangaId ORDER BY b.addedAt DESC")
    fun getBookmarkedMangas(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarks(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmarks(bookmarks: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks")
    suspend fun clearBookmarks()
}
