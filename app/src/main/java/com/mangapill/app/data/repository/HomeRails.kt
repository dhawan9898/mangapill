package com.mangapill.app.data.repository

import com.mangapill.app.data.scraper.MangaDexScraper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The rails of the home screen, all from the MangaDex API (like AniStream's and CineStream's
 * catalogue rails). [seeAll] is what "See all" opens on the Search tab, if anything.
 */
enum class HomeRail(val title: String, val subtitle: String? = null, val seeAll: SeeAll? = null) {
    POPULAR("Popular"),
    TOP_NEW("Top 10 new this season", "The most followed titles that started recently"),
    LATEST("Latest updates", "Fresh chapters"),
    TOP_RATED("Top rated", "Highest scores on MangaDex"),
    ACTION("Action", seeAll = SeeAll(genre = "action")),
    ROMANCE("Romance", seeAll = SeeAll(genre = "romance")),
    FANTASY("Fantasy", seeAll = SeeAll(genre = "fantasy")),
    COMEDY("Comedy", seeAll = SeeAll(genre = "comedy")),
    ISEKAI("Isekai", seeAll = SeeAll(genre = "isekai")),
    MANHWA("Manhwa", "Korean webtoons", SeeAll(type = "manhwa")),
    COMPLETED("Completed series", "Read start to finish", SeeAll(status = "completed"));

    /** The /manga query part for this rail. */
    fun query(now: Long = System.currentTimeMillis()): String = when (this) {
        POPULAR -> "order[followedCount]=desc"
        TOP_NEW -> "createdAtSince=${isoDate(now - 120L * DAY)}&order[followedCount]=desc"
        LATEST -> "order[latestUploadedChapter]=desc"
        TOP_RATED -> "order[rating]=desc"
        MANHWA -> "originalLanguage[]=ko&order[followedCount]=desc"
        COMPLETED -> "status[]=completed&order[followedCount]=desc"
        else -> "includedTags[]=${MangaDexScraper.TAGS[seeAll?.genre]}&order[followedCount]=desc"
    }

    val limit: Int get() = if (this == TOP_NEW) 10 else 20

    data class SeeAll(val genre: String? = null, val type: String? = null, val status: String? = null)

    private companion object {
        const val DAY = 24L * 3600 * 1000
        fun isoDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(ms))
    }
}
