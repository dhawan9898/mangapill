package com.mangapill.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun MangapillTheme(
    theme: AppTheme = AppTheme.DARK,
    clearGlass: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = theme.palette()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.background.toArgb()
            // Dark status / navigation bar icons on the light theme.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = palette.isLight
                isAppearanceLightNavigationBars = palette.isLight
            }
        }
    }

    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = PurpleAccent, secondary = IndigoAccent, background = palette.background,
            surface = palette.surface, surfaceVariant = palette.card, onPrimary = OnImage,
            onBackground = palette.textPrimary, onSurface = palette.textPrimary, onSurfaceVariant = palette.textSecondary
        )
    } else {
        darkColorScheme(
            primary = PurpleAccent, secondary = IndigoAccent, background = palette.background,
            surface = palette.surface, surfaceVariant = palette.card, onPrimary = OnImage,
            onBackground = palette.textPrimary, onSurface = palette.textPrimary, onSurfaceVariant = palette.textSecondary
        )
    }

    CompositionLocalProvider(LocalPalette provides palette, LocalClearGlass provides clearGlass) {
        MaterialTheme(colorScheme = scheme, typography = outfitTypography(), content = content)
    }
}

/** Content that is always dark whatever the theme (the reader, over a black page). */
@Composable
fun AlwaysDark(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides DarkPalette, content = content)
}
