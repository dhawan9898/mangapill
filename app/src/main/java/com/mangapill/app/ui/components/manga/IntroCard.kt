package com.mangapill.app.ui.components.manga

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.ReadingHistory
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.MangaCover
import com.mangapill.app.ui.components.RatingBadge
import com.mangapill.app.ui.components.SourceBadge
import com.mangapill.app.ui.components.glass.GlassIconButton
import com.mangapill.app.ui.components.glass.GradientButton
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.components.shimmer
import com.mangapill.app.ui.components.sourceLabel
import com.mangapill.app.ui.screens.details.formatChapterNumber
import com.mangapill.app.ui.screens.details.pickStartChapter
import com.mangapill.app.ui.theme.BlueStatus
import com.mangapill.app.ui.theme.EmeraldStatus
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Quick peek of a title in a glass bottom sheet: loads details and chapters on demand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroCardSheet(
    manga: Manga,
    repository: MangaRepository,
    onDismiss: () -> Unit,
    onOpenDetails: () -> Unit,
    onChapter: (Chapter) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var details by remember(manga.key) { mutableStateOf(manga) }
    var chapters by remember(manga.key) { mutableStateOf<List<Chapter>?>(null) }
    val progress by remember(manga.key) { repository.getReadingHistory(manga.sourceId, manga.id) }.collectAsState(initial = null)
    val bookmarked by remember(manga.key) { repository.isBookmarked(manga.sourceId, manga.id) }.collectAsState(initial = false)

    LaunchedEffect(manga.key) {
        coroutineScope {
            launch {
                runCatching { repository.getMangaDetails(manga.sourceId, manga.id) }.getOrNull()?.let { d ->
                    details = d.copy(
                        coverImage = d.coverImage.ifBlank { manga.coverImage },
                        rating = if (d.rating > 0f) d.rating else manga.rating
                    )
                }
            }
            launch {
                chapters = runCatching { repository.getChapterList(manga.sourceId, manga.id) }.getOrDefault(emptyList())
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF20C0C14),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 6.dp).width(40.dp).height(4.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        IntroCard(
            manga = details,
            chapters = chapters,
            progress = progress,
            isBookmarked = bookmarked,
            onBookmarkToggle = { scope.launch { repository.toggleBookmark(details) } },
            onOpenDetails = onOpenDetails,
            onChapter = onChapter,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

/**
 * Interactive intro card: cover art, live rating badge, status tag, collapsible synopsis and a
 * quick-jump list of the most recent chapters.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntroCard(
    manga: Manga,
    chapters: List<Chapter>?,
    progress: ReadingHistory?,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    onOpenDetails: () -> Unit,
    onChapter: (Chapter) -> Unit,
    modifier: Modifier = Modifier
) {
    var synopsisExpanded by remember { mutableStateOf(false) }
    val springy = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.width(118.dp).height(177.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            ) {
                MangaCover(manga, Modifier.fillMaxSize())
                RatingBadge(manga.rating, Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    manga.title,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(sourceLabel(manga.sourceId))
                    StatusTag(manga.status)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    manga.genres.take(5).forEach { g ->
                        Text(
                            g,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.liquidGlass(RoundedCornerShape(8.dp), 0.05f).padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        if (manga.synopsis.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(springy)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { synopsisExpanded = !synopsisExpanded }
            ) {
                Text(
                    manga.synopsis,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (synopsisExpanded) "Show less" else "Read more",
                    color = PurpleAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val target = chapters?.let { pickStartChapter(it, progress?.chapterId) }
            GradientButton(onClick = { target?.let(onChapter) }, enabled = target != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        chapters == null -> "Loading…"
                        target == null -> "No chapters"
                        progress != null -> "Resume Ch. ${formatChapterNumber(progress.chapterNumber)}"
                        else -> "Start reading"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            GlassIconButton(
                icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = if (isBookmarked) "Remove from library" else "Add to library",
                onClick = onBookmarkToggle,
                active = isBookmarked
            )
            GlassIconButton(icon = Icons.Default.Info, contentDescription = "Open details", onClick = onOpenDetails)
        }

        Spacer(Modifier.height(18.dp))
        Text("Recent chapters", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (chapters == null) {
            repeat(3) {
                Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).height(44.dp).clip(RoundedCornerShape(12.dp)).shimmer())
            }
        } else {
            run {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (chapters.isEmpty()) Text("No chapters found on this source.", color = TextMuted, fontSize = 12.sp)
                    chapters.take(5).forEach { ch ->
                        val read = progress != null && progress.chapterNumber >= ch.chapterNumber
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .liquidGlass(RoundedCornerShape(12.dp), if (read) 0.02f else 0.06f)
                                .clickable { onChapter(ch) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (read) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Read", tint = EmeraldStatus, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                "Chapter ${formatChapterNumber(ch.chapterNumber)}",
                                color = if (read) TextMuted else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            ch.releaseDate?.let { Text(it, color = TextMuted, fontSize = 11.sp) }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusTag(status: MangaStatus) {
    if (status == MangaStatus.UNKNOWN) return
    val color = when (status) {
        MangaStatus.ONGOING -> EmeraldStatus
        MangaStatus.HIATUS -> Color(0xFFF59E0B)
        else -> BlueStatus
    }
    Text(
        status.name.lowercase().replaceFirstChar { it.uppercase() },
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
