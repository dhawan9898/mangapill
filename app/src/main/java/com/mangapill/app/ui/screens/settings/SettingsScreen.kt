package com.mangapill.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.LinearProgressIndicator
import com.mangapill.app.ui.theme.AppTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.mangapill.app.data.model.ReaderMode
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.data.update.AppUpdateService
import com.mangapill.app.data.update.UpdateInfo
import com.mangapill.app.ui.components.ActionRow
import com.mangapill.app.ui.components.CardDivider
import com.mangapill.app.ui.components.ConfirmDialog
import com.mangapill.app.ui.components.PillButton
import com.mangapill.app.ui.components.ScreenHeader
import com.mangapill.app.ui.components.SectionLabel
import com.mangapill.app.ui.components.SegmentedControl
import com.mangapill.app.ui.components.SettingsCard
import com.mangapill.app.ui.components.ToggleRow
import com.mangapill.app.ui.navigation.bottomContentPadding
import com.mangapill.app.ui.theme.AccentSoft
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings, grouped like AniStream's: Appearance, Reader, OPDS servers, Data and About. */
@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(repository: MangaRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = repository.prefs
    val theme by prefs.theme.collectAsState()
    val clearGlass by prefs.clearGlass.collectAsState()
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

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf<Float?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

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
            SectionLabel("Appearance")
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Theme", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    SegmentedControl(
                        options = AppTheme.entries.map { it.label },
                        selectedIndex = theme.ordinal,
                        onSelect = { prefs.setTheme(AppTheme.entries[it]) }
                    )
                }
                CardDivider()
                ToggleRow(
                    Icons.Outlined.WaterDrop,
                    "Transparent Glass",
                    "Floating bars, buttons and tiles turn see-through instead of frosted.",
                    clearGlass,
                    prefs::setClearGlass
                )
            }

            Spacer(Modifier.height(26.dp))
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
            SectionLabel("App Updates")
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mangapill", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Installed: v$version", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        PillButton(
                            label = if (updateChecking) "Checking..." else "Check for Updates",
                            onClick = {
                                if (!updateChecking && !updateDownloading) {
                                    scope.launch {
                                        updateChecking = true
                                        updateStatus = null
                                        try {
                                            val info = AppUpdateService.checkForUpdate("dhawan9898/mangapill", version)
                                            if (info != null) {
                                                updateInfo = info
                                                updateStatus = "Update available: v${info.version}"
                                            } else {
                                                updateInfo = null
                                                updateStatus = "Mangapill is up to date."
                                            }
                                        } catch (e: Exception) {
                                            updateStatus = "Update check failed: ${e.message ?: "Network error"}"
                                        } finally {
                                            updateChecking = false
                                        }
                                    }
                                }
                            },
                            icon = Icons.Outlined.Refresh
                        )
                    }

                    if (updateStatus != null) {
                        Text(
                            text = updateStatus.orEmpty(),
                            color = if (updateInfo != null) PurpleAccent else TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (updateInfo != null) {
                        val info = updateInfo!!
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentSoft)
                                .border(1.dp, PurpleAccent, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("New Version: v${info.version}", color = PurpleAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                if (info.publishedAt != null) {
                                    Text(info.publishedAt.substringBefore("T"), color = TextMuted, fontSize = 11.sp)
                                }
                            }
                            if (info.releaseNotes.isNotBlank()) {
                                Text(
                                    info.releaseNotes,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            if (updateProgress != null) {
                                LinearProgressIndicator(
                                    progress = { updateProgress ?: 0f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = PurpleAccent,
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                                Text(
                                    "Downloading APK: ${((updateProgress ?: 0f) * 100).toInt()}%",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            PillButton(
                                label = if (updateDownloading) "Downloading & Installing..." else "Download & Install Update",
                                onClick = {
                                    if (!updateDownloading) {
                                        scope.launch {
                                            updateDownloading = true
                                            updateProgress = 0f
                                            val res = AppUpdateService.downloadAndInstallApk(context, info.apkUrl) { pct ->
                                                updateProgress = pct
                                            }
                                            if (res.isFailure) {
                                                updateStatus = "Update failed: ${res.exceptionOrNull()?.message ?: "Error"}"
                                            } else {
                                                updateStatus = "APK downloaded. Launching package installer..."
                                            }
                                            updateDownloading = false
                                        }
                                    }
                                },
                                icon = Icons.Outlined.CloudDownload,
                                background = PurpleAccent,
                                contentColor = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("About")
            SettingsCard {
                ActionRow(Icons.Outlined.Info, "Mangapill", "Manga, manhwa and manhua from several sources at once.", onClick = null, trailing = "v$version")
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
