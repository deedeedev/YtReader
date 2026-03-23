package com.deedeedev.ytreader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.collectAsState
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
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer
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
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedChannelFilter by remember(collectionId) { mutableStateOf<String?>(null) }
    var sortOption by remember(collectionId) { mutableStateOf(SortOption.DOWNLOADED) }
    var isAscending by remember(collectionId) { mutableStateOf(false) }
    var addToCollectionTargetVideoId by remember { mutableStateOf<String?>(null) }

    val collection = remember(uiState.collections, collectionId) {
        uiState.collections.firstOrNull { it.id == collectionId }
    }

    val libraryItemsByVideoId = remember(uiState.savedSubtitles) {
        uiState.savedSubtitles
            .groupBy { it.videoId }
            .mapValues { (_, subtitles) ->
                val first = subtitles.first()
                LibraryItem(
                    videoId = first.videoId,
                    videoUrl = first.videoUrl.ifBlank {
                        YouTubeVideoIdNormalizer.extractVideoId(first.videoId)?.let {
                            YouTubeVideoIdNormalizer.canonicalWatchUrl(it)
                        } ?: first.videoId
                    },
                    title = first.title,
                    channelName = first.channelName,
                    subtitles = subtitles.sortedBy { it.languageCode },
                    uploadDate = first.uploadDate,
                    lastDownloaded = subtitles.maxOf { it.createdAt },
                    lastOpenedAt = subtitles.maxOf { it.lastOpenedAt }
                )
            }
    }

    val collectionItems = remember(collection, libraryItemsByVideoId) {
        if (collection == null) {
            emptyList()
        } else {
            collection.videoIds.mapNotNull { libraryItemsByVideoId[it] }
        }
    }

    val missingCount = remember(collection, collectionItems) {
        if (collection == null) {
            0
        } else {
            collection.videoIds.size - collectionItems.size
        }
    }

    val uniqueChannels = remember(collectionItems) {
        collectionItems.map { it.channelName }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    LaunchedEffect(uniqueChannels, selectedChannelFilter) {
        if (selectedChannelFilter != null && selectedChannelFilter !in uniqueChannels) {
            selectedChannelFilter = null
        }
    }

    val filteredItems = remember(collectionItems, selectedChannelFilter) {
        if (selectedChannelFilter == null) {
            collectionItems
        } else {
            collectionItems.filter { it.channelName == selectedChannelFilter }
        }
    }

    val sortedItems = remember(filteredItems, sortOption, isAscending) {
        val sorted = when (sortOption) {
            SortOption.TITLE -> filteredItems.sortedBy { it.title }
            SortOption.CHANNEL_NAME -> filteredItems.sortedBy { it.channelName }
            SortOption.DATE_PUBLISHED -> filteredItems.sortedBy { it.uploadDate }
            SortOption.DOWNLOADED -> filteredItems.sortedBy { it.lastDownloaded }
            SortOption.LAST_OPENED -> filteredItems.sortedBy { it.lastOpenedAt }
        }
        if (isAscending) sorted else sorted.reversed()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
            if (missingCount > 0) {
                Text(
                    text = "$missingCount removed from library",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var expandedFilter by remember { mutableStateOf(false) }

                Box(modifier = Modifier.weight(1f)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedFilter,
                        onExpandedChange = { expandedFilter = !expandedFilter }
                    ) {
                        OutlinedTextField(
                            value = selectedChannelFilter ?: "All Channels",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFilter)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                .fillMaxWidth(),
                            label = { Text("Filter by Channel") }
                        )
                        ExposedDropdownMenu(
                            expanded = expandedFilter,
                            onDismissRequest = { expandedFilter = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Channels") },
                                onClick = {
                                    selectedChannelFilter = null
                                    expandedFilter = false
                                }
                            )
                            uniqueChannels.forEach { channel ->
                                DropdownMenuItem(
                                    text = { Text(channel) },
                                    onClick = {
                                        selectedChannelFilter = channel
                                        expandedFilter = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                var expandedSort by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expandedSort = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort"
                        )
                    }
                    DropdownMenu(
                        expanded = expandedSort,
                        onDismissRequest = { expandedSort = false }
                    ) {
                        val sortOptions = mapOf(
                            SortOption.TITLE to "Title",
                            SortOption.CHANNEL_NAME to "Channel Name",
                            SortOption.DATE_PUBLISHED to "Date Published",
                            SortOption.DOWNLOADED to "Downloaded",
                            SortOption.LAST_OPENED to "Last opened"
                        )

                        sortOptions.forEach { (option, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    sortOption = option
                                    expandedSort = false
                                },
                                trailingIcon = {
                                    if (sortOption == option) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = { isAscending = !isAscending }) {
                    Icon(
                        imageVector = if (isAscending) {
                            Icons.Default.ArrowUpward
                        } else {
                            Icons.Default.ArrowDownward
                        },
                        contentDescription = if (isAscending) "Ascending" else "Descending"
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (sortedItems.isEmpty()) {
                    item {
                        val emptyText = when {
                            collection.videoIds.isEmpty() -> "No videos in this collection."
                            filteredItems.isEmpty() && selectedChannelFilter != null -> "No videos for this channel."
                            else -> "No videos found in this collection."
                        }
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(
                        items = sortedItems,
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
                                onDelete = {
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
