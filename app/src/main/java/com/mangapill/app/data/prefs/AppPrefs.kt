package com.mangapill.app.data.prefs

import android.content.Context
import com.mangapill.app.data.opds.OpdsServer
import com.mangapill.app.data.scraper.SourceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small user settings: OPDS servers, which sources are enabled and recent searches. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("mangapill_prefs", Context.MODE_PRIVATE)

    // Registered before the enabled set is loaded, so saved OPDS sources are recognised.
    private val _opdsServers = MutableStateFlow(
        OpdsServer.listFromJson(sp.getString(KEY_OPDS, null)).also { SourceRegistry.setOpdsServers(it) }
    )
    val opdsServers: StateFlow<List<OpdsServer>> = _opdsServers.asStateFlow()

    private val _enabledSources = MutableStateFlow(loadEnabled())
    val enabledSources: StateFlow<Set<String>> = _enabledSources.asStateFlow()

    private val _recentSearches = MutableStateFlow(loadRecents())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private fun loadEnabled(): Set<String> {
        val known = SourceRegistry.all.map { it.sourceId }.toSet()
        val saved = sp.getStringSet(KEY_ENABLED, null)?.intersect(known)
        if (!saved.isNullOrEmpty()) return saved
        // Only sources that are known to work are on by default; experimental ones are opt-in.
        return SourceRegistry.all.filterNot { it.isExperimental }.map { it.sourceId }.toSet()
    }

    /** Returns false (and changes nothing) when asked to disable the last enabled source. */
    fun setSourceEnabled(id: String, enabled: Boolean): Boolean {
        val next = if (enabled) _enabledSources.value + id else _enabledSources.value - id
        if (next.isEmpty()) return false
        _enabledSources.value = next
        sp.edit().putStringSet(KEY_ENABLED, next).apply()
        return true
    }

    /** Adds or replaces (same id) a server and enables it. */
    fun saveOpdsServer(server: OpdsServer) {
        val next = _opdsServers.value.filterNot { it.id == server.id } + server
        storeServers(next)
        setSourceEnabled(server.sourceId, true)
    }

    fun removeOpdsServer(id: String) {
        val removed = _opdsServers.value.firstOrNull { it.id == id } ?: return
        storeServers(_opdsServers.value - removed)
        val nextEnabled = _enabledSources.value - removed.sourceId
        // Never leave zero sources enabled: fall back to the stable built-ins.
        val safe = nextEnabled.ifEmpty { SourceRegistry.builtIn.filterNot { it.isExperimental }.map { it.sourceId }.toSet() }
        _enabledSources.value = safe
        sp.edit().putStringSet(KEY_ENABLED, safe).apply()
    }

    private fun storeServers(list: List<OpdsServer>) {
        SourceRegistry.setOpdsServers(list)
        _opdsServers.value = list
        sp.edit().putString(KEY_OPDS, OpdsServer.listToJson(list)).apply()
    }

    private fun loadRecents(): List<String> =
        sp.getString(KEY_RECENTS, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val next = (listOf(q) + _recentSearches.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_RECENTS)
        _recentSearches.value = next
        sp.edit().putString(KEY_RECENTS, next.joinToString("\n")).apply()
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        sp.edit().remove(KEY_RECENTS).apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled_sources"
        const val KEY_RECENTS = "recent_searches"
        const val KEY_OPDS = "opds_servers"
        const val MAX_RECENTS = 8
    }
}
