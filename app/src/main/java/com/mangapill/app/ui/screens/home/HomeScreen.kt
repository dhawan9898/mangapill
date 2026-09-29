package com.mangapill.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.glass.LiquidGlassCard
import com.mangapill.app.ui.theme.AmberRating
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    repository: MangaRepository,
    onMangaSelect: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var mangaList by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var trendingList by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val configuration = LocalConfiguration.current
    val gridColumns = if (configuration.screenWidthDp >= 600) 4 else 2

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            trendingList = repository.getLatestUpdates()
            mangaList = trendingList
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            isLoading = true
            try {
                mangaList = repository.searchManga(searchQuery)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        } else if (searchQuery.isEmpty()) {
            mangaList = trendingList
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Search Header
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search manga, webtoons...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = PurpleAccent) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PurpleAccent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PurpleAccent)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Discovery Carousel Header (Only when not searching)
                if (searchQuery.isEmpty() && trendingList.isNotEmpty()) {
                    item(span = { GridItemSpan(gridColumns) }) {
                        Column {
                            Text(
                                text = "Trending Today",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                items(trendingList.take(8)) { manga ->
                                    TrendingMangaCard(
                                        manga = manga,
                                        onClick = { onMangaSelect(manga.id) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Explore Library",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }

                // Grid Items
                items(mangaList) { manga ->
                    MangaGridCard(
                        manga = manga,
                        onClick = { onMangaSelect(manga.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun TrendingMangaCard(
    manga: Manga,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(manga.coverImage) {
        ImageRequest.Builder(context)
            .data(manga.coverImage)
            .addHeader("Referer", "https://mangapill.com")
            .crossfade(true)
            .build()
    }

    LiquidGlassCard(
        modifier = Modifier
            .width(140.dp)
            .height(210.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = manga.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = AmberRating,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("4.8", color = AmberRating, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                text = manga.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun MangaGridCard(
    manga: Manga,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(manga.coverImage) {
        ImageRequest.Builder(context)
            .data(manga.coverImage)
            .addHeader("Referer", "https://mangapill.com")
            .crossfade(true)
            .build()
    }

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = manga.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = manga.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}
