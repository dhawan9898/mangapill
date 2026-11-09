package com.mangapill.app.data

import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.scraper.MangaDexScraper
import com.mangapill.app.data.scraper.MangapillScraper
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperParsingTest {
    private val mangapill = MangapillScraper()
    private val mangadex = MangaDexScraper()

    private fun doc(html: String) = Jsoup.parse(html, "https://mangapill.com/")

    @Test
    fun mangapillCardsAreDeduplicatedAndUseTheBestTitle() {
        val cards = mangapill.parseMangaCards(doc("""
            <div>
              <a href="/manga/2/one-piece"><img data-src="/covers/op.jpg" alt="alt title"></a>
              <a href="/manga/2/one-piece"><div class="font-bold">One Piece</div></a>
              <a href="/manga/7/berserk"><img src="https://cdn.test/b.jpg"></a>
            </div>
        """.trimIndent()))

        assertEquals(listOf("2/one-piece", "7/berserk"), cards.map { it.id })
        val onePiece = cards.first()
        assertEquals("One Piece", onePiece.title) // a heading beats an img alt
        assertEquals("one-piece", onePiece.slug)
        assertEquals("https://mangapill.com/covers/op.jpg", onePiece.coverImage)
        assertEquals("mangapill", onePiece.sourceId)
        // No usable title: falls back to a readable version of the slug.
        assertEquals("Berserk", cards[1].title)
    }

    @Test
    fun mangapillChaptersAreSortedNewestFirstWithoutDuplicates() {
        val chapters = mangapill.parseChapters("2/one-piece", doc("""
            <a href="/chapters/2-10001000/one-piece-chapter-1">Chapter 1</a>
            <a href="/chapters/2-10010000/one-piece-chapter-10">Chapter 10</a>
            <a href="/chapters/2-10010000/one-piece-chapter-10">Chapter 10</a>
            <a href="/chapters/2-10002500/one-piece-chapter-2.5">Chapter 2.5</a>
        """.trimIndent()))

        assertEquals(listOf(10f, 2.5f, 1f), chapters.map { it.chapterNumber })
        assertEquals("2-10010000/one-piece-chapter-10", chapters.first().id)
        assertTrue(chapters.all { it.mangaId == "2/one-piece" })
    }

    @Test
    fun mangapillChapterNumberFallsBackToTheSlug() {
        val chapters = mangapill.parseChapters("2/x", doc("""<a href="/chapters/2-1/x-chapter-7">Read now</a>"""))
        assertEquals(7f, chapters.single().chapterNumber)
    }

    @Test
    fun mangapillPagesAreInOrderDeduplicatedAndCarryTheReferer() {
        val pages = mangapill.parsePages(doc("""
            <chapter-page><img data-src="https://cdn.test/1.jpg"></chapter-page>
            <chapter-page><img data-src="https://cdn.test/2.jpg"></chapter-page>
            <chapter-page><img data-src="https://cdn.test/2.jpg"></chapter-page>
        """.trimIndent()))

        assertEquals(listOf(1, 2), pages.map { it.pageNumber })
        assertEquals(listOf("https://cdn.test/1.jpg", "https://cdn.test/2.jpg"), pages.map { it.imageUrl })
        assertTrue(pages.all { it.referer == "https://mangapill.com/" })
    }

    @Test
    fun mangapillDetailsReadTitleGenresAndStatus() {
        val manga = mangapill.parseDetails("2/one-piece", doc("""
            <html><head><meta property="og:image" content="https://cdn.test/cover.jpg"></head><body>
              <h1>One Piece</h1>
              <p>Gol D. Roger was known as the Pirate King, the strongest and most infamous being to have sailed.</p>
              <div><label>Status</label> publishing</div>
              <a href="/search?genre=Action">Action</a><a href="/search?genre=Adventure">Adventure</a>
              <a href="/search?genre=Action">Action</a>
            </body></html>
        """.trimIndent()))

        assertEquals("One Piece", manga.title)
        assertEquals("https://cdn.test/cover.jpg", manga.coverImage)
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertEquals(listOf("Action", "Adventure"), manga.genres)
        assertTrue(manga.synopsis.startsWith("Gol D. Roger"))
    }

    @Test
    fun mangadexMangaReadsTitleCoverGenresAndStatus() {
        val manga = mangadex.parseManga(JSONObject("""
            {"id":"abc","attributes":{
               "title":{"ja-ro":"Berusaaku","en":"Berserk"},
               "description":{"en":"  Dark fantasy.  "},
               "status":"completed",
               "tags":[{"attributes":{"name":{"en":"Fantasy"}}},{"attributes":{"name":{"en":"Horror"}}}]},
             "relationships":[{"type":"author"},{"type":"cover_art","attributes":{"fileName":"cover.png"}}]}
        """.trimIndent()))

        assertEquals("abc", manga.id)
        assertEquals("Berserk", manga.title)
        assertEquals("Dark fantasy.", manga.synopsis)
        assertEquals(MangaStatus.COMPLETED, manga.status)
        assertEquals(listOf("Fantasy", "Horror"), manga.genres)
        assertTrue(manga.coverImage.endsWith("/abc/cover.png.512.jpg"))
    }

    @Test
    fun mangadexFallsBackToAltTitleThenUntitled() {
        val alt = mangadex.parseManga(JSONObject("""{"id":"1","attributes":{"title":{},"altTitles":[{"fr":"Titre"}]}}"""))
        assertEquals("Titre", alt.title)
        val none = mangadex.parseManga(JSONObject("""{"id":"2","attributes":{}}"""))
        assertEquals("Untitled", none.title)
        assertEquals(MangaStatus.UNKNOWN, none.status)
    }

    @Test
    fun mangadexListToleratesMissingData() {
        assertEquals(emptyList<Any>(), mangadex.parseMangaList(JSONObject("""{"result":"error"}""")))
        assertEquals(2, mangadex.parseMangaList(JSONObject("""{"data":[{"id":"a","attributes":{}},{"id":"b","attributes":{}}]}""")).size)
    }
}
