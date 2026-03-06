package com.deedeedev.ytreader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.deedeedev.ytreader.AppContainer
import com.deedeedev.ytreader.ui.home.HomeViewModel
import com.deedeedev.ytreader.ui.home.LibraryScreen
import com.deedeedev.ytreader.ui.home.SearchScreen
import com.deedeedev.ytreader.ui.reader.ReaderScreen
import com.deedeedev.ytreader.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Library : Screen("library", "Library", Icons.Default.Home)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Reader : Screen("reader/{subtitleId}", "Reader", Icons.AutoMirrored.Filled.MenuBook)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    appContainer: AppContainer,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.provideFactory(
            appContainer.youtubeRepository,
            appContainer.subtitleDao,
            appContainer.userPreferencesRepository
        )
    )
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isReaderRoute = currentRoute?.startsWith("reader") == true
    var isReaderChromeReady by remember { mutableStateOf(false) }

    LaunchedEffect(navBackStackEntry) {
        if (isReaderRoute) {
            isReaderChromeReady = false
        }
    }

    Scaffold(
        topBar = {
            if (!isReaderRoute || !isReaderChromeReady) {
                CenterAlignedTopAppBar(title = { Text("YtReader") })
            }
        },
        bottomBar = {
            val currentDestination = navBackStackEntry?.destination

            // Hide bottom bar on Reader screen
            if (currentRoute?.startsWith("reader") != true) {
                NavigationBar {
                    val items = listOf(Screen.Search, Screen.Library, Screen.Settings)
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.label) },
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
            startDestination = Screen.Search.route,
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
                        isReaderChromeReady = false
                        navController.navigate("reader/$id")
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
                        isReaderChromeReady = false
                        navController.navigate("reader/$id")
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
                route = Screen.Reader.route,
                arguments = listOf(navArgument("subtitleId") { type = NavType.LongType }),
                enterTransition = { null },
                exitTransition = { null },
                popEnterTransition = { null },
                popExitTransition = { null }
            ) { backStackEntry ->
                val subtitleId = backStackEntry.arguments?.getLong("subtitleId") ?: return@composable
                
                ReaderScreen(
                    subtitleId = subtitleId,
                    subtitleDao = appContainer.subtitleDao,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    aiCleaningRepository = appContainer.aiCleaningRepository,
                    onChromeReady = { isReaderChromeReady = true },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
