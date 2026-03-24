package com.deedeedev.ytreader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    viewModel: HomeViewModel,
    collectionId: String,
    onSubtitleClick: (Long) -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedChannelFilter by remember(collectionId) { mutableStateOf<String?>(null) }
    var sortOption by remember(collectionId) { mutableStateOf(SortOption.DOWNLOADED) }
    var isAscending by remember(collectionId) { mutableStateOf(false) }
    var addToCollectionTargetVideoId by remember { mutableStateOf<String?>(null) }

    val collection = remember(uiState.collections, collectionId) {
        uiState.collections.firstOrNull { it.id == collectionId }
    }

    val collectionVideoIds = remember(collection) {
        collection?.videoIds.orEmpty()
    }

    val uniqueChannels by remember(collectionVideoIds) {
        viewModel.observeCollectionChannels(collectionVideoIds)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val sortedItems by remember(collectionVideoIds, selectedChannelFilter, sortOption, isAscending) {
        viewModel.observeCollectionItems(
            videoIds = collectionVideoIds,
            channelName = selectedChannelFilter,
            sortOption = sortOption,
            isAscending = isAscending
        )
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val onlyInCollectionsCount = remember(collection, uiState.savedSubtitles) {
        collection?.videoIds?.count { videoId ->
            uiState.savedSubtitles.any { subtitle ->
                subtitle.videoId == videoId && !subtitle.isInLibrary
            }
        } ?: 0
    }

    LaunchedEffect(uniqueChannels, selectedChannelFilter) {
        if (selectedChannelFilter != null && selectedChannelFilter !in uniqueChannels) {
            selectedChannelFilter = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (collection == null) {
                Text(
                    text = "Collection not found.",
                    style = MaterialTheme.typography.bodyLarge
                )
                return@Column
            }

            Text(
                text = collection.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${collection.videoIds.size} videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            if (onlyInCollectionsCount > 0) {
                Text(
                    text = "$onlyInCollectionsCount only in collections",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LibraryListControls(
                channels = uniqueChannels,
                selectedChannelFilter = selectedChannelFilter,
                sortOption = sortOption,
                isAscending = isAscending,
                onChannelFilterChange = { selectedChannelFilter = it },
                onSortOptionChange = { sortOption = it },
                onSortDirectionToggle = { isAscending = !isAscending },
                modifier = Modifier.padding(vertical = 16.dp)
            )

            LibraryListSection(
                items = sortedItems,
                emptyText = collectionEmptyText(
                    totalCollectionVideoCount = collection.videoIds.size,
                    selectedChannelFilter = selectedChannelFilter
                ),
                modifier = Modifier.fillMaxSize(),
                key = { it.videoId }
            ) { item ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.removeVideoFromCollection(collection.id, item.videoId)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Video removed from ${collection.name}",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.addVideoToCollection(collection.id, item.videoId)
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    Color.Transparent
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        ) {
                            LibraryItemCard(
                                item = item,
                                onSubtitleClick = onSubtitleClick,
                                onVideoClick = onVideoClick,
                                onAddToCollection = { addToCollectionTargetVideoId = item.videoId },
                                showLibraryStatusBadge = false,
                                showCollectionBadge = false,
                                onRestoreToLibrary = if (!item.isInLibrary) {
                                    { viewModel.restoreLibraryItem(item.subtitles) }
                                } else {
                                    null
                                },
                                onRemoveFromCollection = {
                                    viewModel.removeVideoFromCollection(collection.id, item.videoId)
                                },
                                onSubtitleDelete = { subtitle ->
                                    viewModel.deleteSubtitle(subtitle)
                                },
                                onSubtitleDownloadAgain = { subtitle ->
                                    viewModel.downloadSubtitleAgain(subtitle)
                                },
                                downloadingSubtitleIds = uiState.downloadingSubtitleIds
                            )
                        }
            }
        }
    }

    addToCollectionTargetVideoId?.let { videoId ->
        AddToCollectionDialog(
            collections = uiState.collections,
            onDismiss = { addToCollectionTargetVideoId = null },
            onCreateCollection = { name ->
                val created = viewModel.createCollection(name)
                if (!created) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Could not create collection",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                created
            },
            onAddToCollection = { targetCollectionId ->
                val added = viewModel.addVideoToCollection(targetCollectionId, videoId)
                if (added) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Added to collection",
                            duration = SnackbarDuration.Short
                        )
                    }
                    addToCollectionTargetVideoId = null
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Could not add to collection",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        )
    }

}
