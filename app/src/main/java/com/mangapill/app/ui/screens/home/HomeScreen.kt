package com.mangapill.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Genres
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.repository.HistoryEntry
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.MangaPosterCard
import com.mangapill.app.ui.components.PosterSkeleton
import com.mangapill.app.ui.components.SectionHeader
import com.mangapill.app.ui.components.StateMessage
import com.mangapill.app.ui.components.bleed
import com.mangapill.app.ui.components.Wordmark
import com.mangapill.app.ui.components.glass.GlassIconButton
import androidx.compose.material.icons.outlined.Search
import com.mangapill.app.ui.components.glass.GlassChip
import com.mangapill.app.ui.components.manga.IntroCardSheet
import com.mangapill.app.ui.navigation.bottomContentPadding
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted

internal val FullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/**
 * Home: the wordmark header with a search button (which opens the Search tab, as in AniStream),
 * the source chips and the dashboard rows. Search, filters and results live on the Search tab.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    repository: MangaRepository,
    onMangaSelect: (Manga) -> Unit,
    onReadChapter: (Manga, Chapter) -> Unit,
    onContinue: (HistoryEntry) -> Unit,
    onOpenSearch: () -> Unit
) {
    LaunchedEffect(Unit) { state.start() }

    val enabled by repository.prefs.enabledSources.collectAsState()
    LaunchedEffect(enabled) { state.onEnabledSourcesChanged(enabled) }

    val history by remember { repository.historyEntries() }.collectAsState(initial = emptyList())
    val favorites by remember { repository.bookmarkedManga() }.collectAsState(initial = emptyList())
    LaunchedEffect(history.firstOrNull()?.manga?.key) { state.refreshRecommendations(history.firstOrNull()?.manga?.key) }

    var peek by remember { mutableStateOf<Manga?>(null) }
    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 5 else 2
    val sources = remember(enabled) { repository.sources.filter { it.id in enabled } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Wordmark(Modifier.weight(1f))
            GlassIconButton(Icons.Outlined.Search, "Search", onOpenSearch)
        }
        SourceChipsRow(sources, state.source, state::selectSource)
        Dashboard(state, history, favorites, columns, onMangaSelect, { peek = it }, onContinue)
    }

    peek?.let { m ->
        IntroCardSheet(
            manga = m,
            repository = repository,
            onDismiss = { peek = null },
            onOpenDetails = {
                peek = null
                onMangaSelect(m)
            },
            onChapter = { ch ->
                peek = null
                onReadChapter(m, ch)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dashboard(
    state: HomeState,
    history: List<HistoryEntry>,
    favorites: List<Manga>,
    columns: Int,
    onOpen: (Manga) -> Unit,
    onPeek: (Manga) -> Unit,
    onContinue: (HistoryEntry) -> Unit
) {
    val trending = state.trending
    val hero = trending.take(5)
    val trendingRow = if (trending.size > 8) trending.drop(5) else trending
    val latestKeys = remember(state.latest) { state.latest.map { it.key }.toSet() }
    val updatedFavorites = remember(favorites, latestKeys) { favorites.filter { it.key in latestKeys } }
    val nothingLoaded = trending.isEmpty() && state.latest.isEmpty()

    LazyVerticalGrid(
        state = state.dashboardGrid,
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomContentPadding()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (hero.isNotEmpty()) {
            item(key = "hero", span = FullSpan) { FeaturedCarousel(hero, onOpen, onPeek, Modifier.bleed(16.dp)) }
        } else if (state.dashboardLoading) {
            item(key = "hero-skeleton", span = FullSpan) { FeaturedSkeleton(Modifier.bleed(16.dp)) }
        }

        if (history.isNotEmpty()) {
            item(key = "continue", span = FullSpan) { ContinueReadingRow(history.take(10), onContinue) }
        }

        state.because?.let { rec ->
            item(key = "because", span = FullSpan) {
                MangaCarousel("Because you read ${rec.seed.title}", "More ${rec.genre} picked for you", rec.items, onOpen, onPeek)
            }
        }

        if (trendingRow.isNotEmpty()) {
            item(key = "trending", span = FullSpan) {
                MangaCarousel("Trending today", "Most followed right now", trendingRow, onOpen, onPeek)
            }
        }

        if (updatedFavorites.isNotEmpty()) {
            item(key = "favorites", span = FullSpan) {
                MangaCarousel("Recently updated favorites", "New chapters in your library", updatedFavorites, onOpen, onPeek)
            }
        }

        if (state.hiddenGems.isNotEmpty()) {
            item(key = "gems", span = FullSpan) {
                MangaCarousel("Hidden gems", "Highly rated, still under the radar", state.hiddenGems, onOpen, onPeek)
            }
        }

        when {
            state.latest.isNotEmpty() -> {
                item(key = "latest-header", span = FullSpan) {
                    SectionHeader("Latest updates", subtitle = "Fresh chapters across your sources", modifier = Modifier.padding(bottom = 0.dp))
                }
                items(state.latest, key = { "latest:" + it.key }) { m ->
                    MangaPosterCard(m, onClick = { onOpen(m) }, modifier = Modifier.animateItemPlacement(), onLongClick = { onPeek(m) })
                }
            }

            state.dashboardLoading -> items(6, key = { "skeleton$it" }) { PosterSkeleton() }

            nothingLoaded && state.dashboardError != null -> item(key = "error", span = FullSpan) {
                StateMessage(
                    icon = Icons.Default.CloudOff,
                    title = "Couldn't load manga",
                    message = state.dashboardError.orEmpty(),
                    actionLabel = "Try again",
                    onAction = state::retry
                )
            }
        }
    }
}

