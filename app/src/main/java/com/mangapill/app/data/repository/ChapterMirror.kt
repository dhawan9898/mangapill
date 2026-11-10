package com.mangapill.app.data.repository

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.PageImage
import kotlin.math.abs

/**
 * Pages for a chapter, and the source that actually served them when it was not the title's own
 * source (a catalogue-only source like Comick, or a source that is down).
 */
data class ChapterPages(val pages: List<PageImage>, val servedBy: String? = null)

/**
 * Finding the same title on another source, so a chapter can be read there. Matching is strict on
 * purpose: "Solo Leveling" must not open "Solo Leveling: Ragnarok" or the novel.
 */
internal object ChapterMirror {

    /** Normalised names the title is known by (main title first). */
    fun titleKeys(manga: Manga): List<String> =
        (listOf(manga.title) + manga.altTitles).map(::normalizeTitle).filter { it.length >= 3 }.distinct()

    /** Queries worth sending to another source's search: the title and a couple of Latin-script alternatives. */
    fun queries(manga: Manga): List<String> {
        val latin = manga.altTitles.filter { t -> t.all { it.code < 0x250 } }
        // Full names before abbreviations ("Tower of God" before "TOG").
        val ordered = latin.filter { ' ' in it.trim() } + latin.filterNot { ' ' in it.trim() }
        return (listOf(manga.title) + ordered)
            .map { it.trim() }.filter { it.length >= 2 }.distinctBy(::normalizeTitle).take(4)
    }

    /**
     * 100 for the same name, 70 when the names differ only by a short tail or head ("Solo Leveling"
     * vs "Solo Leveling (Official)" is fine, "... Ragnarok" is not), else 0.
     */
    fun score(candidate: String, keys: List<String>): Int {
        val c = normalizeTitle(candidate)
        if (c.length < 3) return 0
        var best = 0
        for (k in keys) {
            if (c == k) return 100
            val (short, long) = if (c.length <= k.length) c to k else k to c
            if (long.contains(short) && short.length >= 6 && short.length.toFloat() / long.length >= 0.85f) best = maxOf(best, 70)
        }
        return best
    }

    /** The best-matching title in [results], if any is a real match. */
    fun bestMatch(results: List<Manga>, keys: List<String>): Pair<Manga, Int>? =
        results.map { it to score(it.title, keys) }.filter { it.second > 0 }.maxByOrNull { it.second }

    /** The chapter with the same number (a oneshot / unnumbered chapter has no reliable equivalent). */
    fun sameChapter(chapters: List<Chapter>, number: Float): Chapter? =
        if (number <= 0f) null else chapters.firstOrNull { abs(it.chapterNumber - number) < 0.001f }
}
