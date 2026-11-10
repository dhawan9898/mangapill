package com.mangapill.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.components.glass.rememberPressScale
import com.mangapill.app.ui.theme.AccentSoft
import com.mangapill.app.ui.theme.BorderSubtle
import com.mangapill.app.ui.theme.GlassDark
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.SelectBorder
import com.mangapill.app.ui.theme.SelectFill
import com.mangapill.app.ui.theme.Surface
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import com.mangapill.app.ui.theme.ToggleOn
import com.mangapill.app.ui.theme.TrackOff

// The building blocks AniStream and CineStream share (page header, section labels, settings rows,
// segmented control, pill buttons, confirm dialog), redone in Compose so the three apps match.

/** "MANGA" in brand red, "PILL" in white - the same two-tone wordmark as ANISTREAM / CINESTREAM. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = PurpleAccent)) { append("MANGA") }
            withStyle(SpanStyle(color = TextPrimary)) { append("PILL") }
        },
        fontSize = 26.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.5).sp,
        modifier = modifier.semantics { contentDescription = "Mangapill" }
    )
}

/** Top-level page title with a hairline under it ("My Library", "Settings"). */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            trailing()
        }
        HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
    }
}

/** Small uppercase group label above a settings card ("APPEARANCE", "DATA"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = TextMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

/** Glass card holding settings rows, with hairlines between them. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(22.dp), 0.05f, base = Color(0x66000000)),
        content = content
    )
}

@Composable
fun CardDivider() {
    HorizontalDivider(color = BorderSubtle, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

/** Red-tinted square behind a setting's icon. */
@Composable
fun IconTile(icon: ImageVector, tint: Color = PurpleAccent) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(AccentSoft),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** iOS-style switch (green when on), like the toggles in the other two apps. */
@Composable
fun IosSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val knob by animateDpAsState(
        if (checked) 21.dp else 2.dp,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "switchKnob"
    )
    val track by animateColorAsState(if (checked) ToggleOn else TrackOff, label = "switchTrack")
    Box(
        modifier
            .size(width = 49.dp, height = 30.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, if (checked) Color.Transparent else TextMuted.copy(alpha = 0.3f), CircleShape)
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 2.dp)
                .size(26.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/** Icon tile, label + description, and a switch; the whole row toggles. */
@Composable
fun ToggleRow(icon: ImageVector, label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconTile(icon)
        RowText(label, description, Modifier.weight(1f))
        IosSwitch(checked)
    }
}

/** Icon tile, label + description and an optional trailing value; tapping runs [onClick]. */
@Composable
fun ActionRow(
    icon: ImageVector,
    label: String,
    description: String?,
    onClick: (() -> Unit)?,
    danger: Boolean = false,
    trailing: String? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconTile(icon, tint = if (danger) Color(0xFFFB7185) else PurpleAccent)
        RowText(label, description, Modifier.weight(1f), labelColor = if (danger) Color(0xFFFB7185) else TextPrimary)
        if (trailing != null) Text(trailing, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RowText(label: String, description: String?, modifier: Modifier, labelColor: Color = TextPrimary) {
    Column(modifier) {
        Text(label, color = labelColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
        if (!description.isNullOrBlank()) {
            Text(description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * Glass track with a frosted lens that slides to the selected option ("History | My List",
 * "Webtoon | Paged | Right to left"). Labels never wrap; a count shows as a small badge.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
    counts: List<Int?> = emptyList()
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .liquidGlass(RoundedCornerShape(16.dp), 0.04f, base = Color(0x66000000))
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val lensX by animateDpAsState(
            segment * selectedIndex,
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "segmentLens"
        )
        Box(
            Modifier
                .offset(x = lensX)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(SelectFill)
                .border(1.dp, SelectBorder, RoundedCornerShape(12.dp))
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                val color by animateColorAsState(if (selected) TextPrimary else TextSecondary, label = "segmentText")
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab
                        ) {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    icons.getOrNull(i)?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(16.dp)) }
                    if (icons.getOrNull(i) != null) Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        color = color,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    counts.getOrNull(i)?.takeIf { it > 0 }?.let { n ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (n > 99) "99+" else "$n",
                            color = color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(TrackOff)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Secondary rounded button on the plain surface ("Try again", "Cancel"). */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    background: Color = Surface,
    contentColor: Color = TextPrimary
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, 0.95f)
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .border(1.dp, BorderSubtle, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Glass confirmation dialog for destructive actions (the ConfirmModal of the other apps). */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(22.dp), 0.06f, base = GlassDark)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(message, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Cancel", onDismiss, Modifier.weight(1f))
                PillButton(
                    confirmLabel,
                    onConfirm,
                    Modifier.weight(1f),
                    background = if (danger) PurpleAccent else Surface,
                    contentColor = if (danger) Color.White else TextPrimary
                )
            }
        }
    }
}
