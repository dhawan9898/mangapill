package com.mangapill.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.model.SourceInfo
import com.mangapill.app.ui.components.glass.LiquidGlassCard
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(sources: List<SourceInfo>) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Text(
            "Settings",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
        )
        LiquidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Sources", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                sources.forEach { Text("• ${it.name}", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Use the source chips on the Home screen to browse a single provider. If one source is " +
                        "unreachable, the others keep working.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
