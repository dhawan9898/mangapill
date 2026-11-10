package com.mangapill.app.data

import com.mangapill.app.data.scraper.ScraperException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorTextTest {
    @Test fun offlineIsFoundInsideTheScraperWrapper() {
        val wrapped = ScraperException("Unable to resolve host \"mangadex.org\": No address associated with hostname", UnknownHostException("mangadex.org"))
        assertEquals(ErrorText.OFFLINE, ErrorText.describe(wrapped))
    }

    @Test fun timeouts() {
        assertEquals(ErrorText.SLOW, ErrorText.describe(ScraperException("x", SocketTimeoutException("read timed out"))))
        assertEquals(ErrorText.SLOW, ErrorText.describe(IOException("timed out")))
    }

    @Test fun httpStatusesBecomeSentences() {
        assertEquals("This title or chapter is no longer on the source.", ErrorText.describe(ScraperException("MangaDex returned HTTP 404")))
        assertEquals("The source is having problems. Try again later.", ErrorText.describe(ScraperException("Comick returned HTTP 502")))
        assertEquals(
            "The source is blocking the app right now. Try again later or switch source.",
            ErrorText.describe(ScraperException("Asura is protected by Cloudflare and blocked the request"))
        )
    }

    @Test fun readableScraperMessagesPassThrough() {
        assertEquals("Komga does not offer search", ErrorText.describe(ScraperException("Komga does not offer search")))
    }

    @Test fun internalsNeverLeak() {
        val text = ErrorText.describe(IllegalStateException("java.lang.NullPointerException at com.foo.Bar"))
        assertEquals(ErrorText.GENERIC, text)
        assertFalse(ErrorText.describe(ScraperException("Failed to fetch https://x.org/api?q=1")).contains("://"))
    }

    @Test fun identicalReasonsCollapse() {
        val failures = listOf("MangaDex: ${ErrorText.OFFLINE}", "Mangapill: ${ErrorText.OFFLINE}")
        assertEquals(ErrorText.OFFLINE, ErrorText.summarize(failures))
        assertEquals("A: x\nB: y", ErrorText.summarize(listOf("A: x", "B: y")))
    }
}
