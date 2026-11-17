package com.mangapill.app.ui.screens.library

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.repository.HistoryEntry
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.ConfirmDialog
import com.mangapill.app.ui.components.MangaCover
import com.mangapill.app.ui.components.MangaPosterCard
import com.mangapill.app.ui.components.ScreenHeader
import com.mangapill.app.ui.components.SegmentedControl
import com.mangapill.app.ui.components.StateMessage
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.navigation.bottomContentPadding
import com.mangapill.app.ui.screens.details.formatChapterNumber
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * "My Library", laid out like AniStream's: a History | My List switch under the title, the
 * reading history (resume, remove one, clear all) and the saved titles.
 */
@Composable
fun LibraryScreen(
    repository: MangaRepository,
    onMangaSelect: (Manga) -> Unit,
    onContinue: (HistoryEntry) -> Unit
) {
    val history by remember { repository.historyEntries() }.collectAsState(initial = emptyList())
    val library by remember { repository.bookmarkedManga() }.collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Back on My List returns to History first, then leaves the tab.
    BackHandler(enabled = tab != 0) { tab = 0 }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("My Library") {
            if (tab == 0 && history.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history", tint = TextSecondary)
                }
            }
        }
        SegmentedControl(
            options = listOf("History", "My List"),
            selectedIndex = tab,
            onSelect = { tab = it },
            icons = listOf(Icons.Outlined.Schedule, Icons.Outlined.Bookmarks),
            counts = listOf(history.size, library.size),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            label = "libraryTab",
            modifier = Modifier.fillMaxSize()
        ) { selected ->
            if (selected == 0) {
                HistoryList(history, onContinue) { entry -> scope.launch { repository.removeFromHistory(entry.manga) } }
            } else {
                SavedGrid(library, onMangaSelect)
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear reading history?",
            message = "Your progress for all ${history.size} titles will be forgotten. Saved titles stay in My List.",
            confirmLabel = "Clear",
            onConfirm = {
                confirmClear = false
                scope.launch { repository.clearHistory() }
            },
            onDismiss = { confirmClear = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryList(entries: List<HistoryEntry>, onContinue: (HistoryEntry) -> Unit, onRemove: (HistoryEntry) -> Unit) {
    if (entries.isEmpty()) {
        StateMessage(
            icon = Icons.Default.History,
            title = "Nothing read yet",
            message = "Chapters you open will appear here so you can pick up where you left off."
        )
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomContentPadding()),
        modifier = Modifier.fillMaxSize()
    ) {
        items(entries, key = { it.manga.key }) { entry ->
            Row(
                modifier = Modifier
                    .animateItemPlacement()
                    .fillMaxWidth()
                    .liquidGlass(RoundedCornerShape(18.dp), 0.06f)
                    .clickable { onContinue(entry) }
                    .padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(56.dp).height(82.dp).clip(RoundedCornerShape(10.dp))) {
                    MangaCover(entry.manga, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        entry.manga.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Chapter ${formatChapterNumber(entry.chapterNumber)} · page ${entry.lastReadPage}",
                        color = PurpleAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(
                        progress = { (entry.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                        color = PurpleAccent,
                        trackColor = PurpleAccent.copy(alpha = 0.25f)
                    )
                    Text(
                        DateUtils.getRelativeTimeSpanString(entry.lastReadAt).toString(),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = { onRemove(entry) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${entry.manga.title} from history", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SavedGrid(library: List<Manga>, onMangaSelect: (Manga) -> Unit) {
    if (library.isEmpty()) {
        StateMessage(
            icon = Icons.Default.BookmarkBorder,
            title = "Your list is empty",
            message = "Tap the bookmark on any title to keep it here."
        )
        return
    }
    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 5 else 2
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomContentPadding()),
        modifier = Modifier.fillMaxSize()
    ) {
        items(library, key = { it.key }) { manga ->
            MangaPosterCard(manga = manga, onClick = { onMangaSelect(manga) })
        }
    }
}
