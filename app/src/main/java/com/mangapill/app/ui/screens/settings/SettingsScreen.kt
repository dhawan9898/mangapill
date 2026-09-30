package com.mangapill.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.components.glass.LiquidGlassCard
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.navigation.bottomContentPadding
import com.mangapill.app.ui.theme.PinkAccent
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(repository: MangaRepository) {
    val context = LocalContext.current
    val enabled by repository.prefs.enabledSources.collectAsState()
    // Read so the source list below recomposes when a server is added or removed.
    val servers by repository.prefs.opdsServers.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomContentPadding())
    ) {
        Text(
            "Settings",
            color = TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
        )

        OpdsServersCard(repository)

        Spacer(Modifier.height(14.dp))
        LiquidGlassCard(Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sources", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Enabled sources are searched together. Experimental ones could not be verified and may be " +
                        "blocked, slow or broken; a source that fails is skipped for a few minutes automatically.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(8.dp))
                remember(servers) { repository.sources }.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (s.experimental) {
                                Text(
                                    "Experimental",
                                    color = PinkAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .liquidGlass(RoundedCornerShape(6.dp), 0.04f)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Switch(
                            checked = s.id in enabled,
                            onCheckedChange = { on ->
                                if (!repository.prefs.setSourceEnabled(s.id, on)) {
                                    Toast.makeText(context, "Keep at least one source enabled", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PurpleAccent,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                                uncheckedBorderColor = Color.White.copy(alpha = 0.2f),
                                uncheckedThumbColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        LiquidGlassCard(Modifier.fillMaxWidth(), cornerRadius = 24.dp, onClick = {
            repository.prefs.clearRecentSearches()
            Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
        }) {
            Column(Modifier.padding(18.dp)) {
                Text("Clear search history", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Removes the recent searches shown under the search bar.", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}
