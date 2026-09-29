package com.mangapill.app.ui.navigation

import android.net.Uri
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import com.mangapill.app.ui.components.glass.BrandGradient
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

enum class NavigationDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Default.Home),
    BOOKMARKS("bookmarks", "Library", Icons.Default.Bookmark),
    HISTORY("history", "History", Icons.Default.History),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
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

    val navigate: (NavigationDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

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

/** Floating pill navigation bar; the selected tab grows into a gradient capsule with a spring. */
@Composable
private fun GlassNavBar(currentRoute: String?, onSelect: (NavigationDestination) -> Unit) {
    Row(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .liquidGlass(RoundedCornerShape(32.dp), 0.08f, base = GlassDark)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationDestination.entries.forEach { d ->
            NavPill(d, selected = currentRoute == d.route || (currentRoute == null && d == NavigationDestination.HOME)) { onSelect(d) }
        }
    }
}

@Composable
private fun NavPill(destination: NavigationDestination, selected: Boolean, onClick: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "navPill"
    )
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(BrandGradient, shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            destination.icon,
            contentDescription = destination.label,
            tint = if (selected) Color.White else TextMuted,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = 1f + 0.1f * progress; scaleY = 1f + 0.1f * progress }
        )
        AnimatedVisibility(visible = selected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
            Row {
                Spacer(Modifier.width(6.dp))
                Text(destination.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
            .width(76.dp)
            .liquidGlass(RoundedCornerShape(28.dp), 0.07f, base = GlassDark)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NavigationDestination.entries.forEach { d ->
            val selected = currentRoute == d.route || (currentRoute == null && d == NavigationDestination.HOME)
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier
                    .clip(shape)
                    .then(if (selected) Modifier.background(BrandGradient, shape) else Modifier)
                    .clickable { onSelect(d) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(d.icon, contentDescription = d.label, tint = if (selected) Color.White else TextMuted)
                Text(d.label, color = if (selected) Color.White else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
