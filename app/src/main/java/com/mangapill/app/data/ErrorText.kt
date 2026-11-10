package com.mangapill.app.data

import com.mangapill.app.data.scraper.ScraperException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * What a person should read when loading fails: plain words, never an exception class, a URL or a
 * stack. The cause chain is searched, because scrapers wrap the network error that really happened.
 */
object ErrorText {
    const val OFFLINE = "No internet connection. Check your network and try again."
    const val SLOW = "The source took too long to respond. Try again."
    const val GENERIC = "Something went wrong. Try again."

    fun describe(error: Throwable): String {
        var e: Throwable? = error
        var depth = 0
        while (e != null && depth++ < 6) {
            when (e) {
                is UnknownHostException, is ConnectException -> return OFFLINE
                is SocketTimeoutException -> return SLOW
                is SSLException -> return "Couldn't open a secure connection to the source. Try again in a moment."
            }
            e = e.cause
        }
        val msg = error.message.orEmpty()
        return when {
            msg.contains("Unable to resolve host", true) || msg.contains("Failed to connect", true) -> OFFLINE
            msg.contains("timeout", true) || msg.contains("timed out", true) -> SLOW
            msg.contains("Cloudflare", true) -> "The source is blocking the app right now. Try again later or switch source."
            msg.contains("HTTP 404") -> "This title or chapter is no longer on the source."
            msg.contains("HTTP 429") -> "The source is busy. Wait a minute and try again."
            msg.contains("HTTP 403") -> "The source refused the request. Try again later or switch source."
            Regex("HTTP 5\\d\\d").containsMatchIn(msg) -> "The source is having problems. Try again later."
            // Messages the scrapers write themselves are readable ("… does not offer search").
            error is ScraperException && msg.isNotBlank() && msg.length <= 140 && !msg.contains("://") -> msg
            error is IOException -> "Couldn't reach the source. Check your connection and try again."
            else -> GENERIC
        }
    }

    /** One line per failed source, e.g. "MangaDex: No internet connection…"; identical reasons collapse. */
    fun summarize(failures: List<String>): String {
        val distinct = failures.distinct()
        val reasons = distinct.map { it.substringAfter(": ", it) }.distinct()
        return if (reasons.size == 1 && distinct.size > 1) reasons.first() else distinct.joinToString("\n")
    }
}
