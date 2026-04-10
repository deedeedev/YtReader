package com.deedeedev.ytreader.ui

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.ui.annotations.AnnotationsScreen
import com.deedeedev.ytreader.ui.annotations.AnnotationsViewModel
import com.deedeedev.ytreader.ui.home.SearchViewModel
import com.deedeedev.ytreader.ui.home.LibraryViewModel
import com.deedeedev.ytreader.ui.home.CollectionsViewModel
import com.deedeedev.ytreader.ui.home.CollectionDetailScreen
import com.deedeedev.ytreader.ui.home.CollectionsScreen
import com.deedeedev.ytreader.ui.home.LibraryScreen
import com.deedeedev.ytreader.ui.home.SearchScreen
import com.deedeedev.ytreader.ui.reader.JumpBackState
import com.deedeedev.ytreader.ui.reader.ReaderLocation
import com.deedeedev.ytreader.ui.reader.ReaderScreen
import com.deedeedev.ytreader.ui.reader.VideoNotesSheetRoute
import com.deedeedev.ytreader.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

sealed class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Search : Screen("search", R.string.screen_search, Icons.Default.Search)
    object Library : Screen("library", R.string.library, Icons.Default.Home)
    object Annotations : Screen("annotations", R.string.screen_annotations, Icons.Default.EditNote)
    object Collections : Screen("collections", R.string.collections, Icons.Default.CollectionsBookmark)
    object CollectionDetail : Screen("collection/{collectionId}", R.string.collection, Icons.Default.CollectionsBookmark)
    object Settings : Screen("settings", R.string.screen_settings, Icons.Default.Settings)
    object Reader : Screen(
        "reader/{subtitleId}?highlightStart={highlightStart}&highlightEnd={highlightEnd}&bookmarkStart={bookmarkStart}",
        R.string.screen_reader,
        Icons.AutoMirrored.Filled.MenuBook
    ) {
        fun createRoute(
            subtitleId: Long,
            highlightStart: Int? = null,
            highlightEnd: Int? = null,
            bookmarkStart: Int? = null
        ): String {
            return buildString {
                append("reader/")
                append(subtitleId)
                if (highlightStart != null && highlightEnd != null) {
                    append("?highlightStart=")
                    append(highlightStart)
                    append("&highlightEnd=")
                    append(highlightEnd)
                } else if (bookmarkStart != null) {
                    append("?bookmarkStart=")
                    append(bookmarkStart)
                }
            }
        }
    }
    object VideoNotes : Screen("video_notes/{videoId}", R.string.highlights_and_notes, Icons.AutoMirrored.Filled.MenuBook)
}

private const val TAG = "MainScreen"

@Composable
fun MainScreen(
    appContainer: AppContainer,
    requestedHomeRoute: String? = null,
    onHomeRouteHandled: () -> Unit = {},
    requestedReaderSubtitleId: Long? = null,
    onReaderSubtitleHandled: () -> Unit = {},
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
    collectionsViewModel: CollectionsViewModel
) {
    val annotationsViewModel: AnnotationsViewModel = viewModel(
        factory = AnnotationsViewModel.provideFactory(
            appContainer.subtitleDao,
            appContainer.highlightNoteDao,
            appContainer.bookmarkDao
        )
    )
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var pendingReaderInitialLocation by remember { mutableStateOf<ReaderLocation?>(null) }
    var pendingReaderJumpBackState by remember { mutableStateOf<JumpBackState?>(null) }
    var navigatingFromVideoNotes by remember { mutableStateOf(false) }
    var libraryScrollPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var collectionScrollPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val openPreferredSubtitleForVideo: (String, Pair<Int, Int>) -> Unit = { videoId, scrollPosition ->
        coroutineScope.launch {
            val subtitleId = libraryViewModel.getPreferredSubtitleIdForVideo(videoId) ?: return@launch
            pendingReaderInitialLocation = null
            pendingReaderJumpBackState = null
            libraryScrollPosition = scrollPosition
            navController.navigate(Screen.Reader.createRoute(subtitleId)) {
                launchSingleTop = true
            }
        }
    }

    val openPreferredSubtitleForVideoFromCollection: (String, Pair<Int, Int>) -> Unit = { videoId, scrollPosition ->
        coroutineScope.launch {
            val subtitleId = libraryViewModel.getPreferredSubtitleIdForVideo(videoId) ?: return@launch
            pendingReaderInitialLocation = null
            pendingReaderJumpBackState = null
            collectionScrollPosition = scrollPosition
            navController.navigate(Screen.Reader.createRoute(subtitleId)) {
                launchSingleTop = true
            }
        }
    }

    val searchVideoAgain: (String) -> Unit = { url ->
        searchViewModel.onUrlChange(url)
        searchViewModel.searchVideo()
        navController.navigate(Screen.Search.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openReader: (Long, Pair<Int, Int>) -> Unit = { subtitleId, scrollPosition ->
        pendingReaderInitialLocation = null
        pendingReaderJumpBackState = null
        libraryScrollPosition = scrollPosition
        navController.navigate(Screen.Reader.createRoute(subtitleId)) {
            launchSingleTop = true
        }
    }

    val openReaderFromCollection: (Long, Pair<Int, Int>) -> Unit = { subtitleId, scrollPosition ->
        pendingReaderInitialLocation = null
        pendingReaderJumpBackState = null
        collectionScrollPosition = scrollPosition
        navController.navigate(Screen.Reader.createRoute(subtitleId)) {
            launchSingleTop = true
        }
    }

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
        openReader(subtitleId, 0 to 0)
        onReaderSubtitleHandled()
    }

    Scaffold(
        bottomBar = {
            val currentDestination = navBackStackEntry?.destination

            // Hide bottom bar on Reader screen
            if (currentRoute?.startsWith("reader") != true) {
                NavigationBar {
                    val items = listOf(Screen.Library, Screen.Search, Screen.Annotations, Screen.Collections, Screen.Settings)
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
                    viewModel = searchViewModel,
                    onSubtitleClick = { id ->
                        openReader(id, 0 to 0)
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
                    viewModel = libraryViewModel,
                    onSubtitleClick = { id, scrollPosition ->
                        openReader(id, scrollPosition)
                    },
                    onVideoClick = openPreferredSubtitleForVideo,
                    onVideoSearchAgain = searchVideoAgain,
                    subtitleDao = appContainer.subtitleDao,
                    videoDao = appContainer.videoDao,
                    highlightNoteDao = appContainer.highlightNoteDao,
                    bookmarkDao = appContainer.bookmarkDao,
                    initialScrollPosition = libraryScrollPosition
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
                    viewModel = collectionsViewModel,
                    onCollectionClick = { collectionId ->
                        navController.navigate("collection/$collectionId") {
                            launchSingleTop = true
                        }
                    },
                    subtitleDao = appContainer.subtitleDao,
                    videoDao = appContainer.videoDao,
                    highlightNoteDao = appContainer.highlightNoteDao,
                    bookmarkDao = appContainer.bookmarkDao
                )
            }
            composable(
                route = Screen.Annotations.route,
                enterTransition = { null },
                exitTransition = { null },
                popEnterTransition = { null },
                popExitTransition = { null }
            ) {
                AnnotationsScreen(
                    viewModel = annotationsViewModel,
                    onAnnotationClick = { target ->
                        navController.navigate(
                            Screen.Reader.createRoute(
                                subtitleId = target.subtitleId,
                                highlightStart = target.highlightStart,
                                highlightEnd = target.highlightEnd,
                                bookmarkStart = target.bookmarkStart
                            )
                        )
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
                    viewModel = collectionsViewModel,
                    collectionId = collectionId,
                    onSubtitleClick = { id, scrollPosition ->
                        openReaderFromCollection(id, scrollPosition)
                    },
                    onBack = { navController.popBackStack() },
                    onVideoClick = openPreferredSubtitleForVideoFromCollection,
                    onVideoSearchAgain = searchVideoAgain,
                    subtitleDao = appContainer.subtitleDao,
                    videoDao = appContainer.videoDao,
                    highlightNoteDao = appContainer.highlightNoteDao,
                    bookmarkDao = appContainer.bookmarkDao,
                    initialScrollPosition = collectionScrollPosition
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
                    },
                    navArgument("bookmarkStart") {
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
                val bookmarkStart = backStackEntry.arguments?.getInt("bookmarkStart") ?: -1
                
                ReaderScreen(
                    subtitleId = subtitleId,
                    subtitleDao = appContainer.subtitleDao,
                    highlightNoteDao = appContainer.highlightNoteDao,
                    bookmarkDao = appContainer.bookmarkDao,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    initialReaderLocation = pendingReaderInitialLocation?.takeIf { it.subtitleId == subtitleId },
                    initialJumpBackState = pendingReaderJumpBackState.also {
                        Log.d(TAG, "ReaderScreen initialJumpBackState=$it pendingReaderJumpBackState=$pendingReaderJumpBackState")
                    },
                    initialHighlightRange = if (highlightStart >= 0 && highlightEnd > highlightStart) {
                        highlightStart to highlightEnd
                    } else {
                        null
                    },
                    initialBookmarkStart = bookmarkStart.takeIf { it >= 0 },
                    onOpenVideoNotes = { videoId, jumpBackState ->
                        Log.d(TAG, "onOpenVideoNotes: setting pendingReaderJumpBackState=$jumpBackState")
                        pendingReaderJumpBackState = jumpBackState
                        navController.navigate("video_notes/$videoId") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToReaderLocation = { location ->
                        pendingReaderInitialLocation = location
                        pendingReaderJumpBackState = null
                        navController.navigate(Screen.Reader.createRoute(location.subtitleId)) {
                            navController.currentBackStackEntry?.destination?.id?.let { currentDestinationId ->
                                popUpTo(currentDestinationId) {
                                    inclusive = true
                                }
                            }
                        }
                    },
                    onInitialNavigationConsumed = {
                        Log.d(TAG, "onInitialNavigationConsumed: clearing pendingReaderJumpBackState=$pendingReaderJumpBackState", Exception("stacktrace"))
                        pendingReaderInitialLocation = null
                        pendingReaderJumpBackState = null
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
                    bookmarkDao = appContainer.bookmarkDao,
                    onOpenAnnotation = { target ->
                        navigatingFromVideoNotes = true
                        navController.navigate(
                            Screen.Reader.createRoute(
                                subtitleId = target.subtitleId,
                                highlightStart = target.highlightStart,
                                highlightEnd = target.highlightEnd,
                                bookmarkStart = target.bookmarkStart
                            )
                        ) {
                            navController.previousBackStackEntry?.destination?.id?.let { previousDestinationId ->
                                popUpTo(previousDestinationId) {
                                    inclusive = true
                                }
                            }
                        }
                    },
                    onDismiss = {
                        Log.d(TAG, "VideoNotes onDismiss: navigatingFromVideoNotes=$navigatingFromVideoNotes pendingReaderJumpBackState=$pendingReaderJumpBackState")
                        if (!navigatingFromVideoNotes) {
                            pendingReaderJumpBackState = null
                            navController.popBackStack()
                        }
                        navigatingFromVideoNotes = false
                    }
                )
            }
        }
    }
}
