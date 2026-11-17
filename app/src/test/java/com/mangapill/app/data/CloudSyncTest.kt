package com.mangapill.app.data

import com.mangapill.app.data.sync.CloudException
import com.mangapill.app.data.sync.CloudMerge
import com.mangapill.app.data.sync.CloudSync
import com.mangapill.app.data.sync.CollectionSpec
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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CloudSyncTest {
    private val history = CollectionSpec.Records({ it.optString("id") }, { it.optLong("at") })
    private val list = CollectionSpec.Records({ it.optString("id") })
    private fun arr(json: String) = JSONArray(json)
    private fun merged(spec: CollectionSpec, base: String?, local: String, remote: String?) =
        CloudMerge.canonical(CloudMerge.merge(spec, base?.let(::arr), arr(local), remote?.let(::arr)))
    private fun same(expected: String, actual: String) = assertEquals(CloudMerge.canonical(arr(expected)), actual)

    @Test fun nothingInTheCloudKeepsLocalData() = same("""[{"id":"1","at":5}]""", merged(history, null, """[{"id":"1","at":5}]""", null))

    @Test fun firstSyncCombinesBothSidesNewestFirst() = same(
        """[{"id":"2","at":9,"p":"local"},{"id":"3","at":7},{"id":"1","at":5}]""",
        merged(history, null, """[{"id":"1","at":5},{"id":"2","at":9,"p":"local"}]""", """[{"id":"2","at":4,"p":"cloud"},{"id":"3","at":7}]""")
    )

    @Test fun theNewerProgressWins() {
        val base = """[{"id":"1","at":1,"p":10}]"""
        same("""[{"id":"1","at":8,"p":90}]""", merged(history, base, base, """[{"id":"1","at":8,"p":90}]"""))
        same("""[{"id":"1","at":9,"p":95}]""", merged(history, base, """[{"id":"1","at":9,"p":95}]""", """[{"id":"1","at":8,"p":90}]"""))
    }

    @Test fun aRemovalCarriesOverButNotOverNewerProgress() {
        val base = """[{"id":"1"},{"id":"2"}]"""
        same("""[{"id":"1"}]""", merged(list, base, """[{"id":"1"}]""", base))
        same("""[{"id":"1"}]""", merged(list, base, base, """[{"id":"1"}]"""))
        same("""[{"id":"1","at":6}]""", merged(history, """[{"id":"1","at":1}]""", """[{"id":"1","at":6}]""", "[]"))
    }

    @Test fun settingsAreReplacedAsAWhole() {
        val blob = CollectionSpec.Blob
        fun m(base: String?, local: String, remote: String) =
            CloudMerge.canonical(CloudMerge.merge(blob, base?.let { JSONObject(it) }, JSONObject(local), JSONObject(remote)))
        assertEquals(CloudMerge.canonical(JSONObject("""{"t":"b"}""")), m(null, """{"t":"a"}""", """{"t":"b"}"""))
        assertEquals(CloudMerge.canonical(JSONObject("""{"t":"b"}""")), m("""{"t":"a"}""", """{"t":"a"}""", """{"t":"b"}"""))
        assertEquals(CloudMerge.canonical(JSONObject("""{"t":"c"}""")), m("""{"t":"a"}""", """{"t":"c"}""", """{"t":"b"}"""))
    }

    @Test fun numbersCompareEqualWhateverTheirType() =
        assertEquals(CloudMerge.canonical(JSONObject("""{"n":5,"m":1.5}""")), CloudMerge.canonical(JSONObject().put("m", 1.5f.toDouble()).put("n", 5.0)))

    /** A stand-in for the Supabase endpoints the client uses. */
    private class FakeServer : Interceptor {
        val rows = LinkedHashMap<String, Any>()
        val calls = ArrayList<String>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val path = request.url.encodedPath
            calls += "${request.method} $path"
            val body = request.body?.let { Buffer().also(it::writeTo).readUtf8() }.orEmpty()
            fun answer(code: Int, text: String) = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
                .message("x").body(text.toResponseBody("application/json".toMediaType())).build()
            return when {
                path.startsWith("/auth/v1/token") ->
                    if (body.contains("\"wrong\"")) answer(400, """{"msg":"Invalid login credentials"}""")
                    else answer(200, """{"access_token":"tok","refresh_token":"ref","expires_in":3600,"user":{"id":"u1","email":"a@b.c"}}""")
                path.startsWith("/rest/v1/user_data") && request.method == "GET" ->
                    answer(200, JSONArray(rows.map { (k, v) -> JSONObject().put("kind", k).put("data", v) }).toString())
                path.startsWith("/rest/v1/user_data") -> {
                    val posted = JSONArray(body)
                    for (i in 0 until posted.length()) posted.getJSONObject(i).let { rows[it.getString("kind")] = it.get("data") }
                    answer(201, "")
                }
                else -> answer(404, "{}")
            }
        }
    }

    private class Device(server: FakeServer, var history: JSONArray, var bookmarks: JSONArray, specs: Map<String, CollectionSpec>) {
        private val kv = HashMap<String, String>()
        val sync = CloudSync(
            "https://project.supabase.test", "k".repeat(40), "mangapill", specs,
            object : KeyValueStore {
                override fun get(key: String) = kv[key]
                override fun set(key: String, value: String?) { if (value == null) kv.remove(key) else kv[key] = value }
            },
            OkHttpClient.Builder().addInterceptor(server).build(),
            read = { mapOf("history" to JSONArray(history.toString()), "bookmarks" to JSONArray(bookmarks.toString())) },
            write = { state -> history = state["history"] as JSONArray; bookmarks = state["bookmarks"] as JSONArray }
        )
    }

    @Test fun twoDevicesOnOneAccountEndUpWithTheSameData() = runBlocking {
        val server = FakeServer()
        val specs = mapOf("history" to history, "bookmarks" to list)
        val phone = Device(server, arr("""[{"id":"1","at":5}]"""), arr("""[{"id":"a"}]"""), specs)
        val tablet = Device(server, arr("""[{"id":"2","at":7}]"""), arr("[]"), specs)
        phone.sync.signIn("a@b.c", "right")
        tablet.sync.signIn("a@b.c", "right")
        same("""[{"id":"2","at":7},{"id":"1","at":5}]""", CloudMerge.canonical(tablet.history))
        same("""[{"id":"a"}]""", CloudMerge.canonical(tablet.bookmarks))
        phone.sync.syncNow()
        assertEquals(CloudMerge.canonical(tablet.history), CloudMerge.canonical(phone.history))

        // Removed on the phone, then synced on both: gone on the tablet too.
        phone.bookmarks = arr("[]")
        phone.sync.syncNow()
        tablet.sync.syncNow()
        same("[]", CloudMerge.canonical(tablet.bookmarks))
        assertNull(phone.sync.status.value.error)
        assertEquals("a@b.c", phone.sync.status.value.email)
    }

    @Test fun aWrongPasswordIsReportedAndNothingIsSynced() = runBlocking {
        val server = FakeServer()
        val phone = Device(server, arr("[]"), arr("[]"), mapOf("history" to history, "bookmarks" to list))
        try {
            phone.sync.signIn("a@b.c", "wrong")
            fail("sign-in should have been refused")
        } catch (e: CloudException) {
            assertEquals("Invalid login credentials", e.message)
        }
        assertNull(phone.sync.status.value.email)
        assertTrue(server.calls.none { it.contains("user_data") })
    }
}
