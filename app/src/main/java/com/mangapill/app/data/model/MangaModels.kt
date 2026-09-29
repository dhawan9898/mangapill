package com.mangapill.app.data.model

data class Manga(
    val id: String,
    val title: String,
    val slug: String,
    val coverImage: String,
    val synopsis: String = "",
    val status: MangaStatus = MangaStatus.ONGOING,
    val rating: Float = 0.0f,
    val genres: List<String> = emptyList(),
    val sourceId: String = "mangapill",
    val latestChapterNumber: Float = 0f
) {
    /** Globally unique key (ids are only unique within a source). Used for DB rows. */
    val key: String get() = "$sourceId:$id"
}

data class SearchFilters(
    val query: String = "",
    val type: String? = null, // e.g. "manga", "manhwa", "manhua"
    val genre: String? = null,
    val status: String? = null // e.g. "ongoing", "completed", "hiatus"
)

data class SourceInfo(val id: String, val name: String)

enum class MangaStatus {
    ONGOING,
    COMPLETED,
    HIATUS,
    UNKNOWN
}

data class Chapter(
    val id: String,
    val mangaId: String,
    val chapterNumber: Float,
    val title: String? = null,
    val releaseDate: String? = null,
    val url: String = ""
)

data class PageImage(
    val pageNumber: Int,
    val imageUrl: String,
    val referer: String? = null
)

data class ReadingHistory(
    val mangaId: String,
    val chapterId: String,
    val chapterNumber: Float,
    val lastReadPage: Int = 1,
    val scrollPercentage: Float = 0.0f,
    val lastReadAt: Long = System.currentTimeMillis()
)

data class Bookmark(
    val mangaId: String,
    val status: BookmarkStatus = BookmarkStatus.READING,
    val addedAt: Long = System.currentTimeMillis()
)

enum class BookmarkStatus {
    READING,
    PLAN_TO_READ,
    COMPLETED,
    ON_HOLD,
    DROPPED
}

enum class ReaderMode {
    WEBTOON,
    MANGA_HORIZONTAL
}
