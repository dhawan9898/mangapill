package com.mangapill.app.ui.components.glass

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

/** Iridescent rim used on every glass surface. */
val IridescentBorder = Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x0FFFFFFF), Color(0x1FFFFFFF)))

val BrandGradient = Brush.linearGradient(listOf(Color(0xFFFF3B45), PurpleAccent))

/**
 * "Liquid glass" surface: a translucent frosted fill (lighter at the top, like light hitting a
 * curved pane), an iridescent rim and an optional dark base so text stays readable over busy art.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    fillAlpha: Float = 0.07f,
    base: Color = Color.Transparent,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .background(base, shape)
    .background(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = fillAlpha * 1.7f), Color.White.copy(alpha = fillAlpha * 0.4f))),
        shape
    )
    .border(borderWidth, IridescentBorder, shape)

/** Near-black backdrop with a faint red glow at the top, like Anistream. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
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
                blob(PurpleAccent.copy(alpha = 0.10f), w * 0.5f, -h * 0.02f, w * 1.1f)
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
