package com.deedeedev.ytreader.ui.home

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.VideoDao
import com.deedeedev.ytreader.ui.components.EpubExportDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    viewModel: HomeViewModel,
    collectionId: String,
    onSubtitleClick: (Long) -> Unit,
    onVideoClick: (String) -> Unit,
    onVideoSearchAgain: (String) -> Unit,
    onBack: () -> Unit,
    subtitleDao: SubtitleDao,
    videoDao: VideoDao,
    highlightNoteDao: HighlightNoteDao,
    bookmarkDao: BookmarkDao,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showEpubExport by remember { mutableStateOf(false) }
    var epubExportVideoIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var epubExportTitle by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeEvent.ShowMessage -> snackbarHostState.showSnackbar(
                    message = event.message,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    var addToCollectionTargetVideoId by remember { mutableStateOf<String?>(null) }
    val filterState = uiState.collectionFilterStates[collectionId] ?: viewModel.getCollectionFilterState(collectionId)

    val collection = remember(uiState.collections, collectionId) {
        uiState.collections.firstOrNull { it.id == collectionId }
    }

    val collectionVideoIds = remember(collection) {
        collection?.videoIds.orEmpty()
    }

    val uniqueChannels by remember(collectionVideoIds) {
        viewModel.observeCollectionChannels(collectionVideoIds)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val sortedItems by remember(
        collectionVideoIds,
        filterState.selectedChannelFilter,
        filterState.readStatusFilter,
        filterState.sortOption,
        filterState.isAscending
    ) {
        viewModel.observeCollectionItems(
            videoIds = collectionVideoIds,
            channelName = filterState.selectedChannelFilter,
            readStatusFilter = filterState.readStatusFilter,
            sortOption = filterState.sortOption,
            isAscending = filterState.isAscending
        )
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val onlyInCollectionsCount = remember(collection, uiState.savedSubtitles) {
        collection?.videoIds?.count { videoId ->
            uiState.savedSubtitles.any { subtitle ->
                subtitle.videoId == videoId && !subtitle.isInLibrary
            }
        } ?: 0
    }

    LaunchedEffect(uniqueChannels, filterState.selectedChannelFilter, collectionId) {
        if (
            filterState.selectedChannelFilter != null &&
            filterState.selectedChannelFilter !in uniqueChannels
        ) {
            viewModel.setCollectionChannelFilter(collectionId, null)
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
                    text = stringResource(R.string.collection_not_found),
                    style = MaterialTheme.typography.bodyLarge
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.collection_back_to_collections)
                        )
                    }
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = {
                    if (collection.videoIds.isNotEmpty()) {
                        epubExportVideoIds = collection.videoIds
                        epubExportTitle = collection.name
                        showEpubExport = true
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.epub_export_empty),
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.IosShare,
                        contentDescription = stringResource(R.string.epub_export_collection)
                    )
                }
            }
            Text(
                text = pluralStringResource(
                    R.plurals.collection_videos_count,
                    collection.videoIds.size,
                    collection.videoIds.size
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            if (onlyInCollectionsCount > 0) {
                Text(
                    text = stringResource(R.string.collection_only_in_collections, onlyInCollectionsCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LibraryListControls(
                channels = uniqueChannels,
                selectedChannelFilter = filterState.selectedChannelFilter,
                readStatusFilter = filterState.readStatusFilter,
                sortOption = filterState.sortOption,
                isAscending = filterState.isAscending,
                onChannelFilterChange = { viewModel.setCollectionChannelFilter(collectionId, it) },
                onReadStatusFilterChange = { viewModel.setCollectionReadStatusFilter(collectionId, it) },
                onSortOptionChange = { viewModel.setCollectionSortOption(collectionId, it) },
                onSortDirectionToggle = { viewModel.toggleCollectionSortOrder(collectionId) },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                modifier = Modifier.padding(vertical = 16.dp)
            )

            val filteredSortedItems = remember(sortedItems, searchQuery) {
                sortedItems.filterByTitle(searchQuery)
            }

            LibraryListSection(
                items = filteredSortedItems,
                emptyText = collectionEmptyText(
                    resources = context.resources,
                    totalCollectionVideoCount = collection.videoIds.size,
                    selectedChannelFilter = filterState.selectedChannelFilter
                ),
                modifier = Modifier.fillMaxSize(),
                key = { it.videoId }
            ) { item ->
                        LibraryItemCard(
                            item = item,
                            onSubtitleClick = onSubtitleClick,
                            onVideoClick = onVideoClick,
                            onVideoSearchAgain = onVideoSearchAgain,
                            onMarkAsRead = {
                                viewModel.markVideoAsRead(item.videoId)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.library_marked_read),
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            onAddToCollection = { addToCollectionTargetVideoId = item.videoId },
                            onDownloadThumbnail = {
                                viewModel.downloadThumbnailForVideo(
                                    videoId = item.videoId,
                                    videoUrl = item.videoUrl,
                                    title = item.title,
                                    channelName = item.channelName,
                                    uploadDate = item.uploadDate
                                )
                            },
                            onResetProgress = { viewModel.resetVideoProgress(item.videoId) },
                            showLibraryStatusBadge = false,
                            showCollectionBadge = false,
                            onRestoreToLibrary = if (!item.isInLibrary) {
                                { viewModel.restoreLibraryItem(item.subtitles) }
                            } else {
                                null
                            },
                            onRemoveFromCollection = {
                                viewModel.removeVideoFromCollection(collection.id, item.videoId)
                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(
                                            R.string.collection_remove_video,
                                            collection.name
                                        ),
                                        actionLabel = context.getString(R.string.undo),
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.addVideoToCollection(collection.id, item.videoId)
                                    }
                                }
                            },
                            onSubtitleDelete = { subtitle ->
                                viewModel.deleteSubtitle(subtitle)
                            },
                            onSubtitleDownloadAgain = { subtitle ->
                                viewModel.downloadSubtitleAgain(subtitle)
                            },
                            downloadingSubtitleIds = uiState.downloadingSubtitleIds,
                            isDownloadingThumbnail = uiState.downloadingThumbnailVideoIds.contains(item.videoId),
                            onExportEpub = { videoId, title ->
                                epubExportVideoIds = listOf(videoId)
                                epubExportTitle = title
                                showEpubExport = true
                            }
                        )
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
                            message = context.getString(R.string.collection_add_error),
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
                            message = context.getString(R.string.collection_add_success),
                            duration = SnackbarDuration.Short
                        )
                    }
                    addToCollectionTargetVideoId = null
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.collection_create_error),
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        )
    }

    if (showEpubExport) {
        EpubExportDialog(
            bookTitle = epubExportTitle,
            videoIds = epubExportVideoIds,
            subtitleDao = subtitleDao,
            videoDao = videoDao,
            highlightNoteDao = highlightNoteDao,
            bookmarkDao = bookmarkDao,
            onDismiss = { showEpubExport = false }
        )
    }

}
