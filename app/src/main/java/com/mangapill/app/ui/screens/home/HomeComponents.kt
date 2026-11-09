package com.mangapill.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.model.Genres
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.SourceInfo
import com.mangapill.app.data.repository.HistoryEntry
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.MangaCover
import com.mangapill.app.ui.components.MangaPosterCard
import com.mangapill.app.ui.components.RatingBadge
import com.mangapill.app.ui.components.SectionHeader
import com.mangapill.app.ui.components.SourceBadge
import com.mangapill.app.ui.components.bleed
import com.mangapill.app.ui.components.shimmer
import com.mangapill.app.ui.components.sourceLabel
import com.mangapill.app.ui.components.glass.BrandGradient
import com.mangapill.app.ui.components.glass.GlassChip
import com.mangapill.app.ui.components.glass.GlassIconButton
import com.mangapill.app.ui.components.glass.GradientButton
import com.mangapill.app.ui.components.glass.IridescentBorder
import com.mangapill.app.ui.components.glass.LiquidGlassCard
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.screens.details.formatChapterNumber
import com.mangapill.app.ui.theme.GlassDark
import com.mangapill.app.ui.theme.PinkAccent
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

// ---- Header & search ----------------------------------------------------------------------------

/** Gradient wordmark. Drawn with SrcAtop so it works on every Compose version. */
@Composable
fun HomeHeader(modifier: Modifier = Modifier) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(modifier) {
        Text(greeting, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            "Mangapill",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .graphicsLayer(alpha = 0.99f)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(BrandGradient, blendMode = BlendMode.SrcAtop)
                    }
                }
        )
    }
}

@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    focused: Boolean,
    filterCount: Int,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderWidth by animateDpAsState(if (focused) 1.6.dp else 1.dp, label = "searchBorder")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .liquidGlass(RoundedCornerShape(26.dp), if (focused) 0.1f else 0.06f, base = Color(0x80000000), borderWidth = borderWidth)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Search manga, manhwa, webtoons…", color = TextMuted, fontSize = 15.sp, maxLines = 1)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(PurpleAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) }
                )
            }
            AnimatedVisibility(visible = query.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp).clip(CircleShape).clickable { onQueryChange("") }
                )
            }
        }
        Box {
            GlassIconButton(Icons.Default.Tune, "Filters", onOpenFilters, Modifier.size(52.dp), active = filterCount > 0)
            if (filterCount > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(18.dp).clip(CircleShape).background(PinkAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$filterCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SourceChipsRow(sources: List<SourceInfo>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item { GlassChip("All sources", selected == null, { onSelect(null) }) }
        items(sources, key = { it.id }) { s -> GlassChip(s.name, selected == s.id, { onSelect(s.id) }) }
    }
}

// ---- Live suggestions ---------------------------------------------------------------------------

@Composable
fun SuggestionsPanel(
    query: String,
    results: List<Manga>,
    pending: Boolean,
    recents: List<String>,
    onPick: (Manga) -> Unit,
    onRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(max = 460.dp)
            .liquidGlass(RoundedCornerShape(24.dp), 0.06f, base = GlassDark)
            .verticalScroll(rememberScrollState())
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            .padding(vertical = 8.dp)
    ) {
        if (query.isBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Clear", color = PurpleAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClearRecents))
            }
            recents.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().clickable { onRecent(r) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(r, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.NorthWest, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            SuggestionResults(query, results, pending, onPick, onSeeAll)
        }
    }
}

@Composable
private fun SuggestionResults(
    query: String,
    results: List<Manga>,
    pending: Boolean,
    onPick: (Manga) -> Unit,
    onSeeAll: () -> Unit
) {
    Column {
        val top = results.take(6)
        if (top.isEmpty()) {
            if (pending) {
                repeat(3) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(42.dp).height(60.dp).clip(RoundedCornerShape(10.dp)).shimmer())
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.width(160.dp).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                    }
                }
            } else {
                Text(
                    "No matches for \"${query.trim()}\"",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        top.forEach { m ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(m) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(42.dp).height(60.dp).clip(RoundedCornerShape(10.dp))) { MangaCover(m, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(highlight(m.title, query), color = TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(sourceLabel(m.sourceId), m.rating.takeIf { it > 0f }?.let { "★ %.1f".format(it) }).joinToString(" · "),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
        if (top.isNotEmpty() || pending) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onSeeAll).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pending) {
                    CircularProgressIndicator(color = PurpleAccent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    if (pending) "Searching more sources…" else "See all ${results.size} results",
                    color = PurpleAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun highlight(text: String, query: String): AnnotatedString = buildAnnotatedString {
    append(text)
    val q = query.trim()
    val i = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    if (i >= 0) addStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold), i, i + q.length)
}

// ---- Dashboard rows -----------------------------------------------------------------------------

/** Auto-advancing hero carousel; neighbouring slides peek in, shrink and fade with the scroll. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturedCarousel(
    items: List<Manga>,
    onOpen: (Manga) -> Unit,
    onPeek: (Manga) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState { items.size }
    LaunchedEffect(pagerState, items.size) {
        while (items.size > 1) {
            delay(5_000)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(
                    (pagerState.currentPage + 1) % items.size,
                    animationSpec = spring(stiffness = Spring.StiffnessVeryLow)
                )
            }
        }
    }
    Column(modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth().height(236.dp)
        ) { page ->
            FeaturedSlide(
                manga = items[page],
                rank = page + 1,
                onOpen = { onOpen(items[page]) },
                onPeek = { onPeek(items[page]) },
                modifier = Modifier.graphicsLayer {
                    val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    val scale = 1f - 0.08f * offset
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.4f * offset
                }
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                val selected = pagerState.currentPage == i
                val width by animateDpAsState(
                    if (selected) 22.dp else 6.dp,
                    spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                    label = "dot"
                )
                Box(
                    Modifier.padding(horizontal = 3.dp).width(width).height(6.dp).clip(CircleShape)
                        .then(if (selected) Modifier.background(BrandGradient) else Modifier.background(Color.White.copy(alpha = 0.2f)))
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedSlide(manga: Manga, rank: Int, onOpen: () -> Unit, onPeek: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxSize()
            .clip(shape)
            .combinedClickable(onClick = onOpen, onLongClick = onPeek)
    ) {
        MangaCover(manga, Modifier.fillMaxSize().blur(26.dp))
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xF20B0B0D), Color(0xB30B0B0D), Color(0x400B0B0D)))
            )
        )
        Box(Modifier.fillMaxSize().border(1.dp, IridescentBorder, shape))
        Row(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "#$rank TRENDING",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(BrandGradient).padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Text(
                        manga.title,
                        color = TextPrimary,
                        fontSize = 21.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SourceBadge(sourceLabel(manga.sourceId))
                        RatingBadge(manga.rating)
                    }
                }
                GradientButton(onClick = onOpen) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Read now", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier.width(116.dp).height(174.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            ) {
                MangaCover(manga, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun FeaturedSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 16.dp).fillMaxWidth().height(236.dp).clip(RoundedCornerShape(28.dp)).shimmer())
}

@Composable
fun MangaCarousel(
    title: String,
    subtitle: String?,
    items: List<Manga>,
    onOpen: (Manga) -> Unit,
    onPeek: (Manga) -> Unit
) {
    Column {
        SectionHeader(title, subtitle = subtitle)
        LazyRow(
            modifier = Modifier.bleed(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.key }) { m ->
                MangaPosterCard(m, onClick = { onOpen(m) }, modifier = Modifier.width(132.dp), onLongClick = { onPeek(m) })
            }
        }
    }
}

@Composable
fun ContinueReadingRow(entries: List<HistoryEntry>, onContinue: (HistoryEntry) -> Unit) {
    Column {
        SectionHeader("Continue reading", subtitle = "Pick up right where you left off")
        LazyRow(
            modifier = Modifier.bleed(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.manga.key }) { e -> ContinueReadingCard(e) { onContinue(e) } }
        }
    }
}

@Composable
private fun ContinueReadingCard(entry: HistoryEntry, onClick: () -> Unit) {
    val fraction = (entry.progress / 100f).coerceIn(0f, 1f)
    LiquidGlassCard(Modifier.width(272.dp), cornerRadius = 20.dp, onClick = onClick) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(62.dp).height(90.dp).clip(RoundedCornerShape(12.dp))) {
                MangaCover(entry.manga, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.manga.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "Chapter ${formatChapterNumber(entry.chapterNumber)} · page ${entry.lastReadPage}",
                    color = PurpleAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(BrandGradient))
                }
                Text("${(fraction * 100).toInt()}% · Continue", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

// ---- Filters ------------------------------------------------------------------------------------

private val TYPE_OPTIONS = listOf(null to "All", "manga" to "Manga", "manhwa" to "Manhwa", "manhua" to "Manhua")
private val STATUS_OPTIONS = listOf(null to "All", "ongoing" to "Ongoing", "completed" to "Completed", "hiatus" to "Hiatus")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(state: HomeState, repository: MangaRepository, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF217171A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 6.dp).width(40.dp).height(4.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                if (state.activeFilterCount > 0) {
                    Text(
                        "Reset",
                        color = PurpleAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { state.clearFilters() }.padding(8.dp)
                    )
                }
            }
            FilterGroup("Type", TYPE_OPTIONS, state.type, state::selectType)
            FilterGroup("Status", STATUS_OPTIONS, state.status, state::selectStatus)
            FilterGroup("Genre", listOf<Pair<String?, String>>(null to "All") + Genres.all.map { it.slug to it.name }, state.genre, state::selectGenre)

            val targets = repository.sourcesFor(state.filters, state.source)
            Text(
                if (targets.isEmpty()) "None of your enabled sources supports this combination. Enable more sources in Settings or remove a filter."
                else "Searching " + targets.joinToString { it.sourceName },
                color = if (targets.isEmpty()) PinkAccent else TextMuted,
                fontSize = 12.sp
            )
            GradientButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Show results", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, options: List<Pair<String?, String>>, selected: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) -> GlassChip(label, selected == value, { onSelect(value) }) }
        }
    }
}
