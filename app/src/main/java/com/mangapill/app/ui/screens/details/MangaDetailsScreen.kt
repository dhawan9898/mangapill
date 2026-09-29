package com.mangapill.app.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.manga.IntroCard
import com.mangapill.app.ui.theme.OledBackground
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun MangaDetailsScreen(
    mangaId: String,
    repository: MangaRepository,
    onBack: () -> Unit,
    onChapterSelect: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var manga by remember { mutableStateOf<Manga?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val userProgress by repository.getReadingHistory(mangaId).collectAsState(initial = null)
    val isBookmarked by repository.isBookmarked(mangaId).collectAsState(initial = false)

    LaunchedEffect(mangaId) {
        isLoading = true
        try {
            manga = repository.getMangaDetails(mangaId)
            chapters = repository.getChapterList(mangaId)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    val filteredChapters = remember(chapters, searchQuery) {
        if (searchQuery.isBlank()) chapters
        else chapters.filter { it.title?.contains(searchQuery, ignoreCase = true) == true || it.chapterNumber.toString().contains(searchQuery) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBackground)
            .padding(16.dp)
    ) {
        // Back Header
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }

        if (isLoading || manga == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PurpleAccent)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Interactive Intro Card
                item {
                    IntroCard(
                        manga = manga!!,
                        recentChapters = chapters,
                        userProgress = userProgress,
                        isBookmarked = isBookmarked,
                        onStartReading = { chapter -> onChapterSelect(chapter.id) },
                        onBookmarkToggle = {
                            coroutineScope.launch {
                                if (isBookmarked) repository.removeBookmark(mangaId)
                                else repository.toggleBookmark(mangaId)
                            }
                        }
                    )
                }

                // Chapter Search & List Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All Chapters (${chapters.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search chapter number or title...", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurpleAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }

                // Chapters
                items(filteredChapters) { chapter ->
                    val isRead = userProgress != null && userProgress!!.chapterNumber >= chapter.chapterNumber
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isRead) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.08f))
                            .clickable { onChapterSelect(chapter.id) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Chapter ${chapter.chapterNumber}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRead) TextMuted else TextPrimary
                            )
                            chapter.title?.let { t ->
                                Text(text = t, fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                        chapter.releaseDate?.let { date ->
                            Text(text = date, fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}
