package com.mangapill.app.data.opds

import com.mangapill.app.data.SecretBox
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A user-configured OPDS catalog: a self-hosted comic server (Komga, Kavita, Suwayomi, Stump, Codex,
 * Calibre-Web, Ubooquity...) or any public catalog. The URL is the catalog root feed.
 */
data class OpdsServer(
    val id: String,
    val name: String,
    val url: String,
    val username: String = "",
    val password: String = ""
) {
    val sourceId: String get() = SOURCE_PREFIX + id

    val hasCredentials: Boolean get() = username.isNotEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("url", url)
        .put("username", username).put("password", SecretBox.seal(password))

    companion object {
        const val SOURCE_PREFIX = "opds-"

        fun fromJson(o: JSONObject) = OpdsServer(
            id = o.getString("id"),
            name = o.optString("name"),
            url = o.getString("url"),
            username = o.optString("username"),
            password = SecretBox.open(o.optString("password"))
        )

        fun listToJson(list: List<OpdsServer>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(text: String?): List<OpdsServer> {
            if (text.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(text)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }

        /** Adds a scheme when the user typed a bare host, and drops trailing whitespace. */
        fun normalizeUrl(raw: String): String {
            val t = raw.trim()
            return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
        }
    }
}

/**
 * Adds HTTP Basic credentials to requests aimed at a configured server, and only to those: the
 * header is attached when scheme, host and port match the server's catalog URL, so credentials never
 * leak to a CDN or any other site. Installed on the shared client, so the image loader (covers and
 * streamed pages) authenticates the same way as the provider.
 */
object OpdsAuthInterceptor : Interceptor {
    private class Origin(val scheme: String, val host: String, val port: Int, val header: String)

    private val origins = CopyOnWriteArrayList<Origin>()

    fun update(servers: List<OpdsServer>) {
        val next = servers.filter { it.hasCredentials }.mapNotNull { s ->
            val url = s.url.toHttpUrlOrNull() ?: return@mapNotNull null
            Origin(url.scheme, url.host, url.port, Credentials.basic(s.username, s.password, Charsets.UTF_8))
        }
        origins.clear()
        origins.addAll(next)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null || origins.isEmpty()) return chain.proceed(request)
        val url = request.url
        val match = origins.firstOrNull { it.scheme == url.scheme && it.host == url.host && it.port == url.port }
            ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", match.header).build())
    }
}
