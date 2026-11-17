package com.mangapill.app.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
import java.io.IOException
import java.net.URLEncoder

data class CloudStatus(
    /** False when the app was built without a sync server: the account section stays hidden. */
    val configured: Boolean,
    val email: String? = null,
    val syncing: Boolean = false,
    val lastSyncedAt: Long? = null,
    val error: String? = null
)

/** Where the session and the last synced state are kept (SharedPreferences in the app). */
interface KeyValueStore {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

class CloudException(message: String, val status: Int = 0) : IOException(message)

/**
 * Sign-in and sync of reading history, My List and settings between devices.
 *
 * The data lives in one Supabase table shared with AniStream and CineStream: a row per user, app
 * and collection, holding that collection as JSON. Row-level security lets a signed-in user reach
 * only their own rows, so the project's public key can ship in the app. A sync is pull -> merge
 * (see [CloudMerge]) -> push. Nothing here throws into the app: a failed sync leaves local data as
 * it was, shows in [status] and is retried on the next trigger.
 */
class CloudSync(
    url: String,
    private val publicKey: String,
    private val app: String,
    private val collections: Map<String, CollectionSpec>,
    private val store: KeyValueStore,
    private val client: OkHttpClient,
    /** The app's current data, one entry per collection. */
    private val read: suspend () -> Map<String, Any?>,
    /** Replaces the app's data with the merged result. */
    private val write: suspend (Map<String, Any?>) -> Unit
) {
    private val base = url.trimEnd('/')
    private val configured = base.startsWith("https://") && publicKey.length > 20
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var pending: Job? = null
    @Volatile private var applying = false

    private class Session(val accessToken: String, val refreshToken: String, val expiresAt: Long, val userId: String, val email: String)

    @Volatile private var session: Session? = loadSession()

    private val _status = MutableStateFlow(
        CloudStatus(configured, email = session?.email, lastSyncedAt = store.get(SYNCED)?.toLongOrNull())
    )
    val status: StateFlow<CloudStatus> = _status

    // ---- HTTP ---------------------------------------------------------------------------------

    private suspend fun request(
        path: String, method: String = "GET", body: Any? = null, token: String? = null, prefer: String? = null
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(base + path)
            .header("apikey", publicKey)
            .header("Authorization", "Bearer ${token ?: publicKey}")
        if (prefer != null) builder.header("Prefer", prefer)
        val payload = body?.toString()?.toRequestBody(JSON_TYPE)
        builder.method(method, payload ?: if (method == "POST") "".toRequestBody(JSON_TYPE) else null)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val o = runCatching { JSONObject(text) }.getOrNull()
                val message = listOf("msg", "error_description", "message", "error")
                    .firstNotNullOfOrNull { k -> o?.optString(k, "")?.takeIf { it.isNotBlank() } }
                throw CloudException(message ?: "Request failed (HTTP ${response.code})", response.code)
            }
            text
        }
    }

    // ---- session ------------------------------------------------------------------------------

    private fun loadSession(): Session? = runCatching {
        val o = JSONObject(store.get(SESSION) ?: return null)
        Session(o.getString("access"), o.getString("refresh"), o.getLong("expires"), o.getString("user"), o.optString("email"))
    }.getOrNull()

    private fun adopt(response: String): Session? {
        val o = runCatching { JSONObject(response) }.getOrNull() ?: return null
        val user = o.optJSONObject("user")
        val access = o.optString("access_token")
        val refresh = o.optString("refresh_token")
        val userId = user?.optString("id").orEmpty()
        if (access.isEmpty() || refresh.isEmpty() || userId.isEmpty()) return null
        val next = Session(access, refresh, System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000, userId, user?.optString("email").orEmpty())
        session = next
        store.set(
            SESSION,
            JSONObject().put("access", next.accessToken).put("refresh", next.refreshToken)
                .put("expires", next.expiresAt).put("user", next.userId).put("email", next.email).toString()
        )
        _status.update { it.copy(email = next.email, error = null) }
        return next
    }

    private fun forget() {
        session = null
        store.set(SESSION, null)
        store.set(BASE, null)
        store.set(SYNCED, null)
        _status.update { it.copy(email = null, lastSyncedAt = null, syncing = false) }
    }

    /** A token good for the next request, refreshed when it is about to run out. */
    private suspend fun token(): String? {
        val current = session ?: return null
        if (current.expiresAt - System.currentTimeMillis() > 60_000) return current.accessToken
        try {
            val body = JSONObject().put("refresh_token", current.refreshToken)
            return adopt(request("/auth/v1/token?grant_type=refresh_token", "POST", body))?.accessToken
        } catch (e: CloudException) {
            // The refresh token was revoked or expired: signed out. Anything else: try again later.
            if (e.status == 400 || e.status == 401 || e.status == 403) forget()
            throw e
        }
    }

    // ---- sync ---------------------------------------------------------------------------------

    private suspend fun runSync() {
        val bearer = token() ?: return
        val userId = session?.userId ?: return
        val rows = JSONArray(request("/rest/v1/user_data?app=eq.${URLEncoder.encode(app, "UTF-8")}&select=kind,data", token = bearer))
        val remote = HashMap<String, Any?>()
        for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { remote[it.optString("kind")] = it.opt("data") }

        val local = read()
        val known = runCatching { JSONObject(store.get(BASE) ?: "{}") }.getOrNull()
        val baseState = known?.takeIf { it.optString("user") == userId }?.optJSONObject("state") ?: JSONObject()
        val merged = LinkedHashMap<String, Any?>()
        for ((kind, spec) in collections) merged[kind] = CloudMerge.merge(spec, baseState.opt(kind), local[kind], remote[kind])

        if (merged.any { (kind, value) -> CloudMerge.canonical(value) != CloudMerge.canonical(local[kind]) }) {
            applying = true
            try { write(merged) } finally { applying = false }
        }
        val changed = merged.filter { (kind, value) -> value != null && CloudMerge.canonical(value) != CloudMerge.canonical(remote[kind]) }
        if (changed.isNotEmpty()) {
            val now = java.time.Instant.now().toString()
            val body = JSONArray()
            for ((kind, value) in changed) {
                body.put(JSONObject().put("user_id", userId).put("app", app).put("kind", kind).put("data", value).put("updated_at", now))
            }
            request("/rest/v1/user_data?on_conflict=user_id,app,kind", "POST", body, bearer, "resolution=merge-duplicates,return=minimal")
        }
        val state = JSONObject()
        for ((kind, value) in merged) if (value != null) state.put(kind, value)
        store.set(BASE, JSONObject().put("user", userId).put("state", state).toString())
        val at = System.currentTimeMillis()
        store.set(SYNCED, at.toString())
        _status.update { it.copy(lastSyncedAt = at, error = null) }
    }

    /** Syncs now and returns when done. Never throws: a failure shows in [status]. */
    suspend fun syncNow() {
        if (!configured || session == null) return
        lock.withLock {
            _status.update { it.copy(syncing = true) }
            try {
                runSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.update { it.copy(error = e.message ?: "Sync failed") }
            } finally {
                _status.update { it.copy(syncing = false) }
            }
        }
    }

    /** Call when local data changed: syncs a few seconds later, once, however many changes arrive. */
    fun schedule(delayMs: Long = DEBOUNCE_MS) {
        if (!configured || session == null || applying) return
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    // ---- account ------------------------------------------------------------------------------

    /** Throws [CloudException] with a message for the user when the sign-in is refused. */
    suspend fun signIn(email: String, password: String) {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        adopt(request("/auth/v1/token?grant_type=password", "POST", body)) ?: throw CloudException("Sign-in failed")
        syncNow()
    }

    /** Creates the account. True: a confirmation email was sent; sign in after opening it. */
    suspend fun signUp(email: String, password: String): Boolean {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        if (adopt(request("/auth/v1/signup", "POST", body)) == null) return true
        syncNow()
        return false
    }

    suspend fun signOut() {
        val bearer = session?.accessToken
        pending?.cancel()
        forget()
        if (bearer != null) runCatching { request("/auth/v1/logout", "POST", token = bearer) }
    }

    private companion object {
        const val SESSION = "cloud_session"
        const val BASE = "cloud_base"
        const val SYNCED = "cloud_synced_at"
        const val DEBOUNCE_MS = 4_000L
        val JSON_TYPE = "application/json".toMediaType()
    }
}
