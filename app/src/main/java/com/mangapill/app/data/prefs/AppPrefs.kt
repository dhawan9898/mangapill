package com.mangapill.app.data.prefs

import android.content.Context
import com.mangapill.app.data.scraper.SourceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small user settings: which sources are enabled and recent searches. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("mangapill_prefs", Context.MODE_PRIVATE)

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
        const val MAX_RECENTS = 8
    }
}
