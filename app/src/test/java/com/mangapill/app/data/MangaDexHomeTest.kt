package com.mangapill.app.data

import com.mangapill.app.data.repository.HomeRail
import com.mangapill.app.data.scraper.MangaDexScraper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaDexHomeTest {
    private fun manga(title: String, alts: String) = JSONObject(
        """{"id":"x","attributes":{"title":$title,"altTitles":$alts,"description":{"en":"d"},"status":"ongoing","tags":[]},"relationships":[]}"""
    )

    @Test fun theEnglishNameIsShownWhenTheMainTitleIsRomanised() {
        val m = MangaDexScraper().parseManga(manga("""{"ko-ro":"Na Honjaman Level-Up"}""", """[{"ko":"나 혼자만 레벨업"},{"en":"Solo Leveling"},{"ja-ro":"Ore dake Level Up na Ken"}]"""))
        assertEquals("Solo Leveling", m.title)
        assertTrue("the romanised names are kept for matching", "Na Honjaman Level-Up" in m.altTitles && "Ore dake Level Up na Ken" in m.altTitles)
    }

    @Test fun anEnglishMainTitleStays() {
        val m = MangaDexScraper().parseManga(manga("""{"en":"Chainsaw Man"}""", """[{"en":"CSM"}]"""))
        assertEquals("Chainsaw Man", m.title)
    }

    @Test fun railsAskMangaDexForTheRightList() {
        assertEquals("order[followedCount]=desc", HomeRail.POPULAR.query())
        assertEquals("originalLanguage[]=ko&order[followedCount]=desc", HomeRail.MANHWA.query())
        assertTrue(HomeRail.ACTION.query().startsWith("includedTags[]=391b0423-d847-456f-aff0-8b0cfc03066b"))
        assertTrue(HomeRail.TOP_NEW.query(now = 0L + 200L * 24 * 3600 * 1000).contains("createdAtSince=1970-03-"))
        assertEquals(10, HomeRail.TOP_NEW.limit)
        assertEquals(HomeRail.SeeAll(genre = "romance"), HomeRail.ROMANCE.seeAll)
    }
}
