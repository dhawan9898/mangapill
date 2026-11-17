package com.mangapill.app.data

import com.mangapill.app.data.sync.KeyValueStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ErrorReporterTest {
    private val kv = HashMap<String, String>()
    private val sent = ArrayList<JSONArray>()
    private val auth = ArrayList<String?>()
    private var online = true

    private val server = Interceptor { chain ->
        if (!online) throw IOException("offline")
        val request = chain.request()
        sent += JSONArray(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        auth += request.header("Authorization")
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(201).message("Created")
            .body("".toResponseBody("application/json".toMediaType())).build()
    }

    private fun setup(token: String? = "user-token") = ErrorReporter.configure(
        "https://project.supabase.test", "k".repeat(40), "1.0.20", "Android 17 (samsung SM-S928U1)",
        object : KeyValueStore {
            override fun get(key: String) = kv[key]
            override fun set(key: String, value: String?) { if (value == null) kv.remove(key) else kv[key] = value }
        },
        { token }, OkHttpClient.Builder().addInterceptor(server).build()
    )

    @After fun reset() = ErrorReporter.resetForTests()

    @Test fun urlsLoseTheirQueryAndSecretsAreRemoved() = runBlocking {
        assertEquals("GET https://api.mangadex.org/manga/1 failed", ErrorReporter.scrubUrls("GET https://api.mangadex.org/manga/1?token=abc&x=1 failed"))
        setup()
        ErrorReporter.report("source", "MangaDex: timeout https://api.mangadex.org/x?key=SECRET", IOException("x"),
            mapOf("url" to "https://h/y?sig=SECRET", "password" to "p", "nested" to mapOf("authorization" to "Bearer z", "ok" to 1)))
        ErrorReporter.flush()
        val body = sent.single().toString()
        assertFalse(body, body.contains("SECRET") || body.contains("Bearer z") || body.contains("\"p\""))
        assertEquals("MangaDex: timeout https://api.mangadex.org/x", sent.single().getJSONObject(0).getString("message"))
    }

    @Test fun theSameErrorIsSentOncePerSessionAndCounted() = runBlocking {
        setup()
        repeat(4) { ErrorReporter.report("image", "Image failed from cdn.example: HTTP ${500 + it}") }
        ErrorReporter.report("download", "Chapter download failed: disk full")
        ErrorReporter.flush()
        val rows = sent.single()
        assertEquals(2, rows.length())
        assertEquals(4, rows.getJSONObject(0).getInt("occurrences"))
        assertEquals("mangapill", rows.getJSONObject(0).getString("app"))
        assertEquals("Bearer user-token", auth.single())
    }

    @Test fun reportsWaitWhileOfflineAndGoOutLater() = runBlocking {
        online = false
        setup()
        ErrorReporter.report("crash", "java.lang.IllegalStateException: boom", IllegalStateException("boom"))
        ErrorReporter.flush()
        assertTrue(sent.isEmpty())
        assertTrue("kept for the next start", kv["error_queue"]!!.contains("boom"))
        online = true
        ErrorReporter.flush()
        assertEquals("crash", sent.single().getJSONObject(0).getString("kind"))
        assertTrue(sent.single().getJSONObject(0).getString("stack").contains("IllegalStateException"))
        assertNull("queue emptied once sent", kv["error_queue"])
    }

    @Test fun fingerprintsGroupVariants() {
        assertEquals(ErrorReporter.fingerprint("image", "Image failed from https://a.com/p/1.jpg?x=1", null), ErrorReporter.fingerprint("image", "Image failed from https://a.com/p/2.jpg", null))
        assertNotEquals(ErrorReporter.fingerprint("image", "failed from https://a.com/1", null), ErrorReporter.fingerprint("image", "failed from https://b.com/1", null))
    }

    @Test fun nothingHappensBeforeConfigure() {
        ErrorReporter.report("error", "before setup")
        assertTrue(sent.isEmpty())
    }
}
