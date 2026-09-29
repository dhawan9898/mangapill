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
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: ReadingHistoryEntity)

    @Query("SELECT * FROM reading_history WHERE mangaId = :mangaId")
    fun getHistoryForManga(mangaId: String): Flow<ReadingHistoryEntity?>

    @Query("SELECT * FROM reading_history ORDER BY lastReadAt DESC")
    fun getAllHistory(): Flow<List<ReadingHistoryEntity>>
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE mangaId = :mangaId")
    suspend fun deleteBookmark(mangaId: String)

    @Query("SELECT * FROM bookmarks WHERE mangaId = :mangaId")
    fun getBookmark(mangaId: String): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>
}
