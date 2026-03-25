package com.deedeedev.ytreader.ui

import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.deedeedev.ytreader.AppContainer
import com.deedeedev.ytreader.ui.home.HomeViewModel
import com.deedeedev.ytreader.ui.home.CollectionDetailScreen
import com.deedeedev.ytreader.ui.home.CollectionsScreen
import com.deedeedev.ytreader.ui.home.LibraryScreen
import com.deedeedev.ytreader.ui.home.SearchScreen
import com.deedeedev.ytreader.ui.reader.ReaderScreen
import com.deedeedev.ytreader.ui.reader.VideoNotesSheetRoute
import com.deedeedev.ytreader.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Library : Screen("library", "Library", Icons.Default.Home)
    object Collections : Screen("collections", "Collections", Icons.Default.CollectionsBookmark)
    object CollectionDetail : Screen("collection/{collectionId}", "Collection", Icons.Default.CollectionsBookmark)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Reader : Screen(
        "reader/{subtitleId}?highlightStart={highlightStart}&highlightEnd={highlightEnd}",
        "Reader",
        Icons.AutoMirrored.Filled.MenuBook
    ) {
        fun createRoute(
            subtitleId: Long,
            highlightStart: Int? = null,
            highlightEnd: Int? = null
        ): String {
            return buildString {
                append("reader/")
                append(subtitleId)
                if (highlightStart != null && highlightEnd != null) {
                    append("?highlightStart=")
                    append(highlightStart)
                    append("&highlightEnd=")
                    append(highlightEnd)
                }
            }
        }
    }
    object VideoNotes : Screen("video_notes/{videoId}", "Highlights & Notes", Icons.AutoMirrored.Filled.MenuBook)
}

@Composable
fun MainScreen(
    appContainer: AppContainer,
    requestedHomeRoute: String? = null,
    onHomeRouteHandled: () -> Unit = {},
    requestedReaderSubtitleId: Long? = null,
    onReaderSubtitleHandled: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.provideFactory(
            appContainer.youtubeRepository,
            appContainer.subtitleDao,
            appContainer.highlightNoteDao,
            appContainer.userPreferencesRepository
        )
    )
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(requestedHomeRoute) {
        val route = requestedHomeRoute ?: return@LaunchedEffect
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        onHomeRouteHandled()
    }

    LaunchedEffect(requestedReaderSubtitleId) {
        val subtitleId = requestedReaderSubtitleId ?: return@LaunchedEffect
        navController.navigate(Screen.Reader.createRoute(subtitleId)) {
            launchSingleTop = true
        }
        onReaderSubtitleHandled()
    }

    Scaffold(
        bottomBar = {
            val currentDestination = navBackStackEntry?.destination

            // Hide bottom bar on Reader screen
            if (currentRoute?.startsWith("reader") != true) {
                NavigationBar {
                    val items = listOf(Screen.Library, Screen.Search, Screen.Collections, Screen.Settings)
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            alwaysShowLabel = false,
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = Screen.Search.route,
                enterTransition = { null },
                exitTransition = { null },
                popEnterTransition = { null },
                popExitTransition = { null }
            ) {
                SearchScreen(
                    viewModel = viewModel,
                    onSubtitleClick = { id ->
                        navController.navigate(Screen.Reader.createRoute(id))
                    }
                )
            }
            composable(
                route = Screen.Library.route,
                enterTransition = { null },
                exitTransition = { null },
                popEnterTransition = { null },
                popExitTransition = { null }
            ) {
                LibraryScreen(
                    viewModel = viewModel,
                    onSubtitleClick = { id ->
                        navController.navigate(Screen.Reader.createRoute(id))
                    },
                    onVideoClick = { url ->
                        viewModel.onUrlChange(url)
                        viewModel.searchVideo()
                        navController.navigate(Screen.Search.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                route = Screen.Settings.route,
                enterTransition = { null },
                exitTransition = { null },
                popEnterTransition = { null },
                popExitTransition = { null }
            ) {
                SettingsScreen(appContainer = appContainer)
            }
            composable(
                route = Screen.Collections.route,
                enterTransition = { null },
                exitTransition = { null },
                popEnterTransition = { null },
                popExitTransition = { null }
            ) {
                CollectionsScreen(
                    viewModel = viewModel,
                    onCollectionClick = { collectionId ->
                        navController.navigate("collection/$collectionId") {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = Screen.CollectionDetail.route,
                arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
                enterTransition = { null },
                exitTransition = { null },
                popEnterTransition = { null },
                popExitTransition = { null }
            ) { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getString("collectionId") ?: return@composable

                CollectionDetailScreen(
                    viewModel = viewModel,
                    collectionId = collectionId,
                    onSubtitleClick = { id ->
                        navController.navigate(Screen.Reader.createRoute(id))
                    },
                    onBack = { navController.popBackStack() },
                    onVideoClick = { url ->
                        viewModel.onUrlChange(url)
                        viewModel.searchVideo()
                        navController.navigate(Screen.Search.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                route = Screen.Reader.route,
                arguments = listOf(
                    navArgument("subtitleId") { type = NavType.LongType },
                    navArgument("highlightStart") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("highlightEnd") {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                ),
                enterTransition = { null },
                exitTransition = { null },
                popEnterTransition = { null },
                popExitTransition = { null }
            ) { backStackEntry ->
                val subtitleId = backStackEntry.arguments?.getLong("subtitleId") ?: return@composable
                val highlightStart = backStackEntry.arguments?.getInt("highlightStart") ?: -1
                val highlightEnd = backStackEntry.arguments?.getInt("highlightEnd") ?: -1
                
                ReaderScreen(
                    subtitleId = subtitleId,
                    subtitleDao = appContainer.subtitleDao,
                    highlightNoteDao = appContainer.highlightNoteDao,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    initialHighlightRange = if (highlightStart >= 0 && highlightEnd > highlightStart) {
                        highlightStart to highlightEnd
                    } else {
                        null
                    },
                    onOpenVideoNotes = { videoId ->
                        navController.navigate("video_notes/$videoId") {
                            launchSingleTop = true
                        }
                    },
                    onChromeReady = {},
                    onBack = { navController.popBackStack() }
                )
            }
            dialog(
                route = Screen.VideoNotes.route,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                dialogProperties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getString("videoId") ?: return@dialog

                VideoNotesSheetRoute(
                    videoId = videoId,
                    subtitleDao = appContainer.subtitleDao,
                    highlightNoteDao = appContainer.highlightNoteDao,
                    onOpenSubtitle = { targetSubtitleId, targetStart, targetEnd ->
                        navController.navigate(
                            Screen.Reader.createRoute(
                                subtitleId = targetSubtitleId,
                                highlightStart = targetStart,
                                highlightEnd = targetEnd
                            )
                        ) {
                            navController.previousBackStackEntry?.destination?.id?.let { previousDestinationId ->
                                popUpTo(previousDestinationId) {
                                    inclusive = true
                                }
                            }
                        }
                    },
                    onDismiss = { navController.popBackStack() }
                )
            }
        }
    }
}
