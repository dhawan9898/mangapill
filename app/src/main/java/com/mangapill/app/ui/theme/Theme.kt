package com.mangapill.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val DarkColorScheme = darkColorScheme(
    primary = PurpleAccent,
    secondary = IndigoAccent,
    background = OledBackground,
    surface = OledSurface,
    surfaceVariant = OledCard,
    onPrimary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun MangapillTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = OledBackground.toArgb()
            window.navigationBarColor = OledBackground.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
