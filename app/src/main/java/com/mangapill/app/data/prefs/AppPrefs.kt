package com.mangapill.app.data.prefs

import com.mangapill.app.data.model.ReaderMode
import com.mangapill.app.ui.theme.AppTheme
import android.content.Context
import com.mangapill.app.data.opds.OpdsServer
import com.mangapill.app.data.scraper.SourceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small user settings: OPDS servers, enabled sources, recent searches and reader preferences. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("mangapill_prefs", Context.MODE_PRIVATE)

    // Registered before the enabled set is loaded, so saved OPDS sources are recognised.
    private val _opdsServers = MutableStateFlow(
        OpdsServer.listFromJson(sp.getString(KEY_OPDS, null)).also {
            SourceRegistry.setOpdsServers(it)
            // Passwords saved by older versions are in clear text: write the list back once so they are encrypted.
            if (it.any { s -> s.password.isNotEmpty() } && sp.getString(KEY_OPDS, null)?.contains("\"password\":\"enc1:") != true) {
                sp.edit().putString(KEY_OPDS, OpdsServer.listToJson(it)).apply()
            }
        }
    )
    val opdsServers: StateFlow<List<OpdsServer>> = _opdsServers.asStateFlow()

    private val _enabledSources = MutableStateFlow(loadEnabled())
    val enabledSources: StateFlow<Set<String>> = _enabledSources.asStateFlow()

    private val _recentSearches = MutableStateFlow(loadRecents())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    /**
     * Sources are no longer picked in Settings: every stable built-in source is searched, plus the
     * user's OPDS servers. (A choice saved by an older version is ignored, so nobody is left with a
     * source switched off that they can no longer switch back on.)
     */
    private fun loadEnabled(): Set<String> =
        (SourceRegistry.builtIn.filterNot { it.isExperimental } + SourceRegistry.all.filterIsInstance<com.mangapill.app.data.scraper.OpdsScraper>())
            .map { it.sourceId }.toSet()

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

    private val _readerMode = MutableStateFlow(
        ReaderMode.entries.firstOrNull { it.name == sp.getString(KEY_READER_MODE, null) } ?: ReaderMode.WEBTOON
    )
    /** The reading mode last chosen; every chapter opens in it. */
    val readerMode: StateFlow<ReaderMode> = _readerMode.asStateFlow()

    fun setReaderMode(mode: ReaderMode) {
        _readerMode.value = mode
        sp.edit().putString(KEY_READER_MODE, mode.name).apply()
    }

    private val _theme = MutableStateFlow(
        AppTheme.entries.firstOrNull { it.name == sp.getString(KEY_THEME, null) } ?: AppTheme.DARK
    )
    /** Settings → Appearance → Theme (the same four as AniStream and CineStream). */
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
        sp.edit().putString(KEY_THEME, theme.name).apply()
    }

    private val _clearGlass = MutableStateFlow(sp.getBoolean(KEY_CLEAR_GLASS, false))
    /** Settings → Appearance → Transparent Glass. */
    val clearGlass: StateFlow<Boolean> = _clearGlass.asStateFlow()

    fun setClearGlass(on: Boolean) {
        _clearGlass.value = on
        sp.edit().putBoolean(KEY_CLEAR_GLASS, on).apply()
    }

    private val _keepScreenOn = MutableStateFlow(sp.getBoolean(KEY_KEEP_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    fun setKeepScreenOn(on: Boolean) {
        _keepScreenOn.value = on
        sp.edit().putBoolean(KEY_KEEP_SCREEN_ON, on).apply()
    }

    private val _tapToTurn = MutableStateFlow(sp.getBoolean(KEY_TAP_TO_TURN, true))
    /** Paged modes: tapping the left / right edge turns the page; the middle shows the controls. */
    val tapToTurn: StateFlow<Boolean> = _tapToTurn.asStateFlow()

    fun setTapToTurn(on: Boolean) {
        _tapToTurn.value = on
        sp.edit().putBoolean(KEY_TAP_TO_TURN, on).apply()
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
        const val KEY_READER_MODE = "reader_mode"
        const val KEY_THEME = "theme"
        const val KEY_CLEAR_GLASS = "clear_glass"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_TAP_TO_TURN = "tap_to_turn"
        const val MAX_RECENTS = 8
    }
}
