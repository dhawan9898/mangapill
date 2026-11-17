package com.mangapill.app.ui.screens.reader

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.material.icons.filled.SwapHoriz
import kotlinx.coroutines.launch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import android.content.Context
import coil.imageLoader
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

/** The reader keeps its black page and light controls whatever the app theme is. */
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
    com.mangapill.app.ui.theme.AlwaysDark {
        ReaderContent(sourceId, mangaId, chapterId, chapterNumber, repository, onBack, onOpenChapter)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderContent(
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
    /** Set when another source served the pages (the title's own source has none). */
    var servedBy by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    // The mode is remembered across chapters and app restarts.
    val readerMode by repository.prefs.readerMode.collectAsState()
    val keepScreenOn by repository.prefs.keepScreenOn.collectAsState()
    val tapToTurn by repository.prefs.tapToTurn.collectAsState()
    val paged = readerMode != ReaderMode.WEBTOON
    val rtl = readerMode == ReaderMode.MANGA_RTL
    // Set by the pager branch: turns one page forward (+1) or back (-1) in reading order.
    var turnPage by remember { mutableStateOf<(Int) -> Unit>({}) }
    var showControls by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(1) }
    val zoom = rememberZoomState()
    // Set by the active reader branch: scrolls the list / pager by whatever a zoomed pan couldn't use.
    var overflowHandler by remember { mutableStateOf<(Offset) -> Unit>({}) }

    LaunchedEffect(sourceId, chapterId, reloadTick) {
        loading = true
        error = null
        try {
            val resume = repository.getReadingHistoryOnce(sourceId, mangaId)
            startPage = if (resume != null && resume.chapterId == chapterId) (resume.lastReadPage - 1).coerceAtLeast(0) else 0
            val loaded = repository.loadChapter(sourceId, mangaId, chapterId, chapterNumber)
            pages = loaded.pages
            servedBy = loaded.servedBy
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

    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    // Full screen while reading: the status and navigation bars leave with the reader's own
    // controls (a swipe from the edge peeks them), and always come back when the reader closes.
    val window = (view.context as? android.app.Activity)?.window
    DisposableEffect(showControls, window) {
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val bars = androidx.core.view.WindowInsetsCompat.Type.systemBars()
        if (showControls) controller?.show(bars) else controller?.hide(bars)
        onDispose { controller?.show(bars) }
    }
    // In paged modes the outer thirds turn the page (respecting right-to-left); the middle, or any
    // tap in webtoon mode, shows and hides the controls.
    val onTap by rememberUpdatedState { at: Offset, size: IntSize ->
        val third = size.width / 3f
        when {
            !paged || !tapToTurn || zoom.isZoomed -> showControls = !showControls
            at.x < third -> turnPage(if (rtl) 1 else -1)
            at.x > size.width - third -> turnPage(if (rtl) -1 else 1)
            else -> showControls = !showControls
        }
    }

    // Pre-fetch the next few pages so page turns are instant.
    LaunchedEffect(pages, currentPage) {
        val loader = context.imageLoader
        pages.drop(currentPage).take(PREFETCH_PAGES).forEach { loader.enqueue(pageRequest(context, it)) }
    }

    // Near the end of the chapter, download the whole next chapter into the disk cache so it
    // opens instantly and can be read with a poor connection.
    var nextPrefetched by remember(chapterId) { mutableStateOf(false) }
    LaunchedEffect(currentPage, nextChapter?.id) {
        val next = nextChapter ?: return@LaunchedEffect
        if (nextPrefetched || pages.isEmpty() || currentPage < pages.size - 3) return@LaunchedEffect
        nextPrefetched = true
        val nextPages = runCatching { repository.loadChapter(sourceId, mangaId, next.id, next.chapterNumber).pages }.getOrNull() ?: return@LaunchedEffect
        val loader = context.imageLoader
        nextPages.forEach { loader.enqueue(pageRequest(context, it)) }
    }

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
    ) {
      Box(
        Modifier
            .fillMaxSize()
            .zoomable(zoom, onTap = { at, size -> onTap(at, size) }, onOverflow = { overflowHandler(it) })
      ) {
        when {
            loading -> CircularProgressIndicator(color = PurpleAccent, modifier = Modifier.align(Alignment.Center))

            error != null -> {
                val isOfflineOrSlow = error.orEmpty().contains("network", ignoreCase = true) ||
                    error.orEmpty().contains("internet", ignoreCase = true) ||
                    error.orEmpty().contains("timeout", ignoreCase = true) ||
                    error.orEmpty().contains("reach", ignoreCase = true)

                StateMessage(
                    icon = Icons.Default.CloudOff,
                    title = if (isOfflineOrSlow) "Slow or Offline Network" else "Couldn't load chapter",
                    message = if (isOfflineOrSlow)
                        "Network is slow or disconnected. You can retry, or return to read saved chapters from your library."
                    else error.orEmpty(),
                    modifier = Modifier.align(Alignment.Center),
                    actionLabel = "Try again",
                    onAction = { reloadTick++ },
                    secondaryActionLabel = if (isOfflineOrSlow) "Go to Library" else null,
                    onSecondaryAction = if (isOfflineOrSlow) onBack else null
                )
            }

            pages.isEmpty() -> {
                StateMessage(
                    icon = Icons.Default.CloudOff,
                    title = "No pages found",
                    message = "This chapter contains no readable pages from this source.",
                    modifier = Modifier.align(Alignment.Center),
                    actionLabel = "Retry",
                    onAction = { reloadTick++ },
                    secondaryActionLabel = "Go back",
                    onSecondaryAction = onBack
                )
            }

            readerMode == ReaderMode.WEBTOON -> {
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentPage - 1).coerceAtLeast(0))
                LaunchedEffect(listState, pages) {
                    snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { currentPage = it + 1 }
                }
                SideEffect {
                    overflowHandler = { o -> listState.dispatchRawDelta(-o.y / zoom.scale) }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(pages, key = { _, p -> p.pageNumber }) { _, page ->
                        PageImageView(page, ContentScale.FillWidth, Modifier.fillMaxWidth(), minHeight = 480, tallStrip = true)
                    }
                    item {
                        ChapterEndCard(nextChapter, onOpenChapter)
                    }
                }
            }

            else -> {
                // One extra page after the last: the "next chapter" card, as at the end of a webtoon.
                val pageCount = pages.size + 1
                val pagerState = rememberPagerState(initialPage = (currentPage - 1).coerceIn(0, pages.lastIndex)) { pageCount }
                val pagerScope = rememberCoroutineScope()
                LaunchedEffect(pagerState, pages) {
                    snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { currentPage = (it + 1).coerceAtMost(pages.size) }
                }
                SideEffect {
                    // Right to left the layout is mirrored, so a finger moving left goes back.
                    overflowHandler = { o -> pagerState.dispatchRawDelta(if (rtl) o.x else -o.x) }
                    turnPage = { step ->
                        val target = (pagerState.currentPage + step).coerceIn(0, pageCount - 1)
                        if (target != pagerState.currentPage) pagerScope.launch { pagerState.animateScrollToPage(target) }
                    }
                }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { zoom.reset() }
                }
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = rtl,
                    userScrollEnabled = !zoom.isZoomed,
                    beyondBoundsPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { i ->
                    if (i < pages.size) {
                        PageImageView(pages[i], ContentScale.Fit, Modifier.fillMaxSize(), minHeight = 0)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ChapterEndCard(nextChapter, onOpenChapter) }
                    }
                }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Column {
                        Text(
                            text = "Chapter ${formatChapterNumber(chapterNumber)}",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable {
                            zoom.reset()
                            repository.prefs.setReaderMode(readerMode.next())
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(if (paged) Icons.Default.SwapHoriz else Icons.Default.SwapVert, contentDescription = "Reading mode", tint = PurpleAccent, modifier = Modifier.size(16.dp))
                    Text(
                        text = readerMode.label,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = zoom.isZoomed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 12.dp)
        ) {
            Text(
                text = "%.1fx  ·  Reset".format(zoom.scale),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PurpleAccent)
                    .clickable { zoom.reset() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
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
private fun PageImageView(page: PageImage, scale: ContentScale, modifier: Modifier, minHeight: Int, tallStrip: Boolean = false) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    // Webtoon pages have no height limit of their own; see pageRequest.
    val maxWidth = if (tallStrip) context.resources.displayMetrics.widthPixels else 0
    val request = remember(page.imageUrl, attempt, maxWidth) { pageRequest(context, page, attempt, maxWidth) }
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
            Box(sizeModifier.background(Color(0xFF17171A)).clickable { attempt++ }, contentAlignment = Alignment.Center) {
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

private const val PREFETCH_PAGES = 4

/** Tallest a webtoon strip is decoded: at screen width this stays well under the 100 MB Android will draw. */
private const val MAX_STRIP_HEIGHT_PX = 12_000

/**
 * [maxWidthPx] > 0 (webtoon mode): the page is decoded to fit that width and [MAX_STRIP_HEIGHT_PX].
 * Left to its own size, one very long strip becomes a bitmap of more than 100 MB, and drawing it
 * closes the app ("Canvas: trying to draw too large bitmap"); capped, it is only a little softer.
 */
private fun pageRequest(context: Context, page: PageImage, attempt: Int = 0, maxWidthPx: Int = 0): ImageRequest =
    ImageRequest.Builder(context)
        .data(page.imageUrl)
        .apply {
            if (maxWidthPx > 0) {
                size(maxWidthPx, MAX_STRIP_HEIGHT_PX)
                scale(coil.size.Scale.FIT)
            }
            page.referer?.let { addHeader("Referer", it) }
            // A retry must bypass the memory cache entry of the failed attempt.
            if (attempt > 0) setParameter("retry", attempt)
        }
        .crossfade(true)
        .build()
