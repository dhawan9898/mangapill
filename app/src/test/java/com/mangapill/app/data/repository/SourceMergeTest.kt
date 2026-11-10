package com.mangapill.app.data.repository

import com.mangapill.app.data.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SourceMergeTest {
    private fun m(title: String, source: String) = Manga(id = "$source/$title", title = title, slug = title, coverImage = "", sourceId = source)

    @Test
    fun normalizeIgnoresCaseSpacesAndPunctuation() {
        assertEquals("sololeveling", normalizeTitle("Solo Leveling!"))
        assertEquals(normalizeTitle("solo-leveling"), normalizeTitle("SOLO  LEVELING"))
        assertEquals("", normalizeTitle(" -- "))
    }

    @Test
    fun aSingleSourceIsReturnedAsIs() {
        val only = listOf(m("A", "x"), m("A", "x")) // not de-duplicated: nothing to compare against
        assertSame(only, interleaveSources(listOf(only)))
    }

    @Test
    fun sourcesAlternateSoEachIsNearTheTop() {
        val merged = interleaveSources(listOf(
            listOf(m("A1", "a"), m("A2", "a"), m("A3", "a")),
            listOf(m("B1", "b"), m("B2", "b"))
        ))
        assertEquals(listOf("A1", "B1", "A2", "B2", "A3"), merged.map { it.title })
    }

    @Test
    fun aTitleAnotherSourceAlreadyProvidedIsDropped() {
        val merged = interleaveSources(listOf(
            listOf(m("Solo Leveling", "a"), m("Berserk", "a")),
            listOf(m("solo-leveling", "b"), m("Vagabond", "b"))
        ))
        assertEquals(listOf("Solo Leveling", "Berserk", "Vagabond"), merged.map { it.title })
        // The first source to provide a title keeps it.
        assertEquals("a", merged.first().sourceId)
    }

    @Test
    fun emptyAndUnevenListsAreHandled() {
        assertEquals(emptyList<Manga>(), interleaveSources(listOf(emptyList(), emptyList())))
        assertEquals(listOf("B1", "B2"), interleaveSources(listOf(emptyList(), listOf(m("B1", "b"), m("B2", "b")))).map { it.title })
    }
}
