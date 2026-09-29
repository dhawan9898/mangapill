package com.mangapill.app.ui.screens.history

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.repository.HistoryEntry
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.MangaCover
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.navigation.bottomContentPadding
import com.mangapill.app.ui.components.StateMessage
import com.mangapill.app.ui.components.sourceLabel
import com.mangapill.app.ui.screens.details.formatChapterNumber
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(repository: MangaRepository, onContinue: (HistoryEntry) -> Unit) {
    val entries by remember { repository.historyEntries() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
            if (entries.isNotEmpty()) {
                IconButton(onClick = { scope.launch { repository.clearHistory() } }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history", tint = TextSecondary)
                }
            }
        }

        if (entries.isEmpty()) {
            StateMessage(
                icon = Icons.Default.History,
                title = "Nothing read yet",
                message = "Chapters you open will show up here so you can pick up where you left off.",
                modifier = Modifier.padding(top = 48.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = bottomContentPadding())
            ) {
                items(entries, key = { it.manga.key }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(RoundedCornerShape(18.dp), 0.06f)
                            .clickable { onContinue(entry) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(56.dp).height(82.dp).clip(RoundedCornerShape(10.dp))) {
                            MangaCover(entry.manga, Modifier.fillMaxSize())
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                entry.manga.title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Chapter ${formatChapterNumber(entry.chapterNumber)} · page ${entry.lastReadPage}",
                                color = PurpleAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            LinearProgressIndicator(
                                progress = { (entry.progress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                                color = PurpleAccent,
                                trackColor = PurpleAccent.copy(alpha = 0.25f)
                            )
                            Text(
                                "${sourceLabel(entry.manga.sourceId)} · " +
                                    DateUtils.getRelativeTimeSpanString(entry.lastReadAt).toString(),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
