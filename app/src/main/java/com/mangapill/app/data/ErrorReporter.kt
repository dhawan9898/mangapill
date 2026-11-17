package com.mangapill.app.data

import com.mangapill.app.data.sync.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Sends what goes wrong in the app to the app_errors table shared with AniStream and CineStream
 * (supabase/errors.sql in those repositories), so failures seen on the phone can be looked at and
 * fixed: crashes, source failures, chapters that cannot be opened, images that do not load,
 * failed downloads, sync and update problems.
 *
 * Reporting must never make things worse, so: [report] never throws and only queues; the same error
 * is sent once per app session (later sightings are counted); a session sends at most
 * [MAX_PER_SESSION] reports; reports wait in storage while offline; URLs lose their query strings
 * and secrets are removed from the context. Until [configure] is called nothing is sent.
 */
object ErrorReporter {
    private const val APP = "mangapill"
    private const val MAX_PER_SESSION = 150
    private const val MAX_QUEUED = 60
    private const val QUEUE_KEY = "error_queue"
    private val JSON_TYPE = "application/json".toMediaType()

    private class Config(
        val url: String, val key: String, val version: String, val device: String,
        val store: KeyValueStore, val token: () -> String?, val client: OkHttpClient
    )

    @Volatile private var config: Config? = null
    private val lock = Any()
    private val queue = ArrayList<JSONObject>()
    private val seen = HashMap<String, JSONObject>()
    private var sentThisSession = 0
    private val sessionId = UUID.randomUUID().toString().replace("-", "").take(20)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sending = Mutex()
    private var flushJob: Job? = null

    fun configure(
        url: String, key: String, version: String, device: String, store: KeyValueStore,
        token: () -> String?, client: OkHttpClient
    ) {
        val c = Config(url.trimEnd('/'), key, version, device, store, token, client.newBuilder()
            .callTimeout(15, TimeUnit.SECONDS).build())
        synchronized(lock) {
            config = c
            runCatching {
                val saved = JSONArray(c.store.get(QUEUE_KEY) ?: "[]")
                val restored = (0 until saved.length()).mapNotNull { saved.optJSONObject(it) }
                queue.addAll(0, restored)
                while (queue.size > MAX_QUEUED) queue.removeAt(0)
            }
        }
        if (queue.isNotEmpty()) schedule(3_000)
    }

    /** Removes query strings and fragments from every URL in a text (keys and tokens live there). */
    fun scrubUrls(text: String): String = Regex("""(https?://[^\s?#"'<>]+)[?#][^\s"'<>]*""").replace(text) { it.groupValues[1] }

    /** Same error, same fingerprint: digits, ids and URLs are blurred so variants group together. */
    fun fingerprint(kind: String, message: String, stack: String?): String {
        val normal = message
            .replace(Regex("""https?://([^/\s]+)\S*"""), "$1")
            .replace(Regex("""\b[0-9a-fA-F]{8,}\b"""), "#")
            .replace(Regex("""\d+"""), "#")
            .take(300)
        val frame = stack?.lineSequence()?.map { it.trim() }?.firstOrNull { it.startsWith("at com.mangapill") }
            ?.replace(Regex(""":\d+\)$"""), ")").orEmpty()
        var h = 5381
        for (ch in "$kind|$normal|$frame") h = (h shl 5) + h + ch.code
        return "$kind:" + Integer.toUnsignedString(h, 36)
    }

    private fun clip(text: String, max: Int) = if (text.length > max) text.take(max - 1) + "…" else text

    private fun clean(value: Any?, depth: Int = 0): Any? = when (value) {
        null -> JSONObject.NULL
        is Number, is Boolean -> value
        is String -> clip(scrubUrls(value), 500)
        is Throwable -> clip(scrubUrls(value.message ?: value.javaClass.simpleName), 500)
        is Map<*, *> -> if (depth > 3) "…" else JSONObject().also { o ->
            value.entries.take(40).forEach { (k, v) ->
                val name = k.toString()
                o.put(name, if (Regex("token|password|secret|authorization|apikey|cookie", RegexOption.IGNORE_CASE).containsMatchIn(name)) "[removed]" else clean(v, depth + 1))
            }
        }
        is Collection<*> -> if (depth > 3) "…" else JSONArray(value.take(30).map { clean(it, depth + 1) })
        else -> clip(value.toString(), 500)
    }

    private fun persist() {
        val c = config ?: return
        val text = synchronized(lock) { if (queue.isEmpty()) null else JSONArray(queue.takeLast(MAX_QUEUED)).toString() }
        runCatching { c.store.set(QUEUE_KEY, text) }
    }

    /**
     * Records a failure. [context] says what was being done (source, title, chapter...); it is
     * cleaned of query strings and secrets before it leaves the device. Never throws.
     */
    fun report(kind: String, message: String, error: Throwable? = null, context: Map<String, Any?> = emptyMap()) {
        try {
            val c = config ?: return
            val text = clip(scrubUrls(message.ifBlank { error?.toString() ?: "Unknown error" }), 2000)
            val stack = error?.let { StringWriter().also { w -> it.printStackTrace(PrintWriter(w)) }.toString() }
            val print = fingerprint(kind, text, stack)
            synchronized(lock) {
                seen[print]?.let { earlier ->
                    earlier.put("occurrences", (earlier.optInt("occurrences", 1) + 1).coerceAtMost(100_000))
                    if (queue.contains(earlier)) persist()
                    return
                }
                if (sentThisSession >= MAX_PER_SESSION) return
                sentThisSession++
                val row = JSONObject()
                    .put("occurred_at", Instant.now().toString())
                    .put("app", APP)
                    .put("app_version", c.version)
                    .put("device", clip(c.device, 200))
                    .put("session_id", sessionId)
                    .put("kind", kind)
                    .put("message", text)
                    .put("fingerprint", print)
                    .put("occurrences", 1)
                if (stack != null) row.put("stack", clip(scrubUrls(stack), 20_000))
                if (context.isNotEmpty()) row.put("context", clean(context))
                seen[print] = row
                queue.add(row)
                while (queue.size > MAX_QUEUED) queue.removeAt(0)
            }
            persist()
            schedule(if (kind == "crash") 1_000 else 5_000)
        } catch (_: Throwable) {
            // reporting must never break the app
        }
    }

    private fun schedule(delayMs: Long) {
        if (config == null) return
        synchronized(lock) {
            if (flushJob?.isActive == true) return
            flushJob = scope.launch {
                delay(delayMs)
                flush()
            }
        }
    }

    /** Sends what is queued. Never throws; reports that cannot be sent now stay queued. */
    suspend fun flush() {
        val c = config ?: return
        if (!c.url.startsWith("https://") || c.key.length < 20) return
        sending.withLock {
            val batch = synchronized(lock) { queue.take(25) }
            if (batch.isEmpty()) return
            val keep = withContext(Dispatchers.IO) {
                runCatching {
                    val token = runCatching { c.token() }.getOrNull()
                    val request = Request.Builder()
                        .url("${c.url}/rest/v1/app_errors")
                        .header("apikey", c.key)
                        .header("Authorization", "Bearer ${token ?: c.key}")
                        .header("Prefer", "return=minimal")
                        .post(JSONArray(batch).toString().toRequestBody(JSON_TYPE))
                        .build()
                    c.client.newCall(request).execute().use { r ->
                        // Sent, or refused for good (a 4xx other than an expired token): drop them,
                        // so one bad report cannot block the rest forever.
                        !(r.isSuccessful || (r.code in 400..499 && r.code != 401))
                    }
                }.getOrDefault(true)
            }
            if (!keep) {
                synchronized(lock) { queue.removeAll(batch.toSet()) }
                persist()
            }
            if (synchronized(lock) { queue.isNotEmpty() }) schedule(if (keep) 30_000 else 1_000)
        }
    }

    /** For tests. */
    internal fun resetForTests() {
        synchronized(lock) {
            config = null
            queue.clear()
            seen.clear()
            sentThisSession = 0
            flushJob?.cancel()
            flushJob = null
        }
    }
}
