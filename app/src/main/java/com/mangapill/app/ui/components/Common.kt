package com.mangapill.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.scraper.SourceRegistry
import com.mangapill.app.ui.components.glass.GradientButton
import com.mangapill.app.ui.components.glass.LiquidGlassCard
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.theme.AmberRating
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary

fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerX"
    )
    background(
        Brush.linearGradient(
            colors = listOf(Color(0xFF17171A), Color(0xFF26262C), Color(0xFF17171A)),
            start = Offset(x, 0f),
            end = Offset(x + 400f, 400f)
        )
    )
}

/** Lets a full-width child of a padded lazy grid extend to the screen edges (edge-to-edge carousels). */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-horizontal.roundToPx(), 0) }
}

fun sourceLabel(sourceId: String): String = SourceRegistry.name(sourceId)

fun coverReferer(manga: Manga): String? = SourceRegistry.imageReferer(manga.sourceId)

@Composable
fun CoverFallback(modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF1E1E22)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = TextMuted, modifier = Modifier.size(28.dp))
    }
}

/** Cover art with shimmer while loading, a fallback on error, and the Referer the source's CDN expects. */
@Composable
fun CoverImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    referer: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f
) {
    if (url.isBlank()) {
        CoverFallback(modifier)
        return
    }
    val context = LocalContext.current
    val request = remember(url, referer) {
        ImageRequest.Builder(context)
            .data(url)
            .apply { if (referer != null) addHeader("Referer", referer) }
            .crossfade(250)
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha,
        loading = { Box(Modifier.fillMaxSize().shimmer()) },
        error = { CoverFallback(Modifier.fillMaxSize()) }
    )
}

@Composable
fun MangaCover(manga: Manga, modifier: Modifier = Modifier, alpha: Float = 1f) {
    CoverImage(manga.coverImage, manga.title, modifier, referer = coverReferer(manga), alpha = alpha)
}

@Composable
fun SourceBadge(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .liquidGlass(RoundedCornerShape(8.dp), 0.08f, base = Color(0x8C000000))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(name, color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RatingBadge(rating: Float, modifier: Modifier = Modifier) {
    if (rating <= 0f) return
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Star, contentDescription = "Rating", tint = AmberRating, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(String.format("%.1f", rating), color = AmberRating, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private val ScrimBrush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0xB3000000), Color(0xF0000000))
)

/** Poster card: cover art with the title over a bottom scrim. Long-press opens the quick peek. */
@Composable
fun MangaPosterCard(
    manga: Manga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    LiquidGlassCard(modifier = modifier, cornerRadius = 18.dp, onClick = onClick, onLongClick = onLongClick) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            MangaCover(manga, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(96.dp).background(ScrimBrush))
            RatingBadge(manga.rating, Modifier.align(Alignment.TopEnd).padding(8.dp))
            Text(
                text = manga.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
            )
        }
    }
}

@Composable
fun PosterSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(18.dp)).shimmer())
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (action != null && onAction != null) {
            Text(
                action,
                color = PurpleAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp)
            )
        }
    }
}

@Composable
fun StateMessage(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
    ) {
        Box(
            Modifier.size(68.dp).liquidGlass(CircleShape, 0.08f),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(30.dp))
        }
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(message, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        if (actionLabel != null && onAction != null) {
            GradientButton(onClick = onAction) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Turns a raw exception message into something a person can act on. */
fun friendlyError(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        e is java.net.UnknownHostException || msg.contains("Unable to resolve host") ->
            "No internet connection. Check your network and try again."
        e is java.net.SocketTimeoutException || msg.contains("timeout", true) || msg.contains("timed out", true) ->
            "The source took too long to respond."
        msg.isNotBlank() -> msg
        else -> "Something went wrong."
    }
}
