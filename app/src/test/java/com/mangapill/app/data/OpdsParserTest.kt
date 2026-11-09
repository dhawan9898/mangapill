package com.mangapill.app.data

import com.mangapill.app.data.opds.OpdsLink
import com.mangapill.app.data.opds.OpdsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsParserTest {
    private val base = "https://example.com/opds/v1.2/catalog"

    @Test
    fun atomFeedWithPageStreamingEntry() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:pse="http://vaemendis.net/opds-pse/ns">
              <title>Library</title>
              <link rel="next" type="application/atom+xml" href="/opds/v1.2/catalog?page=2"/>
              <entry>
                <id>urn:book:1</id>
                <title>One Piece</title>
                <summary>&lt;p&gt;Pirates&lt;/p&gt;</summary>
                <author><name>Oda</name></author>
                <category label="Action" term="a"/>
                <link rel="http://vaemendis.net/opds-pse/stream" type="image/jpeg" href="/pages/1?page={pageNumber}" pse:count="12"/>
                <link rel="http://opds-spec.org/image/thumbnail" type="image/jpeg" href="/thumb.jpg"/>
              </entry>
            </feed>
        """.trimIndent()

        val feed = OpdsParser.parseFeed(xml, base)

        assertEquals("Library", feed.title)
        assertEquals("https://example.com/opds/v1.2/catalog?page=2", feed.next?.href)
        val entry = feed.entries.single()
        assertEquals("One Piece", entry.title)
        assertEquals(listOf("Oda"), entry.authors)
        assertEquals(listOf("Action"), entry.categories)
        assertTrue(entry.summary.contains("Pirates"))
        assertFalse(entry.summary.contains("<p>"))
        assertTrue(entry.isReadable)
        assertEquals(12, entry.pageStream?.pageCount)
        // The {pageNumber} template survives resolution.
        assertEquals("https://example.com/pages/1?page={pageNumber}", entry.pageStream?.href)
        assertEquals("https://example.com/thumb.jpg", entry.cover)
    }

    @Test
    fun atomNavigationEntryIsAFeedNotABook() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom"><title>Root</title>
              <entry><id>n1</id><title>Series</title>
                <link rel="subsection" type="application/atom+xml;profile=opds-catalog" href="/series"/></entry>
            </feed>
        """.trimIndent()
        val entry = OpdsParser.parseFeed(xml, base).entries.single()
        assertNotNull(entry.feedLink)
        assertFalse(entry.isReadable)
        assertEquals("https://example.com/series", entry.feedLink?.href)
    }

    @Test
    fun untitledEntryGetsAPlaceholder() {
        val xml = """<feed xmlns="http://www.w3.org/2005/Atom"><title>T</title><entry><id>x</id></entry></feed>"""
        assertEquals("Untitled", OpdsParser.parseFeed(xml, base).entries.single().title)
    }

    @Test
    fun nonFeedXmlIsRejectedWithAClearError() {
        try {
            OpdsParser.parseFeed("<html><body>hi</body></html>", base)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Not an OPDS feed"))
        }
    }

    @Test
    fun opds2FeedWithNavigationAndPublications() {
        val json = """
            {"metadata":{"title":"Komga"},
             "links":[{"rel":"next","href":"/opds/v2/catalog?page=2","type":"application/opds+json"}],
             "navigation":[{"href":"/opds/v2/series","title":"Series"}],
             "publications":[{
               "metadata":{"identifier":"book-9","title":"Berserk","author":[{"name":"Miura"},"Someone"],"subject":["Dark Fantasy"]},
               "links":[{"rel":"http://vaemendis.net/opds-pse/stream","href":"/p/9/{pageNumber}","type":"image/jpeg","properties":{"numberOfItems":5}}],
               "images":[{"href":"/big.jpg","width":900},{"href":"/small.jpg","width":100}]
             }]}
        """.trimIndent()

        val feed = OpdsParser.parseFeed(json, "https://example.com/opds/v2/catalog")

        assertEquals("Komga", feed.title)
        assertEquals("https://example.com/opds/v2/catalog?page=2", feed.next?.href)
        val (nav, pub) = feed.entries
        assertEquals("Series", nav.title)
        // Navigation links often omit the type; they are still feeds.
        assertNotNull(nav.feedLink)
        assertEquals("book-9", pub.id)
        assertEquals(listOf("Miura", "Someone"), pub.authors)
        assertEquals(listOf("Dark Fantasy"), pub.categories)
        assertEquals(5, pub.pageStream?.pageCount)
        // Smallest image is the thumbnail, so covers stay light.
        assertEquals("https://example.com/small.jpg", pub.cover)
    }

    @Test
    fun manifestPagesFollowReadingOrderAndResolveAgainstTheBase() {
        val json = """{"readingOrder":[{"href":"a.jpg"},{"href":"/b.jpg"},{"title":"no href"},{"href":"https://cdn.test/c.jpg"}]}"""
        assertEquals(
            listOf("https://example.com/books/9/a.jpg", "https://example.com/b.jpg", "https://cdn.test/c.jpg"),
            OpdsParser.parseManifestPages(json, "https://example.com/books/9/manifest")
        )
        assertEquals(emptyList<String>(), OpdsParser.parseManifestPages("""{"metadata":{}}""", base))
    }

    @Test
    fun openSearchPrefersAnAtomTemplate() {
        val xml = """
            <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
              <Url type="text/html" template="/search?q={searchTerms}"/>
              <Url type="application/atom+xml" template="/opds/search?q={searchTerms}"/>
            </OpenSearchDescription>
        """.trimIndent()
        assertEquals("https://example.com/opds/search?q={searchTerms}", OpdsParser.parseOpenSearch(xml, base))
        assertNull(OpdsParser.parseOpenSearch("<OpenSearchDescription/>", base))
    }

    @Test
    fun pageUrlFillsTheTemplate() {
        assertEquals("https://x/p/3?w=1500", OpdsParser.pageUrl("https://x/p/{pageNumber}?w={maxWidth}", 3, 1500))
        assertEquals("https://x/p/0?w=2000", OpdsParser.pageUrl("https://x/p/{pageNumber}?w={maxWidth}", 0))
    }

    @Test
    fun linkClassification() {
        val image = OpdsLink(href = "h", rel = OpdsLink.REL_IMAGE)
        val thumb = OpdsLink(href = "h", rel = OpdsLink.REL_THUMBNAIL)
        val feed = OpdsLink(href = "h", rel = "subsection", type = OpdsLink.TYPE_ATOM)
        val entryDoc = OpdsLink(href = "h", rel = "alternate", type = "application/atom+xml;type=entry;profile=opds-catalog")
        assertTrue(image.isImage)
        assertTrue(thumb.isThumbnail)
        assertTrue(feed.isFeed)
        assertFalse(image.isFeed)
        assertFalse(entryDoc.isFeed)
    }
}
