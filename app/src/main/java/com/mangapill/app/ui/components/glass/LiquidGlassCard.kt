package com.mangapill.app.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mangapill.app.ui.theme.GlassBorder
import com.mangapill.app.ui.theme.GlassHighlight
import com.mangapill.app.ui.theme.OledCard

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    glowColor: Color = Color(0x268B5CF6),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.6f),
        label = "glassCardScale"
    )

    val shape = RoundedCornerShape(cornerRadius)

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            OledCard.copy(alpha = 0.85f),
            OledCard.copy(alpha = 0.60f)
        )
    )

    val borderGradient = Brush.linearGradient(
        colors = listOf(
            GlassHighlight,
            GlassBorder,
            glowColor
        )
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(brush = backgroundGradient)
            .border(width = 1.dp, brush = borderGradient, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        content = content
    )
}
