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
    val latestChapterNumber: Float = 0f,
    /** Other names the title is known by (English, romanised...). Used to find it on another source. */
    val altTitles: List<String> = emptyList()
) {
    /** Globally unique key (ids are only unique within a source). Used for DB rows. */
    val key: String get() = "$sourceId:$id"
}

/** Filter values: type = manga|manhwa|manhua, status = ongoing|completed|hiatus, genre = a [Genres] slug. */
data class SearchFilters(
    val query: String = "",
    val type: String? = null,
    val genre: String? = null,
    val status: String? = null
) {
    val hasFilters: Boolean get() = type != null || genre != null || status != null
}

data class Genre(val slug: String, val name: String)

/** The genres offered in the UI; each source maps them to its own ids. */
object Genres {
    val all = listOf(
        Genre("action", "Action"),
        Genre("adventure", "Adventure"),
        Genre("comedy", "Comedy"),
        Genre("drama", "Drama"),
        Genre("fantasy", "Fantasy"),
        Genre("horror", "Horror"),
        Genre("isekai", "Isekai"),
        Genre("mystery", "Mystery"),
        Genre("romance", "Romance"),
        Genre("slice-of-life", "Slice of Life")
    )

    fun bySlug(slug: String?): Genre? = all.firstOrNull { it.slug == slug }

    /** Maps a genre name as a source spells it ("Slice of Life", "slice_of_life") to a catalog entry. */
    fun match(name: String): Genre? {
        val slug = name.trim().lowercase().replace(Regex("[^a-z]+"), "-").trim('-')
        return all.firstOrNull { it.slug == slug }
    }
}

data class SourceInfo(val id: String, val name: String, val experimental: Boolean = false)

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

enum class ReaderMode(val label: String) {
    /** One long vertical strip (webtoons, manhwa). */
    WEBTOON("Webtoon"),
    /** One page at a time, swiping left to go forward. */
    MANGA_HORIZONTAL("Paged"),
    /** One page at a time, right to left, the way Japanese manga is read. */
    MANGA_RTL("Right to left");

    fun next(): ReaderMode = entries[(ordinal + 1) % entries.size]
}
