package com.mangapill.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mangapill.app.data.model.Manga
import com.mangapill.app.ui.components.glass.LiquidGlassCard
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
            colors = listOf(Color(0xFF14141C), Color(0xFF242432), Color(0xFF14141C)),
            start = Offset(x, 0f),
            end = Offset(x + 400f, 400f)
        )
    )
}

@Composable
fun CoverFallback(modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF1A1A24)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = TextMuted, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun CoverImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f
) {
    if (url.isBlank()) {
        CoverFallback(modifier)
        return
    }
    val context = LocalContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(true).build() }
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
fun SourceBadge(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
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
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Star, contentDescription = "Rating", tint = AmberRating, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(String.format("%.1f", rating), color = AmberRating, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

fun sourceLabel(sourceId: String): String = when (sourceId) {
    "mangapill" -> "Mangapill"
    "mangadex" -> "MangaDex"
    else -> sourceId
}

private val ScrimBrush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0xCC000000), Color(0xF2000000))
)

/** Poster card: cover art with the title over a bottom scrim. */
@Composable
fun MangaPosterCard(
    manga: Manga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(modifier = modifier, cornerRadius = 16.dp, onClick = onClick) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            CoverImage(manga.coverImage, manga.title, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(90.dp).background(ScrimBrush))
            SourceBadge(sourceLabel(manga.sourceId), Modifier.align(Alignment.TopStart).padding(8.dp))
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
    Box(modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).shimmer())
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
            Modifier.size(64.dp).clip(CircleShape).background(PurpleAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(30.dp))
        }
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(message, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel, fontWeight = FontWeight.Bold)
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
        e is java.net.SocketTimeoutException || msg.contains("timeout", true) ->
            "The source took too long to respond."
        msg.isNotBlank() -> msg
        else -> "Something went wrong."
    }
}
