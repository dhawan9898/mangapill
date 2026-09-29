package com.mangapill.app.ui.components.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import com.mangapill.app.ui.theme.CyanAccent
import com.mangapill.app.ui.theme.OledBackground
import com.mangapill.app.ui.theme.PinkAccent
import com.mangapill.app.ui.theme.PurpleAccent

/** Iridescent rim used on every glass surface. */
val IridescentBorder = Brush.linearGradient(
    listOf(
        Color(0x59FFFFFF),
        PurpleAccent.copy(alpha = 0.35f),
        CyanAccent.copy(alpha = 0.18f),
        PinkAccent.copy(alpha = 0.25f),
        Color(0x33FFFFFF)
    )
)

val BrandGradient = Brush.linearGradient(listOf(PurpleAccent, Color(0xFF6366F1), CyanAccent))

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

/** Slowly drifting aurora behind the whole app; the glass panels pick up its colour. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraPhase"
    )
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
                blob(PurpleAccent.copy(alpha = 0.24f), w * (0.1f + 0.35f * p), h * 0.08f, w * 0.95f)
                blob(CyanAccent.copy(alpha = 0.10f), w * (1f - 0.35f * p), h * (0.42f + 0.12f * p), w * 0.8f)
                blob(PinkAccent.copy(alpha = 0.09f), w * (0.25f + 0.3f * p), h * (0.98f - 0.12f * p), w * 0.9f)
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
