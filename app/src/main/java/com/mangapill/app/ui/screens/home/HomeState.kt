package com.mangapill.app.ui.screens.home

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home screen state. It is held above the NavHost so that going into a manga and coming back keeps the
 * query, the selected source, the loaded lists and the scroll position.
 */
@Stable
class HomeState(
    private val repository: MangaRepository,
    private val scope: CoroutineScope
) {
    var query by mutableStateOf("")
        private set
    /** null = all sources. */
    var source by mutableStateOf<String?>(null)
        private set
    var trending by mutableStateOf<List<Manga>>(emptyList())
        private set
    var latest by mutableStateOf<List<Manga>>(emptyList())
        private set
    var results by mutableStateOf<List<Manga>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val gridState = LazyGridState()

    val isSearching: Boolean get() = query.trim().length >= 2

    private var job: Job? = null
    private var requestId = 0
    private var started = false

    fun start() {
        if (started) return
        started = true
        load(debounce = false)
    }

    fun updateQuery(value: String) {
        query = value
        if (value.trim().length >= 2 || value.isBlank()) load(debounce = true)
    }

    fun selectSource(id: String?) {
        if (id == source) return
        source = id
        load(debounce = false)
    }

    fun retry() = load(debounce = false, force = true)

    private fun load(debounce: Boolean, force: Boolean = false) {
        job?.cancel()
        val id = ++requestId
        val q = query.trim()
        val src = source
        job = scope.launch {
            if (debounce && q.isNotEmpty()) delay(400)
            loading = true
            error = null
            try {
                if (q.length >= 2) {
                    val found = repository.search(q, src)
                    if (id == requestId) results = found
                } else {
                    val t = async { runCatching { repository.trending(src, force) } }
                    val l = async { runCatching { repository.latest(src, force) } }
                    val tr = t.await()
                    val lr = l.await()
                    if (id != requestId) return@launch
                    trending = tr.getOrDefault(emptyList())
                    latest = lr.getOrDefault(emptyList())
                    if (trending.isEmpty() && latest.isEmpty()) {
                        error = (tr.exceptionOrNull() ?: lr.exceptionOrNull())?.let { friendlyError(it) }
                            ?: "Nothing to show right now."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (id == requestId) {
                    error = friendlyError(e)
                    results = emptyList()
                }
            } finally {
                if (id == requestId) loading = false
            }
        }
    }
}
