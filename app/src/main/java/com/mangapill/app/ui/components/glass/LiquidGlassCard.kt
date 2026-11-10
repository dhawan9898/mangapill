package com.mangapill.app.ui.components.glass

import androidx.compose.ui.graphics.luminance
import com.mangapill.app.ui.theme.LocalClearGlass
import com.mangapill.app.ui.theme.LocalPalette

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mangapill.app.ui.theme.OledBackground
import com.mangapill.app.ui.theme.PurpleAccent

/** Iridescent rim used on every glass surface (dark themes). */
val IridescentBorder = Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x0FFFFFFF), Color(0x1FFFFFFF)))
private val LightRim = Brush.linearGradient(listOf(Color(0x1F0F172A), Color(0x0F0F172A), Color(0x1A0F172A)))
private val ClearRim = Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x33FFFFFF), Color(0x52FFFFFF)))

val BrandGradient = Brush.linearGradient(listOf(Color(0xFFFF3B45), PurpleAccent))

/**
 * "Liquid glass" surface: a translucent fill (lighter at the top, like light hitting a curved pane),
 * an iridescent rim and an optional base so text stays readable over busy art.
 *
 * Follows the theme: on Light the dark base becomes a white one and the rim a dark hairline. With
 * Transparent Glass on, every pane keeps only a faint tint and a brighter rim, like AniStream's
 * clear glass. [overImage] keeps the dark base whatever the theme (badges drawn on cover art).
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    fillAlpha: Float = 0.07f,
    base: Color = Color.Transparent,
    borderWidth: Dp = 1.dp,
    overImage: Boolean = false
): Modifier {
    val palette = LocalPalette.current
    val clear = LocalClearGlass.current
    val light = palette.isLight && !overImage
    var b = base
    if (light && b.alpha > 0f && b.luminance() < 0.2f) b = Color.White.copy(alpha = (b.alpha + 0.3f).coerceAtMost(0.92f))
    if (clear && b.alpha > 0f) b = b.copy(alpha = (b.alpha * 0.4f).coerceAtMost(0.38f))
    val tint = if (light) 0.6f else 1.7f
    val rim = when {
        clear && !light -> ClearRim
        light -> LightRim
        else -> IridescentBorder
    }
    return this
        .clip(shape)
        .background(b, shape)
        .background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = fillAlpha * tint), Color.White.copy(alpha = fillAlpha * 0.4f))),
            shape
        )
        .border(borderWidth, rim, shape)
}

/** Near-black backdrop with a faint red glow at the top, like Anistream. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val glow = LocalPalette.current.glowAlpha
    Box(
        modifier
            .fillMaxSize()
            .background(OledBackground)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun blob(color: Color, x: Float, y: Float, r: Float) {
                    val c = Offset(x, y)
                    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = r), r, c)
                }
                blob(PurpleAccent.copy(alpha = glow), w * 0.5f, -h * 0.02f, w * 1.1f)
            }
    )
}

/** Glass card with a springy press animation, haptic long-press and optional click handlers. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    fillAlpha: Float = 0.07f,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 450f),
        label = "glassPress"
    )
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(cornerRadius)
    val clickable = onClick != null || onLongClick != null

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlass(shape, fillAlpha)
            .then(
                if (clickable) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onLongClick = onLongClick?.let { longClick ->
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                longClick()
                            }
                        },
                        onClick = { onClick?.invoke() }
                    )
                } else Modifier
            ),
        content = content
    )
}
