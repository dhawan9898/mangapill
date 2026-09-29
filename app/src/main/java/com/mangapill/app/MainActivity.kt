package com.mangapill.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mangapill.app.data.local.AppDatabase
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.ui.navigation.AdaptiveAppScaffold
import com.mangapill.app.ui.navigation.NavigationDestination
import com.mangapill.app.ui.screens.details.MangaDetailsScreen
import com.mangapill.app.ui.screens.home.HomeScreen
import com.mangapill.app.ui.screens.reader.DualModeReaderScreen
import com.mangapill.app.ui.theme.MangapillTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(this)
        val repository = MangaRepository(db)

        setContent {
            MangapillTheme {
                val navController = rememberNavController()

                AdaptiveAppScaffold(navController = navController) {
                    NavHost(
                        navController = navController,
                        startDestination = NavigationDestination.HOME.route
                    ) {
                        composable(NavigationDestination.HOME.route) {
                            HomeScreen(
                                repository = repository,
                                onMangaSelect = { mangaId ->
                                    navController.navigate("manga/${Uri.encode(mangaId)}")
                                }
                            )
                        }

                        composable(
                            route = "manga/{mangaId}",
                            arguments = listOf(navArgument("mangaId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val mangaId = backStackEntry.arguments?.getString("mangaId") ?: ""
                            MangaDetailsScreen(
                                mangaId = mangaId,
                                repository = repository,
                                onBack = { navController.popBackStack() },
                                onChapterSelect = { chapterId ->
                                    navController.navigate("reader/${Uri.encode(mangaId)}/${Uri.encode(chapterId)}")
                                }
                            )
                        }

                        composable(
                            route = "reader/{mangaId}/{chapterId}",
                            arguments = listOf(
                                navArgument("mangaId") { type = NavType.StringType },
                                navArgument("chapterId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val mangaId = backStackEntry.arguments?.getString("mangaId") ?: ""
                            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
                            DualModeReaderScreen(
                                mangaId = mangaId,
                                chapterId = chapterId,
                                repository = repository,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavigationDestination.BOOKMARKS.route) {
                            Text("Library & Bookmarks")
                        }

                        composable(NavigationDestination.HISTORY.route) {
                            Text("Reading History")
                        }

                        composable(NavigationDestination.SETTINGS.route) {
                            Text("Settings")
                        }
                    }
                }
            }
        }
    }
}
