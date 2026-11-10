package com.mangapill.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.TouchApp
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import coil.imageLoader
import com.mangapill.app.data.model.ReaderMode
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.ActionRow
import com.mangapill.app.ui.components.CardDivider
import com.mangapill.app.ui.components.ConfirmDialog
import com.mangapill.app.ui.components.IosSwitch
import com.mangapill.app.ui.components.ScreenHeader
import com.mangapill.app.ui.components.SectionLabel
import com.mangapill.app.ui.components.SegmentedControl
import com.mangapill.app.ui.components.SettingsCard
import com.mangapill.app.ui.components.ToggleRow
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.navigation.bottomContentPadding
import com.mangapill.app.ui.theme.PinkAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings, grouped like AniStream's: Reader, Sources, OPDS servers, Data and About. */
@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(repository: MangaRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = repository.prefs
    val enabled by prefs.enabledSources.collectAsState()
    // Read so the source list below recomposes when a server is added or removed.
    val servers by prefs.opdsServers.collectAsState()
    val readerMode by prefs.readerMode.collectAsState()
    val keepScreenOn by prefs.keepScreenOn.collectAsState()
    val tapToTurn by prefs.tapToTurn.collectAsState()
    val recents by prefs.recentSearches.collectAsState()
    val history by remember { repository.historyEntries() }.collectAsState(initial = emptyList())
    val library by remember { repository.bookmarkedManga() }.collectAsState(initial = emptyList())

    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var cacheTick by remember { mutableStateOf(0) }
    LaunchedEffect(cacheTick) {
        cacheBytes = withContext(Dispatchers.IO) { context.imageLoader.diskCache?.size }
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Settings")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 18.dp, bottom = bottomContentPadding()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SectionLabel("Reader")
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Reading mode", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Every chapter opens this way. You can also switch from the reader's top bar.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )
                    SegmentedControl(
                        options = ReaderMode.entries.map { it.label },
                        selectedIndex = readerMode.ordinal,
                        onSelect = { prefs.setReaderMode(ReaderMode.entries[it]) }
                    )
                }
                CardDivider()
                ToggleRow(
                    Icons.Outlined.TouchApp,
                    "Tap edges to turn pages",
                    "In paged modes, tap the left or right side to go back or forward.",
                    tapToTurn,
                    prefs::setTapToTurn
                )
                CardDivider()
                ToggleRow(
                    Icons.Outlined.LightMode,
                    "Keep screen on",
                    "The screen stays awake while a chapter is open.",
                    keepScreenOn,
                    prefs::setKeepScreenOn
                )
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Sources")
            SettingsCard {
                Text(
                    "Enabled sources are searched together. Experimental ones could not be verified and may be " +
                        "blocked, slow or broken; a source that fails is skipped for a few minutes automatically.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
                )
                val sources = remember(servers) { repository.sources }
                sources.forEachIndexed { i, s ->
                    if (i > 0) CardDivider()
                    val on = s.id in enabled
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Switch) {
                                if (!prefs.setSourceEnabled(s.id, !on)) {
                                    Toast.makeText(context, "Keep at least one source enabled", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (s.experimental) {
                                Text(
                                    "Experimental",
                                    color = PinkAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .liquidGlass(RoundedCornerShape(6.dp), 0.04f)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        IosSwitch(on)
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("OPDS servers")
            OpdsServersCard(repository)

            Spacer(Modifier.height(26.dp))
            SectionLabel("Data")
            SettingsCard {
                ActionRow(
                    Icons.AutoMirrored.Outlined.ManageSearch,
                    "Clear search history",
                    "Removes the recent searches on the Search tab.",
                    onClick = if (recents.isEmpty()) null else ({
                        prefs.clearRecentSearches()
                        Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                    }),
                    trailing = recents.size.takeIf { it > 0 }?.toString()
                )
                CardDivider()
                ActionRow(
                    Icons.Outlined.DeleteSweep,
                    "Clear reading history",
                    "Forgets where you stopped in every title.",
                    onClick = if (history.isEmpty()) null else ({ confirm = Confirm.HISTORY }),
                    danger = history.isNotEmpty(),
                    trailing = history.size.takeIf { it > 0 }?.toString()
                )
                CardDivider()
                ActionRow(
                    Icons.Outlined.BookmarkRemove,
                    "Clear My List",
                    "Removes every saved title from your library.",
                    onClick = if (library.isEmpty()) null else ({ confirm = Confirm.LIBRARY }),
                    danger = library.isNotEmpty(),
                    trailing = library.size.takeIf { it > 0 }?.toString()
                )
                CardDivider()
                ActionRow(
                    Icons.Outlined.Image,
                    "Clear image cache",
                    "Frees the space used by covers and prefetched pages. They download again when needed.",
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                context.imageLoader.diskCache?.clear()
                                context.imageLoader.memoryCache?.clear()
                            }
                            cacheTick++
                            Toast.makeText(context, "Image cache cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    trailing = cacheBytes?.let { formatBytes(it) }
                )
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("About")
            SettingsCard {
                ActionRow(Icons.Outlined.Info, "Mangapill", "A manga reader for the sources you enable.", onClick = null, trailing = "v$version")
                CardDivider()
                ActionRow(
                    Icons.Outlined.AutoStories,
                    "Reading tips",
                    "Double-tap or pinch to zoom. Long-press a cover for a quick look and its chapters.",
                    onClick = null
                )
            }
            Text(
                "Made with the same look as AniStream and CineStream.",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    when (confirm) {
        Confirm.HISTORY -> ConfirmDialog(
            title = "Clear reading history?",
            message = "Your progress for all ${history.size} titles will be forgotten. Saved titles stay in My List.",
            confirmLabel = "Clear",
            onConfirm = {
                confirm = null
                scope.launch { repository.clearHistory() }
            },
            onDismiss = { confirm = null }
        )
        Confirm.LIBRARY -> ConfirmDialog(
            title = "Clear My List?",
            message = "All ${library.size} saved titles will be removed. Your reading history is kept.",
            confirmLabel = "Clear",
            onConfirm = {
                confirm = null
                scope.launch { repository.clearLibrary() }
            },
            onDismiss = { confirm = null }
        )
        null -> Unit
    }
}

private enum class Confirm { HISTORY, LIBRARY }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
