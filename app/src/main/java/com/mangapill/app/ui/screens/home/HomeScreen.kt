package com.mangapill.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.repository.HistoryEntry
import com.mangapill.app.data.repository.HomeRail
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.StateMessage
import com.mangapill.app.ui.components.Wordmark
import androidx.compose.ui.platform.LocalContext
import com.mangapill.app.MangapillApp
import com.mangapill.app.ui.screens.settings.AccountButton
import com.mangapill.app.ui.components.glass.GlassIconButton
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.components.manga.IntroCardSheet
import com.mangapill.app.ui.navigation.bottomContentPadding
import com.mangapill.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Home, laid out like AniStream's and CineStream's: the wordmark with Categories and Search, a
 * billboard carousel of the most popular titles, Continue Reading, a Top 10, and rails of
 * MangaDex lists (latest, top rated, genres, manhwa, completed). Each rail loads when it scrolls
 * into view and stays loaded, so coming back to Home is instant.
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

    val history by remember { repository.historyEntries() }.collectAsState(initial = emptyList())
    val favorites by remember { repository.bookmarkedManga() }.collectAsState(initial = emptyList())
    LaunchedEffect(history.firstOrNull()?.manga?.key) { state.refreshRecommendations(history.firstOrNull()?.manga?.key) }
    val savedKeys = remember(favorites) { favorites.map { it.key }.toSet() }

    var peek by remember { mutableStateOf<Manga?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // "See all" and Categories both end on the Search tab with the matching filter applied.
    val openFiltered: (HomeRail.SeeAll) -> Unit = { f ->
        state.updateQuery("")
        state.clearFilters()
        f.genre?.let(state::selectGenre)
        f.type?.let(state::selectType)
        f.status?.let(state::selectStatus)
        onOpenSearch()
    }

    @Composable
    fun rail(r: HomeRail): List<Manga>? {
        LaunchedEffect(r) { state.loadRail(r) }
        return (state.rails[r] as? RailState.Ready)?.items
    }

    LazyColumn(
        state = state.homeList,
        contentPadding = PaddingValues(bottom = bottomContentPadding()),
        verticalArrangement = Arrangement.spacedBy(26.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Wordmark(Modifier.weight(1f))
                Row(
                    Modifier
                        .liquidGlass(RoundedCornerShape(50), 0.06f, base = Color(0x66000000))
                        .clickable { showCategories = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Categories", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                }
                val cloud = (LocalContext.current.applicationContext as MangapillApp).cloud
                AccountButton(cloud)
            }
        }

        item(key = "hero") {
            when (val hero = state.rails[HomeRail.POPULAR]) {
                is RailState.Ready -> HeroCarousel(
                    items = hero.items.take(6),
                    isSaved = { it.key in savedKeys },
                    onRead = onMangaSelect,
                    onInfo = { peek = it },
                    onToggleSave = { m -> scope.launch { repository.toggleBookmark(m) } }
                )
                is RailState.Failed -> StateMessage(
                    icon = Icons.Default.CloudOff,
                    title = "Couldn't load manga",
                    message = hero.message,
                    actionLabel = "Try again",
                    onAction = state::refreshHome
                )
                else -> HeroSkeleton()
            }
        }

        if (history.isNotEmpty()) {
            item(key = "continue") {
                Box(Modifier.padding(horizontal = 16.dp)) { ContinueReadingRow(history.take(10), onContinue) }
            }
        }

        item(key = "top10") {
            val r = HomeRail.TOP_NEW
            TopTenRail(r.title, r.subtitle, rail(r), onMangaSelect) { peek = it }
        }
        item(key = "latest") {
            val r = HomeRail.LATEST
            PosterRail(r.title, rail(r), onMangaSelect, { peek = it }, r.subtitle)
        }
        if (favorites.isNotEmpty()) {
            item(key = "mylist") { PosterRail("My List", favorites, onMangaSelect, { peek = it }) }
        }
        state.because?.let { rec ->
            item(key = "because") {
                PosterRail("Because you read ${rec.seed.title}", rec.items, onMangaSelect, { peek = it }, "More ${rec.genre} picked for you")
            }
        }
        listOf(
            HomeRail.TOP_RATED, HomeRail.ACTION, HomeRail.ROMANCE, HomeRail.FANTASY,
            HomeRail.COMEDY, HomeRail.ISEKAI, HomeRail.MANHWA, HomeRail.COMPLETED
        ).forEach { r ->
            item(key = r.name) {
                val items = rail(r)
                // A rail that could not load is left out rather than shown empty.
                if (state.rails[r] !is RailState.Failed) {
                    PosterRail(r.title, items, onMangaSelect, { peek = it }, r.subtitle, r.seeAll?.let { f -> { openFiltered(f) } })
                }
            }
        }
    }

    if (showCategories) {
        FilterSheet(state, repository) {
            showCategories = false
            if (state.activeFilterCount > 0) onOpenSearch()
        }
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
