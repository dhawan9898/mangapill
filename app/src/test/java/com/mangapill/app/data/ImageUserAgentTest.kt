package com.mangapill.app.data

import com.mangapill.app.data.scraper.BaseScraper
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUserAgentTest {
    @Test
    fun mangaDexHostsGetTheAppUserAgent() {
        // uploads.mangadex.org redirects a browser User-Agent to its bot challenge page.
        assertEquals(BaseScraper.APP_USER_AGENT, BaseScraper.imageUserAgent("uploads.mangadex.org"))
        assertEquals(BaseScraper.APP_USER_AGENT, BaseScraper.imageUserAgent("abc123.xyz.mangadex.network"))
    }

    @Test
    fun otherCdnsGetTheBrowserUserAgent() {
        assertEquals(BaseScraper.DEFAULT_USER_AGENT, BaseScraper.imageUserAgent("cdn.readdetectiveconan.com"))
        assertEquals(BaseScraper.DEFAULT_USER_AGENT, BaseScraper.imageUserAgent("notmangadex.org"))
    }
}
