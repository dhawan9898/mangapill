package com.mangapill.app.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.ReaderMode
import com.mangapill.app.data.model.ReadingHistory
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.StateMessage
import com.mangapill.app.ui.components.friendlyError
import com.mangapill.app.ui.components.shimmer
import com.mangapill.app.ui.screens.details.formatChapterNumber
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DualModeReaderScreen(
    sourceId: String,
    mangaId: String,
    chapterId: String,
    chapterNumber: Float,
    repository: MangaRepository,
    onBack: () -> Unit,
    onOpenChapter: (Chapter) -> Unit
) {
    var pages by remember { mutableStateOf<List<PageImage>>(emptyList()) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var startPage by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var readerMode by remember { mutableStateOf(ReaderMode.WEBTOON) }
    var showControls by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(1) }

    LaunchedEffect(sourceId, chapterId, reloadTick) {
        loading = true
        error = null
        try {
            val resume = repository.getReadingHistoryOnce(sourceId, mangaId)
            startPage = if (resume != null && resume.chapterId == chapterId) (resume.lastReadPage - 1).coerceAtLeast(0) else 0
            pages = repository.getChapterPages(sourceId, chapterId)
            currentPage = startPage + 1
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = friendlyError(e)
        } finally {
            loading = false
        }
        // Only needed for prev/next; a failure here just hides those buttons.
        chapters = runCatching { repository.getChapterList(sourceId, mangaId) }.getOrDefault(emptyList())
    }

    // chapters are newest-first: the "next" chapter is the one before it in the list.
    val index = chapters.indexOfFirst { it.id == chapterId }
    val nextChapter = if (index > 0) chapters[index - 1] else null
    val previousChapter = if (index >= 0 && index < chapters.lastIndex) chapters[index + 1] else null

    // Persist reading progress whenever the visible page changes.
    LaunchedEffect(pages, currentPage) {
        if (pages.isEmpty()) return@LaunchedEffect
        repository.saveReadingHistory(
            sourceId,
            ReadingHistory(
                mangaId = mangaId,
                chapterId = chapterId,
                chapterNumber = chapterNumber,
                lastReadPage = currentPage,
                scrollPercentage = (currentPage.toFloat() / pages.size * 100f).coerceIn(0f, 100f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                showControls = !showControls
            }
    ) {
        when {
            loading -> CircularProgressIndicator(color = PurpleAccent, modifier = Modifier.align(Alignment.Center))

            error != null -> StateMessage(
                icon = Icons.Default.CloudOff,
                title = "Couldn't load chapter",
                message = error.orEmpty(),
                modifier = Modifier.align(Alignment.Center),
                actionLabel = "Try again",
                onAction = { reloadTick++ }
            )

            pages.isEmpty() -> Box(Modifier.fillMaxSize())

            readerMode == ReaderMode.WEBTOON -> {
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentPage - 1).coerceAtLeast(0))
                LaunchedEffect(listState, pages) {
                    snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { currentPage = it + 1 }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(pages, key = { _, p -> p.pageNumber }) { _, page ->
                        PageImageView(page, ContentScale.FillWidth, Modifier.fillMaxWidth(), minHeight = 480)
                    }
                    item {
                        ChapterEndCard(nextChapter, onOpenChapter)
                    }
                }
            }

            else -> {
                val pagerState = rememberPagerState(initialPage = (currentPage - 1).coerceIn(0, pages.lastIndex)) { pages.size }
                LaunchedEffect(pagerState, pages) {
                    snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { currentPage = it + 1 }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { i ->
                    PageImageView(pages[i], ContentScale.Fit, Modifier.fillMaxSize(), minHeight = 0)
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        text = "Chapter ${formatChapterNumber(chapterNumber)}",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable {
                            readerMode = if (readerMode == ReaderMode.WEBTOON) ReaderMode.MANGA_HORIZONTAL else ReaderMode.WEBTOON
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Switch mode", tint = PurpleAccent, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (readerMode == ReaderMode.WEBTOON) "Webtoon" else "Paged",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showControls && pages.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { previousChapter?.let(onOpenChapter) }, enabled = previousChapter != null) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous chapter", tint = if (previousChapter != null) TextPrimary else TextMuted)
                }
                Text("Page $currentPage / ${pages.size}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { nextChapter?.let(onOpenChapter) }, enabled = nextChapter != null) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next chapter", tint = if (nextChapter != null) TextPrimary else TextMuted)
                }
            }
        }
    }
}

@Composable
private fun PageImageView(page: PageImage, scale: ContentScale, modifier: Modifier, minHeight: Int) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val request = remember(page.imageUrl, attempt) {
        ImageRequest.Builder(context).data(page.imageUrl).apply {
            page.referer?.let { addHeader("Referer", it) }
        }.setParameter("retry", attempt).crossfade(true).build()
    }
    val sizeModifier = if (minHeight > 0) modifier.height(minHeight.dp) else modifier

    SubcomposeAsyncImage(
        model = request,
        contentDescription = "Page ${page.pageNumber}",
        modifier = modifier,
        contentScale = scale,
        loading = {
            Box(sizeModifier.shimmer(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PurpleAccent, modifier = Modifier.size(28.dp))
            }
        },
        error = {
            Box(sizeModifier.background(Color(0xFF14141C)).clickable { attempt++ }, contentAlignment = Alignment.Center) {
                Text("Failed to load page ${page.pageNumber}. Tap to retry.", color = TextMuted, fontSize = 12.sp)
            }
        }
    )
}

@Composable
private fun ChapterEndCard(next: Chapter?, onOpen: (Chapter) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        if (next != null) {
            Text(
                text = "Next: Chapter ${formatChapterNumber(next.chapterNumber)}  ›",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(PurpleAccent)
                    .clickable { onOpen(next) }
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            )
        } else {
            Text("You're all caught up", color = TextMuted, fontSize = 13.sp)
        }
    }
}
