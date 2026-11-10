package com.mangapill.app.ui.components.glass

import androidx.compose.foundation.border
import com.mangapill.app.ui.theme.SelectBorder
import com.mangapill.app.ui.theme.SelectFill

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary

/** Springy scale on press, shared by glass buttons and chips. */
@Composable
fun rememberPressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.93f): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "pressScale"
    )
    return scale
}

/** Pill chip: frosted glass when idle, the brighter frosted lens when selected (as in AniStream). */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val textColor by animateColorAsState(
        when {
            !enabled -> TextSecondary.copy(alpha = 0.4f)
            selected -> TextPrimary
            else -> TextPrimary.copy(alpha = 0.85f)
        },
        label = "chipText"
    )
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (selected) Modifier.clip(shape).background(SelectFill, shape).border(1.dp, SelectBorder, shape)
                else Modifier.liquidGlass(shape, 0.05f, base = Color(0x4D000000))
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leading != null) Icon(leading, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
        Text(label, color = textColor, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    }
}

/** Primary call-to-action with the brand gradient. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, 0.96f)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .background(BrandGradient, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

/**
 * The main action on a page ("Start reading", "Read now"): white with dark content, like the
 * Play button in AniStream and CineStream. Text and icons inside take the dark colour by default.
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, 0.96f)
    val shape = RoundedCornerShape(16.dp)
    // White on the dark themes, ink on the light one, so it is always the strongest thing on the page.
    val light = com.mangapill.app.ui.theme.LocalPalette.current.isLight
    val fill = if (light) TextPrimary else Color.White
    val ink = if (light) Color.White else Color.Black
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides ink) {
        Row(
            modifier = modifier
                .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.6f }
                .clip(shape)
                .background(fill, shape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/** Round frosted icon button. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    active: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, 0.88f)
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (active) Modifier.clip(shape).background(BrandGradient, shape) else Modifier.liquidGlass(shape, 0.08f, base = Color(0x66000000)))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (active) Color.White else tint, modifier = Modifier.size(20.dp))
    }
}
