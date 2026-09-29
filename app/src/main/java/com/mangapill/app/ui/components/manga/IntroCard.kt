package com.mangapill.app.ui.components.manga

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.MangaStatus
import com.mangapill.app.data.model.ReadingHistory
import com.mangapill.app.ui.components.glass.LiquidGlassCard
import com.mangapill.app.ui.theme.AmberRating
import com.mangapill.app.ui.theme.BlueStatus
import com.mangapill.app.ui.theme.EmeraldStatus
import com.mangapill.app.ui.theme.IndigoAccent
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntroCard(
    manga: Manga,
    recentChapters: List<Chapter>,
    userProgress: ReadingHistory?,
    isBookmarked: Boolean,
    onStartReading: (Chapter) -> Unit,
    onBookmarkToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var synopsisExpanded by remember { mutableStateOf(false) }
    var chapterDrawerOpen by remember { mutableStateOf(false) }

    val imageRequest = remember(manga.coverImage) {
        ImageRequest.Builder(context)
            .data(manga.coverImage)
            .addHeader("Referer", "https://mangapill.com")
            .crossfade(true)
            .build()
    }

    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cover Image Container
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = manga.title,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Rating Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = AmberRating,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = String.format("%.1f", manga.rating),
                                color = AmberRating,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Status Badge
                    val statusColor = if (manga.status == MangaStatus.ONGOING) EmeraldStatus else BlueStatus
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.25f))
                            .border(1.dp, statusColor.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = manga.status.name,
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Manga Details & Actions
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = manga.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = onBookmarkToggle,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isBookmarked) PurpleAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f)
                                )
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) PurpleAccent else TextSecondary
                            )
                        }
                    }

                    // Genres
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        manga.genres.take(4).forEach { genre ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = genre,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // User Reading Progress
                    userProgress?.let { history ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PurpleAccent.copy(alpha = 0.12f))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Ch. ${history.chapterNumber}",
                                    color = PurpleAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${history.scrollPercentage.toInt()}%",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (history.scrollPercentage / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = PurpleAccent,
                                trackColor = PurpleAccent.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            // Synopsis
            if (manga.synopsis.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = manga.synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (synopsisExpanded) "Show Less" else "Read Synopsis",
                    color = PurpleAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { synopsisExpanded = !synopsisExpanded }
                        .padding(vertical = 4.dp)
                )
            }

            // Action Buttons Row
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val targetChapter = recentChapters.firstOrNull()
                Button(
                    onClick = { targetChapter?.let { onStartReading(it) } },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Read",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (userProgress != null) "Resume" else "Start Reading",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { chapterDrawerOpen = !chapterDrawerOpen },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Chapters", color = TextPrimary, fontSize = 13.sp)
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Drawer",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Quick Chapter Drawer
            AnimatedVisibility(
                visible = chapterDrawerOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = "Recent Releases",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        recentChapters.take(5).forEach { ch ->
                            val isRead = userProgress != null && userProgress.chapterNumber >= ch.chapterNumber
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isRead) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable { onStartReading(ch) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isRead) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Read",
                                            tint = EmeraldStatus,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = "Chapter ${ch.chapterNumber}",
                                        color = if (isRead) TextMuted else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                ch.releaseDate?.let { date ->
                                    Text(text = date, color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
