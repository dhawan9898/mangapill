package com.mangapill.app.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.MangaPosterCard
import com.mangapill.app.ui.components.StateMessage
import com.mangapill.app.ui.theme.TextPrimary

@Composable
fun LibraryScreen(repository: MangaRepository, onMangaSelect: (Manga) -> Unit) {
    val library by remember { repository.bookmarkedManga() }.collectAsState(initial = emptyList())
    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 5 else 2

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Text(
            "Library",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
        )
        if (library.isEmpty()) {
            StateMessage(
                icon = Icons.Default.BookmarkBorder,
                title = "Your library is empty",
                message = "Tap the bookmark on any title to keep it here.",
                modifier = Modifier.padding(top = 48.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(library, key = { it.key }) { manga ->
                    MangaPosterCard(manga = manga, onClick = { onMangaSelect(manga) })
                }
            }
        }
    }
}
