package com.mangapill.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ---- Themes ---------------------------------------------------------------------------------------
// The same four themes as AniStream and CineStream (index.css: dark, obsidian = OLED, slate, light),
// with the same values, so the apps look alike whichever one is picked.

enum class AppTheme(val label: String) { DARK("Dark"), OLED("OLED"), SLATE("Slate"), LIGHT("Light") }

@Immutable
data class Palette(
    val isLight: Boolean,
    /** Page background (--bg-main). */
    val background: Color,
    /** Plain surfaces: buttons, sheets (--bg-surface). */
    val surface: Color,
    /** Raised / hovered surfaces, shimmer highlight (--bg-surface-hover). */
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    /** Dense base under floating chrome: the dock, the rail, popovers (--bg-dock). */
    val chrome: Color,
    /** Hairlines and card borders (--border-subtle). */
    val border: Color,
    /** Frosted lens behind a selected tab / segment / chip (--select-bg / --select-border). */
    val selectFill: Color,
    val selectBorder: Color,
    /** Off switch track and empty progress tracks (--track-bg). */
    val track: Color,
    /** Faint red glow at the top of the page. */
    val glowAlpha: Float
)

val DarkPalette = Palette(
    isLight = false,
    background = Color(0xFF0B0B0D), surface = Color(0xFF17171A), card = Color(0xFF212126),
    textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFFA8A8B3), textMuted = Color(0xFF6F6F7B),
    chrome = Color(0xE616161C), border = Color(0x14FFFFFF),
    selectFill = Color(0x29FFFFFF), selectBorder = Color(0x47FFFFFF), track = Color(0x17FFFFFF),
    glowAlpha = 0.10f
)

val OledPalette = DarkPalette.copy(
    background = Color(0xFF000000), surface = Color(0xFF0A0A0E), card = Color(0xFF14141C),
    chrome = Color(0xF2000000), border = Color(0x1CFFFFFF), glowAlpha = 0.07f
)

val SlatePalette = DarkPalette.copy(
    background = Color(0xFF0A1120), surface = Color(0xFF131E33), card = Color(0xFF1E2E4D),
    textPrimary = Color(0xFFF8FAFC), textSecondary = Color(0xFF94A3B8), textMuted = Color(0xFF64748B),
    chrome = Color(0xE60E1629), border = Color(0x1AFFFFFF), glowAlpha = 0.08f
)

val LightPalette = Palette(
    isLight = true,
    background = Color(0xFFF2F3F7), surface = Color(0xFFFFFFFF), card = Color(0xFFECEEF5),
    textPrimary = Color(0xFF0F172A), textSecondary = Color(0xFF52607A), textMuted = Color(0xFF7B879C),
    chrome = Color(0xE0FFFFFF), border = Color(0x140F172A),
    selectFill = Color(0xEBFFFFFF), selectBorder = Color(0x140F172A), track = Color(0x0F0F172A),
    glowAlpha = 0.06f
)

fun AppTheme.palette(): Palette = when (this) {
    AppTheme.DARK -> DarkPalette
    AppTheme.OLED -> OledPalette
    AppTheme.SLATE -> SlatePalette
    AppTheme.LIGHT -> LightPalette
}

val LocalPalette = compositionLocalOf { DarkPalette }

/** Settings → Transparent Glass: floating bars, buttons and tiles turn see-through. */
val LocalClearGlass = compositionLocalOf { false }

// ---- Theme-dependent colours (read from the current palette) ----------------------------------

val OledBackground: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
val OledSurface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
val OledCard: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textSecondary
val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textMuted
val GlassDark: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.chrome
val SelectFill: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.selectFill
val SelectBorder: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.selectBorder
val BorderSubtle: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.border
val TrackOff: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.track
/** Plain surface for secondary pill buttons (--bg-surface). */
val Surface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface

// ---- Fixed colours (the same in every theme) ---------------------------------------------------

/** Text and icons drawn over cover art and its dark scrim: always white. */
val OnImage = Color(0xFFFFFFFF)

val PurpleAccent = Color(0xFFE50914)
val PurpleAccentDark = Color(0xFFB20710)
val IndigoAccent = Color(0xFFFF3B45)

val AmberRating = Color(0xFFFFB800)
val EmeraldStatus = Color(0xFF10B981)
val BlueStatus = Color(0xFF3B82F6)

val CyanAccent = Color(0xFFFF3B45)
val PinkAccent = Color(0xFFF59E0B)

/** Tinted square behind a setting's icon (--accent-soft). */
val AccentSoft = Color(0x24E50914)
/** iOS-style "on" switch track, as in the other two apps. */
val ToggleOn = Color(0xFF34C759)
