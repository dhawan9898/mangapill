package com.mangapill.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.Manga
import com.mangapill.app.ui.navigation.AdaptiveAppScaffold
import com.mangapill.app.ui.navigation.NavigationDestination
import com.mangapill.app.ui.navigation.Routes
import com.mangapill.app.ui.screens.details.MangaDetailsScreen
import com.mangapill.app.ui.screens.search.SearchScreen
import com.mangapill.app.data.repository.HistoryEntry
import com.mangapill.app.ui.navigation.navigateToTab
import com.mangapill.app.ui.screens.home.HomeScreen
import com.mangapill.app.ui.screens.home.HomeState
import com.mangapill.app.ui.screens.library.LibraryScreen
import com.mangapill.app.ui.screens.reader.DualModeReaderScreen
import com.mangapill.app.ui.screens.settings.SettingsScreen
import com.mangapill.app.ui.theme.MangapillTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as MangapillApp).repository

        setContent {
            val theme by repository.prefs.theme.collectAsState()
            val clearGlass by repository.prefs.clearGlass.collectAsState()
            MangapillTheme(theme = theme, clearGlass = clearGlass) {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                // Lives above the NavHost so query, filters and scroll position survive navigation.
                val homeState = remember { HomeState(repository, scope) }

                val openManga: (Manga) -> Unit = { m -> navController.navigateOnce(Routes.manga(m.sourceId, m.id)) }
                val readChapter: (Manga, Chapter) -> Unit = { m, ch ->
                    navController.navigateOnce(Routes.reader(m.sourceId, m.id, ch.id, ch.chapterNumber))
                }
                val continueReading: (HistoryEntry) -> Unit = { e ->
                    navController.navigateOnce(Routes.reader(e.manga.sourceId, e.manga.id, e.chapterId, e.chapterNumber))
                }

                AdaptiveAppScaffold(navController = navController) {
                    NavHost(
                        navController = navController,
                        startDestination = NavigationDestination.HOME.route,
                        // Tabs cross-fade; pushed screens spring in and scale away.
                        enterTransition = { fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.96f) },
                        exitTransition = { fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 0.98f) },
                        popEnterTransition = { fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.98f) },
                        popExitTransition = { fadeOut(tween(160)) + scaleOut(spring(stiffness = Spring.StiffnessMediumLow), targetScale = 0.94f) }
                    ) {
                        composable(NavigationDestination.HOME.route) {
                            HomeScreen(
                                state = homeState,
                                repository = repository,
                                onMangaSelect = openManga,
                                onReadChapter = readChapter,
                                onContinue = continueReading,
                                onOpenSearch = { navController.navigateToTab(NavigationDestination.SEARCH) }
                            )
                        }

                        composable(NavigationDestination.SEARCH.route) {
                            SearchScreen(
                                state = homeState,
                                repository = repository,
                                onMangaSelect = openManga,
                                onReadChapter = readChapter
                            )
                        }

                        composable(
                            route = Routes.MANGA,
                            arguments = listOf(
                                navArgument("src") { type = NavType.StringType; defaultValue = "mangapill" },
                                navArgument("id") { type = NavType.StringType; defaultValue = "" }
                            ),
                            enterTransition = { slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(tween(220)) },
                            popExitTransition = { slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(200)) }
                        ) { entry ->
                            val src = entry.arguments?.getString("src").orEmpty()
                            val id = entry.arguments?.getString("id").orEmpty()
                            MangaDetailsScreen(
                                sourceId = src,
                                mangaId = id,
                                repository = repository,
                                onBack = { navController.popBackStack() },
                                onChapterSelect = { ch ->
                                    navController.navigateOnce(Routes.reader(src, id, ch.id, ch.chapterNumber))
                                }
                            )
                        }

                        composable(
                            route = Routes.READER,
                            arguments = listOf(
                                navArgument("src") { type = NavType.StringType; defaultValue = "mangapill" },
                                navArgument("manga") { type = NavType.StringType; defaultValue = "" },
                                navArgument("chapter") { type = NavType.StringType; defaultValue = "" },
                                navArgument("num") { type = NavType.FloatType; defaultValue = 0f }
                            )
                        ) { entry ->
                            val src = entry.arguments?.getString("src").orEmpty()
                            val manga = entry.arguments?.getString("manga").orEmpty()
                            val chapter = entry.arguments?.getString("chapter").orEmpty()
                            val num = entry.arguments?.getFloat("num") ?: 0f
                            DualModeReaderScreen(
                                sourceId = src,
                                mangaId = manga,
                                chapterId = chapter,
                                chapterNumber = num,
                                repository = repository,
                                onBack = { navController.popBackStack() },
                                onOpenChapter = { next: Chapter -> openChapterReplacing(navController, src, manga, next) }
                            )
                        }

                        composable(NavigationDestination.LIBRARY.route) {
                            LibraryScreen(repository = repository, onMangaSelect = openManga, onContinue = continueReading)
                        }

                        composable(NavigationDestination.SETTINGS.route) {
                            SettingsScreen(repository = repository)
                        }
                    }
                }
            }
        }
    }

    /** Prev/next inside the reader replaces the current reader entry so Back returns to the manga page. */
    private fun openChapterReplacing(nav: NavHostController, src: String, manga: String, chapter: Chapter) {
        val currentId = nav.currentDestination?.id
        nav.navigateOnce(Routes.reader(src, manga, chapter.id, chapter.chapterNumber)) {
            if (currentId != null) popUpTo(currentId) { inclusive = true }
        }
    }
}

/**
 * Navigates only while the current screen is fully on screen. A second tap that lands during the
 * transition (a quick double tap on a chapter, a cover or "Next") would otherwise open the
 * destination twice.
 */
private fun NavHostController.navigateOnce(route: String, builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {}) {
    val state = currentBackStackEntry?.lifecycle?.currentState
    if (state == null || state == androidx.lifecycle.Lifecycle.State.RESUMED) navigate(route, builder)
}
