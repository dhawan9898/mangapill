package com.mangapill.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.mangapill.app.data.model.Manga
import com.mangapill.app.data.model.SourceInfo
import com.mangapill.app.ui.components.MangaPosterCard
import com.mangapill.app.ui.components.PosterSkeleton
import com.mangapill.app.ui.components.StateMessage
import com.mangapill.app.ui.theme.OledBackground
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onMangaSelect: (Manga) -> Unit
) {
    LaunchedEffect(Unit) { state.start() }

    val focus = LocalFocusManager.current
    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 5 else 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Mangapill",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = state::updateQuery,
            placeholder = { Text("Search manga, manhwa, webtoons…", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = PurpleAccent) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { state.updateQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PurpleAccent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = PurpleAccent
            )
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 6.dp)
        ) {
            item { CustomFilterChip("All Types", state.type == null) { state.selectType(null) } }
            item { CustomFilterChip("Manga", state.type == "manga") { state.selectType("manga") } }
            item { CustomFilterChip("Manhwa", state.type == "manhwa") { state.selectType("manhwa") } }
            item { CustomFilterChip("Manhua", state.type == "manhua") { state.selectType("manhua") } }
        }
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 6.dp)
        ) {
            item { CustomFilterChip("All Status", state.status == null) { state.selectStatus(null) } }
            item { CustomFilterChip("Ongoing", state.status == "ongoing") { state.selectStatus("ongoing") } }
            item { CustomFilterChip("Completed", state.status == "completed") { state.selectStatus("completed") } }
            item { CustomFilterChip("Hiatus", state.status == "hiatus") { state.selectStatus("hiatus") } }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            item { CustomFilterChip("All Genres", state.genre == null) { state.selectGenre(null) } }
            item { CustomFilterChip("Action", state.genre == "action") { state.selectGenre("action") } }
            item { CustomFilterChip("Romance", state.genre == "romance") { state.selectGenre("romance") } }
            item { CustomFilterChip("Fantasy", state.genre == "fantasy") { state.selectGenre("fantasy") } }
            item { CustomFilterChip("Comedy", state.genre == "comedy") { state.selectGenre("comedy") } }
        }

        if (state.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = PurpleAccent,
                trackColor = Color.Transparent
            )
        }

        val gridItems = if (state.isSearching) state.results else state.latest
        val showSkeleton = state.loading && gridItems.isEmpty() && state.trending.isEmpty()

        LazyVerticalGrid(
            state = state.gridState,
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                showSkeleton -> items(6) { PosterSkeleton() }

                state.error != null && gridItems.isEmpty() && state.trending.isEmpty() -> item(span = { GridItemSpan(columns) }) {
                    StateMessage(
                        icon = Icons.Default.CloudOff,
                        title = "Couldn't load manga",
                        message = state.error.orEmpty(),
                        actionLabel = "Try again",
                        onAction = state::retry
                    )
                }

                state.isSearching && gridItems.isEmpty() && !state.loading -> item(span = { GridItemSpan(columns) }) {
                    StateMessage(
                        icon = Icons.Default.SearchOff,
                        title = "No results",
                        message = "Nothing matched \"${state.query.trim()}\". Try another title or switch source."
                    )
                }

                else -> {
                    if (!state.isSearching && state.trending.isNotEmpty()) {
                        item(span = { GridItemSpan(columns) }) {
                            Column {
                                SectionTitle("Trending now")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.trending.take(12)) { manga ->
                                        MangaPosterCard(
                                            manga = manga,
                                            onClick = { onMangaSelect(manga) },
                                            modifier = Modifier.width(140.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(18.dp))
                            }
                        }
                    }

                    if (gridItems.isNotEmpty()) {
                        item(span = { GridItemSpan(columns) }) {
                            SectionTitle(if (state.isSearching) "Results (${gridItems.size})" else "Latest updates")
                        }
                        items(gridItems, key = { it.key }) { manga ->
                            MangaPosterCard(manga = manga, onClick = { onMangaSelect(manga) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.05f),
            labelColor = TextSecondary,
            selectedContainerColor = PurpleAccent.copy(alpha = 0.25f),
            selectedLabelColor = PurpleAccent
        ),
        border = BorderStroke(1.dp, if (selected) PurpleAccent else Color.White.copy(alpha = 0.12f))
    )
}
