package com.mangapill.app.ui.screens.home

import com.mangapill.app.data.ErrorText
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.SearchFilters
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.data.repository.Recommendation
import com.mangapill.app.data.repository.SourceResults
import com.mangapill.app.ui.components.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Home screen state. It is held above the NavHost so that going into a manga and coming back keeps the
 * query, filters, the selected source, the loaded rows and the scroll position.
 */
@Stable
class HomeState(
    private val repository: MangaRepository,
    private val scope: CoroutineScope
) {
    // ---- Inputs ---------------------------------------------------------------------------------
    var query by mutableStateOf("")
        private set
    var type by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var genre by mutableStateOf<String?>(null)
        private set
    /** null = all enabled sources. */
    var source by mutableStateOf<String?>(null)
        private set
    /** True once the user pressed search / "see all": the suggestion panel closes and the grid takes over. */
    var submitted by mutableStateOf(false)
        private set

    // ---- Search ---------------------------------------------------------------------------------
    var results by mutableStateOf<List<Manga>>(emptyList())
        private set
    var searchPending by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    // ---- Dashboard ------------------------------------------------------------------------------
    var trending by mutableStateOf<List<Manga>>(emptyList())
        private set
    var latest by mutableStateOf<List<Manga>>(emptyList())
        private set
    var hiddenGems by mutableStateOf<List<Manga>>(emptyList())
        private set
    var because by mutableStateOf<Recommendation?>(null)
        private set
    var dashboardLoading by mutableStateOf(false)
        private set
    var dashboardError by mutableStateOf<String?>(null)
        private set

    val resultsGrid = LazyGridState()
    val dashboardGrid = LazyGridState()

    val filters: SearchFilters get() = SearchFilters(query.trim(), type, genre, status)
    val activeFilterCount: Int get() = listOfNotNull(type, status, genre).size
    val isSearching: Boolean get() = query.trim().length >= 2 || filters.hasFilters

    private var searchJob: Job? = null
    private var dashboardJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        loadDashboard(force = false)
    }

    fun updateQuery(value: String) {
        query = value
        submitted = false
        runSearch(debounce = true)
    }

    fun submit() {
        submitted = true
        if (query.isNotBlank()) repository.prefs.addRecentSearch(query)
    }

    fun searchFor(text: String) {
        query = text
        submitted = true
        repository.prefs.addRecentSearch(text)
        runSearch(debounce = false)
    }

    fun selectType(value: String?) { if (type != value) { type = value; runSearch(false) } }
    fun selectStatus(value: String?) { if (status != value) { status = value; runSearch(false) } }
    fun selectGenre(value: String?) { if (genre != value) { genre = value; runSearch(false) } }

    fun clearFilters() {
        if (activeFilterCount == 0) return
        type = null; status = null; genre = null
        runSearch(false)
    }

    fun selectSource(id: String?) {
        if (id == source) return
        source = id
        loadDashboard(force = false)
        runSearch(debounce = false)
    }

    /** Called when the user enables/disables sources in Settings. */
    fun onEnabledSourcesChanged(enabled: Set<String>) {
        val previous = knownEnabled
        knownEnabled = enabled
        if (!started || previous == null || previous == enabled) return
        if (source != null && source !in enabled) source = null
        loadDashboard(force = false)
        runSearch(debounce = false)
    }

    private var recommendationJob: Job? = null
    private var knownEnabled: Set<String>? = null
    private var lastHistoryKey: String? = UNSET

    /** Re-computes "Because you read X" when the most recently read title changes. */
    fun refreshRecommendations(latestHistoryKey: String?) {
        val previous = lastHistoryKey
        lastHistoryKey = latestHistoryKey
        // The first call only records the state; the dashboard load already computes the row.
        if (!started || previous == UNSET || previous == latestHistoryKey) return
        recommendationJob?.cancel()
        val src = source
        recommendationJob = scope.launch {
            runCatching { repository.becauseYouRead(src) }.getOrNull()?.let { because = it }
        }
    }

    fun retry() {
        loadDashboard(force = true)
        runSearch(debounce = false)
    }

    private fun runSearch(debounce: Boolean) {
        searchJob?.cancel()
        if (!isSearching) {
            results = emptyList()
            searchPending = false
            searchError = null
            return
        }
        val f = filters
        val src = source
        searchJob = scope.launch {
            if (debounce) delay(300)
            searchPending = true
            searchError = null
            var first = true
            collectSafely(repository.searchStream(f, src)) { r ->
                // Keep the previous results on screen until the new query has something to show.
                if (r.items.isNotEmpty() || r.done || !first) {
                    results = r.items
                    if (r.items.isNotEmpty()) first = false
                }
                searchPending = !r.done
                if (r.done && r.items.isEmpty() && r.failures.isNotEmpty()) searchError = ErrorText.summarize(r.failures)
            }.onFailure { searchError = friendlyError(it) }
            searchPending = false
        }
    }

    private fun loadDashboard(force: Boolean) {
        dashboardJob?.cancel()
        val src = source
        dashboardJob = scope.launch {
            dashboardLoading = true
            dashboardError = null
            var failures = emptyList<String>()
            val rows = listOf(
                launch { collectSafely(repository.trendingStream(src, force)) { trending = it.items; failures = failures + it.failures } },
                launch { collectSafely(repository.latestStream(src, force)) { latest = it.items; failures = failures + it.failures } }
            )
            launch { hiddenGems = runCatching { repository.hiddenGems(src) }.getOrDefault(emptyList()) }
            launch { because = runCatching { repository.becauseYouRead(src) }.getOrNull() }
            rows.joinAll()
            dashboardLoading = false
            if (trending.isEmpty() && latest.isEmpty()) {
                dashboardError = ErrorText.summarize(failures).ifBlank { "Nothing to show right now." }
            }
        }
    }

    /** Collects [flow], turning a thrown error into a failed Result instead of crashing the screen. */
    private suspend fun collectSafely(flow: Flow<SourceResults>, onEach: (SourceResults) -> Unit): Result<Unit> =
        try {
            flow.collect { onEach(it) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private companion object {
        const val UNSET = "\u0000unset"
    }
}
