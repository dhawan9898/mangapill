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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.model.ReaderMode
import com.mangapill.app.data.model.ReadingHistory
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.theme.OledBackground
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DualModeReaderScreen(
    mangaId: String,
    chapterId: String,
    repository: MangaRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pages by remember { mutableStateOf<List<PageImage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var readerMode by remember { mutableStateOf(ReaderMode.WEBTOON) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(chapterId) {
        isLoading = true
        try {
            pages = repository.getChapterPages(chapterId)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PurpleAccent)
            }
        } else if (pages.isNotEmpty()) {
            when (readerMode) {
                ReaderMode.WEBTOON -> {
                    val listState = rememberLazyListState()

                    // Track scroll percentage
                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                            .collectLatest { (index, _) ->
                                val progress = ((index + 1).toFloat() / pages.size.toFloat() * 100f).coerceIn(0f, 100f)
                                repository.saveReadingHistory(
                                    ReadingHistory(
                                        mangaId = mangaId,
                                        chapterId = chapterId,
                                        chapterNumber = chapterId.toFloatOrNull() ?: 1f,
                                        lastReadPage = index + 1,
                                        scrollPercentage = progress
                                    )
                                )
                            }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(pages) { _, page ->
                            val request = remember(page.imageUrl) {
                                ImageRequest.Builder(context)
                                    .data(page.imageUrl)
                                    .addHeader("Referer", page.referer ?: "https://mangapill.com")
                                    .crossfade(true)
                                    .build()
                            }
                            AsyncImage(
                                model = request,
                                contentDescription = "Page ${page.pageNumber}",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }

                ReaderMode.MANGA_HORIZONTAL -> {
                    val pagerState = rememberPagerState(pageCount = { pages.size })

                    LaunchedEffect(pagerState.currentPage) {
                        val progress = ((pagerState.currentPage + 1).toFloat() / pages.size.toFloat() * 100f).coerceIn(0f, 100f)
                        repository.saveReadingHistory(
                            ReadingHistory(
                                mangaId = mangaId,
                                chapterId = chapterId,
                                chapterNumber = chapterId.toFloatOrNull() ?: 1f,
                                lastReadPage = pagerState.currentPage + 1,
                                scrollPercentage = progress
                            )
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val page = pages[pageIndex]
                        val request = remember(page.imageUrl) {
                            ImageRequest.Builder(context)
                                .data(page.imageUrl)
                                .addHeader("Referer", page.referer ?: "https://mangapill.com")
                                .crossfade(true)
                                .build()
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = request,
                                contentDescription = "Page ${page.pageNumber}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }

        // HUD Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        text = "Chapter Details",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Mode Switcher Toggle
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
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Switch Mode",
                        tint = PurpleAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (readerMode == ReaderMode.WEBTOON) "Webtoon" else "Horizontal",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
