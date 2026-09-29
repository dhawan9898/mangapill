package com.mangapill.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.mangapill.app.data.model.BookmarkStatus
import com.mangapill.app.data.model.MangaStatus

@Entity(tableName = "mangas")
data class MangaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val slug: String,
    val coverImage: String,
    val synopsis: String,
    val status: String,
    val rating: Float,
    val genresCsv: String,
    val sourceId: String,
    val latestChapterNumber: Float,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val chapterNumber: Float,
    val title: String?,
    val releaseDate: String?,
    val url: String
)

@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
    @PrimaryKey val mangaId: String,
    val chapterId: String,
    val chapterNumber: Float,
    val lastReadPage: Int,
    val scrollPercentage: Float,
    val lastReadAt: Long
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val mangaId: String,
    val status: String,
    val addedAt: Long
)

/** A reading-history row joined with the manga it belongs to. */
data class HistoryRow(
    @Embedded val manga: MangaEntity,
    val chapterId: String,
    val chapterNumber: Float,
    val lastReadPage: Int,
    val scrollPercentage: Float,
    val lastReadAt: Long
)

class DatabaseConverters {
    @TypeConverter
    fun fromGenresList(genres: List<String>?): String {
        return genres?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toGenresList(data: String?): List<String> {
        if (data.isNullOrEmpty()) return emptyList()
        return data.split(",")
    }
}
