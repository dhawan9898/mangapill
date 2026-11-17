package com.mangapill.app.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.download.DownloadState
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.MangaCover
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.mangapill.app.ui.components.glass.GlassIconButton
import com.mangapill.app.ui.components.glass.PrimaryButton
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.components.manga.StatusTag
import com.mangapill.app.ui.components.RatingBadge
import com.mangapill.app.ui.components.StateMessage
import com.mangapill.app.ui.components.friendlyError
import com.mangapill.app.ui.components.shimmer
import com.mangapill.app.ui.theme.BlueStatus
import com.mangapill.app.ui.theme.EmeraldStatus
import com.mangapill.app.ui.theme.OledBackground
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaDetailsScreen(
    sourceId: String,
    mangaId: String,
    repository: MangaRepository,
    onBack: () -> Unit,
    onChapterSelect: (Chapter) -> Unit
) {
    val scope = rememberCoroutineScope()

    var manga by remember { mutableStateOf<Manga?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var chaptersError by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    var chapterFilter by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }
    var synopsisExpanded by remember { mutableStateOf(false) }

    val progress by remember(sourceId, mangaId) { repository.getReadingHistory(sourceId, mangaId) }.collectAsState(initial = null)
    val bookmarked by remember(sourceId, mangaId) { repository.isBookmarked(sourceId, mangaId) }.collectAsState(initial = false)

    // Offline downloads: state per chapter, and the chapter whose removal is being confirmed.
    val downloads = repository.downloads
    val downloadStates by remember(downloads) { downloads?.states ?: MutableStateFlow<Map<String, DownloadState>>(emptyMap()) }.collectAsState()
    var removing by remember { mutableStateOf<Chapter?>(null) }

    // Debounce chapter clicks to prevent rapid double taps opening multiple reader instances
    var lastChapterClickTime by remember { mutableStateOf(0L) }
    val safeChapterSelect: (Chapter) -> Unit = remember(onChapterSelect) {
        { chapter ->
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastChapterClickTime >= 800L) {
                lastChapterClickTime = now
                onChapterSelect(chapter)
            }
        }
    }

    LaunchedEffect(sourceId, mangaId, reloadTick) {
        loading = true
        error = null
        chaptersError = null
        // Load both in parallel; the chapter list failing must not hide the manga itself.
        val details = async { runCatching { repository.getMangaDetails(sourceId, mangaId) } }
        val list = async { runCatching { repository.getChapterList(sourceId, mangaId, force = reloadTick > 0) } }
        val d = details.await()
        val c = list.await()
        manga = d.getOrNull()
        chapters = c.getOrDefault(emptyList())
        error = d.exceptionOrNull()?.let { friendlyError(it) }
        chaptersError = c.exceptionOrNull()?.let { friendlyError(it) }
        loading = false
    }

    val visibleChapters = remember(chapters, chapterFilter, newestFirst) {
        val filtered = if (chapterFilter.isBlank()) chapters else chapters.filter {
            it.title?.contains(chapterFilter, ignoreCase = true) == true ||
                formatChapterNumber(it.chapterNumber).contains(chapterFilter.trim())
        }
        // Scrapers return newest first.
        if (newestFirst) filtered else filtered.reversed()
    }

    Box(Modifier.fillMaxSize()) {
        val m = manga
        when {
            loading && m == null -> DetailsSkeleton()

            m == null -> {
                StateMessage(
                    icon = Icons.Default.CloudOff,
                    title = "Couldn't load this title",
                    message = error ?: "Unknown error",
                    modifier = Modifier.align(Alignment.Center),
                    actionLabel = "Try again",
                    onAction = { reloadTick++ }
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Hero(m) }

                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val target = remember(chapters, progress) { pickStartChapter(chapters, progress?.chapterId) }
                            PrimaryButton(
                                onClick = { target?.let(safeChapterSelect) },
                                enabled = target != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        target == null -> "No chapters"
                                        progress != null -> "Resume Ch. ${formatChapterNumber(progress!!.chapterNumber)}"
                                        else -> "Start reading"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            GlassIconButton(
                                icon = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (bookmarked) "Remove from library" else "Add to library",
                                onClick = { scope.launch { repository.toggleBookmark(m) } },
                                active = bookmarked
                            )
                        }

                        if (m.genres.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                m.genres.take(10).forEach { g ->
                                    Text(
                                        text = g,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(com.mangapill.app.ui.theme.TrackOff)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (m.synopsis.isNotBlank()) {
                            Column {
                                Text(
                                    text = m.synopsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (synopsisExpanded) "Show less" else "Read more",
                                    color = PurpleAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { synopsisExpanded = !synopsisExpanded }
                                        .padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Chapters (${chapters.size})", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Saves every chapter listed below (all of them, or the ones the filter shows).
                                if (downloads != null && visibleChapters.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { visibleChapters.forEach { repository.downloadChapter(sourceId, mangaId, it, chapters) } }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Download all", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { newestFirst = !newestFirst }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.SwapVert, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (newestFirst) "Newest" else "Oldest", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (chapters.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = chapterFilter,
                                onValueChange = { chapterFilter = it },
                                placeholder = { Text("Jump to chapter…", color = TextMuted) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PurpleAccent,
                                    unfocusedBorderColor = TextMuted.copy(alpha = 0.35f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = PurpleAccent
                                )
                            )
                        }
                    }
                }

                if (chapters.isEmpty()) {
                    item {
                        if (loading) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp).clip(RoundedCornerShape(12.dp)).shimmer())
                        } else {
                            StateMessage(
                                icon = Icons.Default.CloudOff,
                                title = if (chaptersError != null) "Couldn't load chapters" else "No chapters available",
                                message = chaptersError ?: "This title has no readable chapters yet.",
                                actionLabel = if (chaptersError != null) "Retry" else null,
                                onAction = { reloadTick++ }
                            )
                        }
                    }
                }

                items(visibleChapters, key = { it.id }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        isCurrent = progress?.chapterId == chapter.id,
                        isRead = progress?.let { it.chapterNumber > chapter.chapterNumber } == true,
                        onClick = { safeChapterSelect(chapter) },
                        showDownload = downloads != null,
                        download = downloads?.let { downloadStates[it.keyOf(sourceId, mangaId, chapter.id)] },
                        onDownload = { repository.downloadChapter(sourceId, mangaId, chapter, chapters) },
                        // A finished download is only removed after asking; one in progress just stops.
                        onRemove = { state ->
                            if (state is DownloadState.Done) removing = chapter
                            else downloads?.remove(sourceId, mangaId, chapter.id)
                        }
                    )
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        removing?.let { chapter ->
            AlertDialog(
                onDismissRequest = { removing = null },
                title = { Text("Remove download?") },
                text = { Text("Chapter ${formatChapterNumber(chapter.chapterNumber)} will no longer be available offline.") },
                confirmButton = {
                    TextButton(onClick = {
                        downloads?.remove(sourceId, mangaId, chapter.id)
                        removing = null
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } }
            )
        }

        // Floating back button, above everything else.
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(10.dp)
        )
    }
}

@Composable
private fun Hero(manga: Manga) {
    Box(Modifier.fillMaxWidth().height(340.dp)) {
        // Blurred backdrop that dissolves into the aurora background instead of ending in a hard edge.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.6f), Color.Transparent)),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            MangaCover(manga, Modifier.fillMaxSize().blur(22.dp), alpha = 0.55f)
            // Darkens the blurred cover on the dark themes, washes it out on the light one, so the
            // title under it always reads.
            val light = com.mangapill.app.ui.theme.LocalPalette.current.isLight
            Box(Modifier.fillMaxSize().background(if (light) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f)))
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                Modifier
                    .width(124.dp)
                    .height(186.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            ) {
                MangaCover(manga, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = manga.title,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusTag(manga.status)
                    RatingBadge(manga.rating)
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    isCurrent: Boolean,
    isRead: Boolean,
    onClick: () -> Unit,
    showDownload: Boolean = false,
    download: DownloadState? = null,
    onDownload: () -> Unit = {},
    onRemove: (DownloadState) -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .liquidGlass(shape, if (isRead) 0.02f else 0.06f)
            .then(if (isCurrent) Modifier.border(1.5.dp, PurpleAccent, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (chapter.chapterNumber > 0f || chapter.title == null) "Chapter ${formatChapterNumber(chapter.chapterNumber)}"
                else chapter.title.orEmpty(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRead) TextMuted else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = chapter.title?.takeIf { chapter.chapterNumber > 0f && !it.startsWith("Chapter", ignoreCase = true) }
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (isRead) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Read", tint = EmeraldStatus, modifier = Modifier.size(16.dp))
        } else {
            chapter.releaseDate?.let { Text(it, fontSize = 11.sp, color = TextMuted) }
        }
        if (showDownload) {
            Spacer(Modifier.width(6.dp))
            DownloadControl(download, onDownload, onRemove)
        }
    }
}

/** Download / progress (tap to stop) / downloaded (tap to remove) / failed (tap to try again). */
@Composable
private fun DownloadControl(state: DownloadState?, onDownload: () -> Unit, onRemove: (DownloadState) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { if (state == null || state is DownloadState.Failed) onDownload() else onRemove(state) }
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            null -> Icon(Icons.Default.Download, contentDescription = "Download chapter", tint = TextSecondary, modifier = Modifier.size(20.dp))
            is DownloadState.Running -> {
                if (state.total > 0) {
                    Text("${state.done}/${state.total}", fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.width(6.dp))
                }
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PurpleAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Icon(Icons.Default.Close, contentDescription = "Stop download", tint = TextSecondary, modifier = Modifier.size(10.dp))
                }
            }
            is DownloadState.Done -> Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded. Tap to remove", tint = EmeraldStatus, modifier = Modifier.size(20.dp))
            is DownloadState.Failed -> Icon(Icons.Default.ErrorOutline, contentDescription = "Download failed. Tap to try again", tint = Color(0xFFE5484D), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DetailsSkeleton() {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.width(124.dp).height(186.dp).clip(RoundedCornerShape(14.dp)).shimmer())
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(180.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                Box(Modifier.width(120.dp).height(16.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            }
        }
        repeat(5) { Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).shimmer()) }
    }
}

fun pickStartChapter(chapters: List<Chapter>, resumeChapterId: String?): Chapter? {
    if (chapters.isEmpty()) return null
    resumeChapterId?.let { id -> chapters.firstOrNull { it.id == id }?.let { return it } }
    // Lists are newest-first, so the first chapter of the story is the last entry.
    return chapters.last()
}

fun formatChapterNumber(n: Float): String =
    if (n % 1f == 0f) n.toInt().toString() else n.toString()
