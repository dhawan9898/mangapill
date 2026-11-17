package com.mangapill.app.data.repository

import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.scraper.FlameComicsScraper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterMirrorTest {
    private fun manga(title: String, alts: List<String> = emptyList(), src: String = "x") =
        Manga(id = title, title = title, slug = title, coverImage = "", sourceId = src, altTitles = alts)

    private val solo = manga("Solo Leveling", listOf("Na Honjaman Level-Up", "Only I Level Up", "나 혼자만 레벨업"))

    @Test fun sameNameMatchesWhateverThePunctuation() {
        val keys = ChapterMirror.titleKeys(solo)
        assertEquals(100, ChapterMirror.score("Solo Leveling!", keys))
        assertEquals(100, ChapterMirror.score("Na Honjaman Level-up", keys))
    }

    @Test fun romanisationVariantsAreTheSameTitle() {
        // MangaDex: "Koi o Suru", Mangapill: "Koi wo Suru" - the same title; it was not found at all.
        val keys = ChapterMirror.titleKeys(manga("My Dress-Up Darling", listOf("Sono Bisque Doll wa Koi o Suru")))
        assertEquals(100, ChapterMirror.score("Sono Bisque Doll wa Koi wo Suru", keys))
        assertEquals(100, ChapterMirror.score("Sono Bisque Doll ha Koi wo Suru", keys))
        assertEquals(100, ChapterMirror.score("Shōjo Shūmatsu Ryokō", ChapterMirror.titleKeys(manga("Shoujo Shuumatsu Ryokou", emptyList()))))
        // ...but a different title still is not.
        assertEquals(0, ChapterMirror.score("Bisque Doll de Chu", keys))
    }

    @Test fun sequelsSpinOffsAndNovelsAreNotTheSameTitle() {
        val keys = ChapterMirror.titleKeys(solo)
        assertEquals(0, ChapterMirror.score("Solo Leveling: Ragnarok", keys))
        assertEquals(0, ChapterMirror.score("Solo Leveling Novel", keys))
        assertEquals(0, ChapterMirror.score("Solo", keys))
    }

    @Test fun aShortDecorationStillMatches() {
        val keys = ChapterMirror.titleKeys(manga("Omniscient Reader's Viewpoint"))
        assertEquals(70, ChapterMirror.score("Omniscient Reader's Viewpoint (WT)", keys))
    }

    @Test fun bestMatchPrefersTheExactTitle() {
        val results = listOf(manga("Solo Leveling Novel"), manga("Solo Leveling: Ragnarok"), manga("Solo Leveling"))
        assertEquals("Solo Leveling", ChapterMirror.bestMatch(results, ChapterMirror.titleKeys(solo))?.first?.title)
        assertNull(ChapterMirror.bestMatch(results.take(2), ChapterMirror.titleKeys(solo)))
    }

    @Test fun queriesUseLatinNamesAndFullNamesFirst() {
        val tog = manga("Sinui Tap", listOf("TOG", "Tower of God", "신의 탑"))
        assertEquals(listOf("Sinui Tap", "Tower of God", "TOG"), ChapterMirror.queries(tog))
    }

    @Test fun chaptersMatchByNumber() {
        val chapters = listOf(Chapter("a", "m", 101f), Chapter("b", "m", 100.5f), Chapter("c", "m", 100f))
        assertEquals("c", ChapterMirror.sameChapter(chapters, 100f)?.id)
        assertEquals("b", ChapterMirror.sameChapter(chapters, 100.5f)?.id)
        assertNull(ChapterMirror.sameChapter(chapters, 99f))
        assertNull("an unnumbered chapter has no reliable equivalent", ChapterMirror.sameChapter(chapters, 0f))
    }

    @Test fun flameSearchIsFilteredByTheQuery() {
        val all = listOf(manga("Solo Leveling"), manga("30 Years Have Passed"), manga("Solo Leveling: Ragnarok"))
        assertEquals(listOf("Solo Leveling", "Solo Leveling: Ragnarok"), FlameComicsScraper().filterByQuery(all, "solo leveling").map { it.title })
    }
}
