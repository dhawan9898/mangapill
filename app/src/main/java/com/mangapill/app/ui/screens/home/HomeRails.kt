package com.mangapill.app.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.ui.components.MangaCover
import com.mangapill.app.ui.components.MangaPosterCard
import com.mangapill.app.ui.components.PosterSkeleton
import com.mangapill.app.ui.components.glass.IridescentBorder
import com.mangapill.app.ui.components.glass.PrimaryButton
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.components.shimmer
import com.mangapill.app.ui.theme.OnImage
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

// The home screen's building blocks, laid out like AniStream's and CineStream's: a billboard
// carousel, a Top 10 rail with big outlined numbers, and poster rails with "See all".

/** Billboard carousel: cover art, rank badge, title, genres, a line of synopsis, Read / Info / My List. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    items: List<Manga>,
    isSaved: (Manga) -> Boolean,
    onRead: (Manga) -> Unit,
    onInfo: (Manga) -> Unit,
    onToggleSave: (Manga) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState { items.size }
    LaunchedEffect(pagerState, items.size) {
        while (items.size > 1) {
            delay(6_000)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size, animationSpec = spring(stiffness = Spring.StiffnessVeryLow))
            }
        }
    }
    Column(modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth().height(440.dp)
        ) { page ->
            val m = items[page]
            HeroSlide(
                manga = m,
                rank = page + 1,
                saved = isSaved(m),
                onRead = { onRead(m) },
                onInfo = { onInfo(m) },
                onToggleSave = { onToggleSave(m) },
                modifier = Modifier.graphicsLayer {
                    val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    val scale = 1f - 0.06f * offset
                    scaleX = scale
                    scaleY = scale
                }
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                val selected = pagerState.currentPage == i
                val width by animateDpAsState(if (selected) 18.dp else 6.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "heroDot")
                Box(
                    Modifier.padding(horizontal = 3.dp).width(width).height(6.dp).clip(CircleShape)
                        .background(if (selected) TextPrimary else TextMuted.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun HeroSlide(
    manga: Manga,
    rank: Int,
    saved: Boolean,
    onRead: () -> Unit,
    onInfo: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.fillMaxSize().clip(shape).clickable(onClick = onInfo)) {
        MangaCover(manga, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color(0x33000000), 1f to Color(0xF0000000))
            )
        )
        Box(Modifier.fillMaxSize().border(1.dp, IridescentBorder, shape))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge("#$rank Popular", PurpleAccent)
                statusLabel(manga.status)?.let { Badge(it, Color(0x66000000), outlined = true) }
                if (manga.rating > 0f) Badge("★ %.1f".format(manga.rating), Color(0x66000000), outlined = true)
            }
            Text(manga.title, color = OnImage, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (manga.genres.isNotEmpty()) {
                Text(manga.genres.take(3).joinToString(" · "), color = OnImage.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            if (manga.synopsis.isNotBlank()) {
                Text(manga.synopsis, color = OnImage.copy(alpha = 0.75f), fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(onClick = onRead, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Read", fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier
                        .liquidGlass(RoundedCornerShape(16.dp), 0.1f, base = Color(0x66000000), overImage = true)
                        .clickable(onClick = onInfo)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = OnImage, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Info", color = OnImage, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .liquidGlass(CircleShape, 0.1f, base = if (saved) PurpleAccent else Color(0x66000000), overImage = true)
                        .clickable(onClick = onToggleSave),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (saved) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = if (saved) "Remove from My List" else "Add to My List",
                        tint = OnImage
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, background: Color, outlined: Boolean = false) {
    Text(
        text,
        color = OnImage,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .then(if (outlined) Modifier.border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

private fun statusLabel(status: MangaStatus): String? = when (status) {
    MangaStatus.ONGOING -> "Ongoing"
    MangaStatus.COMPLETED -> "Completed"
    MangaStatus.HIATUS -> "Hiatus"
    else -> null
}

@Composable
fun HeroSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 16.dp).fillMaxWidth().height(440.dp).clip(RoundedCornerShape(24.dp)).shimmer())
}

/** Section title with an optional subtitle and "See all". */
@Composable
fun RailHeader(title: String, subtitle: String? = null, onSeeAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onSeeAll != null) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("See all", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A row of posters; skeletons while it loads. */
@Composable
fun PosterRail(
    title: String,
    items: List<Manga>?,
    onOpen: (Manga) -> Unit,
    onPeek: (Manga) -> Unit,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null
) {
    Column {
        RailHeader(title, subtitle, onSeeAll)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (items == null) {
                items(5) { PosterSkeleton(Modifier.width(120.dp)) }
            } else {
                items(items.size, key = { items[it].key }) { i ->
                    val m = items[i]
                    MangaPosterCard(m, onClick = { onOpen(m) }, modifier = Modifier.width(120.dp), onLongClick = { onPeek(m) })
                }
            }
        }
    }
}

/** Top 10: each poster with its rank drawn as a large outlined number beside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopTenRail(title: String, subtitle: String?, items: List<Manga>?, onOpen: (Manga) -> Unit, onPeek: (Manga) -> Unit) {
    Column {
        RailHeader(title, subtitle)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (items == null) {
                items(4) { PosterSkeleton(Modifier.padding(start = 40.dp).width(112.dp)) }
            } else {
                itemsIndexed(items.take(10), key = { _, m -> "top:" + m.key }) { i, m ->
                    Box(Modifier.width(158.dp).height(172.dp).combinedClickable(onClick = { onOpen(m) }, onLongClick = { onPeek(m) })) {
                        Text(
                            "${i + 1}",
                            style = TextStyle(
                                fontSize = 130.sp,
                                fontWeight = FontWeight.Black,
                                color = TextMuted,
                                drawStyle = Stroke(width = 5f)
                            ),
                            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-6).dp, y = 22.dp)
                        )
                        Box(
                            Modifier.align(Alignment.CenterEnd).width(112.dp).height(168.dp)
                                .clip(RoundedCornerShape(14.dp)).border(1.dp, IridescentBorder, RoundedCornerShape(14.dp))
                        ) {
                            MangaCover(m, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
