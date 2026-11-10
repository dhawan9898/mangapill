package com.mangapill.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.mangapill.app.ui.theme.SelectBorder
import com.mangapill.app.ui.theme.SelectFill
import com.mangapill.app.ui.theme.TextPrimary
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mangapill.app.ui.components.glass.LiquidBackground
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.theme.GlassDark
import com.mangapill.app.ui.theme.TextMuted

object Routes {
    const val MANGA = "manga?src={src}&id={id}"
    const val READER = "reader?src={src}&manga={manga}&chapter={chapter}&num={num}"

    fun manga(src: String, id: String) = "manga?src=${Uri.encode(src)}&id=${Uri.encode(id)}"

    fun reader(src: String, manga: String, chapter: String, num: Float) =
        "reader?src=${Uri.encode(src)}&manga=${Uri.encode(manga)}&chapter=${Uri.encode(chapter)}&num=$num"
}

/** The four tabs, in the same order as AniStream and CineStream: Home, Search, Library, Settings. */
enum class NavigationDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Outlined.Home),
    SEARCH("search", "Search", Icons.Outlined.Search),
    LIBRARY("library", "Library", Icons.Outlined.CollectionsBookmark),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings)
}

/** Switches tab keeping each tab's own back stack and state, as the bottom bar does. */
fun NavHostController.navigateToTab(destination: NavigationDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Height reserved at the bottom of scrollable content for the floating navigation bar. */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

/** Bottom padding for scrollable content so the last row clears the floating bar and the system bar. */
@Composable
fun bottomContentPadding(): Dp =
    LocalBottomBarInset.current + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp

@Composable
fun AdaptiveAppScaffold(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // The bar belongs to the top-level tabs only; details and the reader are immersive.
    val showBar = currentRoute == null || NavigationDestination.entries.any { it.route == currentRoute }

    val navigate: (NavigationDestination) -> Unit = { destination -> navController.navigateToTab(destination) }

    Box(Modifier.fillMaxSize()) {
        LiquidBackground()

        if (isTablet) {
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = showBar, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    GlassRail(currentRoute, navigate)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            CompositionLocalProvider(LocalBottomBarInset provides if (showBar) 84.dp else 0.dp) {
                content()
            }
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GlassNavBar(currentRoute, navigate)
            }
        }
    }
}

/**
 * Floating liquid-glass dock (streamkit's Dock): every tab shows its icon and label, and a frosted
 * lens - not a brand-coloured fill - slides under the selected one with a slight overshoot.
 */
@Composable
private fun GlassNavBar(currentRoute: String?, onSelect: (NavigationDestination) -> Unit) {
    val tabs = NavigationDestination.entries
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .liquidGlass(RoundedCornerShape(32.dp), 0.06f, base = GlassDark)
            .padding(6.dp)
    ) {
        val slot = maxWidth / tabs.size
        val lensX by animateDpAsState(
            targetValue = slot * selectedIndex,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "dockLens"
        )
        Box(
            Modifier
                .offset(x = lensX)
                .width(slot)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(SelectFill)
                .border(1.dp, SelectBorder, CircleShape)
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, d ->
                val selected = i == selectedIndex
                val tint by animateColorAsState(if (selected) TextPrimary else TextMuted, label = "dockTint")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(d)
                        }
                        .semantics { this.selected = selected },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(d.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        d.label,
                        color = tint,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassRail(currentRoute: String?, onSelect: (NavigationDestination) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp)
            .width(80.dp)
            .liquidGlass(RoundedCornerShape(28.dp), 0.06f, base = GlassDark)
            .padding(vertical = 16.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NavigationDestination.entries.forEach { d ->
            val selected = currentRoute == d.route || (currentRoute == null && d == NavigationDestination.HOME)
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .then(if (selected) Modifier.background(SelectFill, shape).border(1.dp, SelectBorder, shape) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(d) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(d.icon, contentDescription = null, tint = if (selected) TextPrimary else TextMuted)
                Text(d.label, color = if (selected) TextPrimary else TextMuted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}
