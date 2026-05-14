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
import com.deedeedev.ytreader.data.ARCHIVED_COLLECTION_ID
import com.deedeedev.ytreader.data.NoteRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.VideoRepository
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.ui.components.EpubExportDialog
import com.deedeedev.ytreader.ui.home.CollectionsUiState
import com.deedeedev.ytreader.ui.home.CollectionsEvent
import com.deedeedev.ytreader.ui.home.CollectionFilterState
import com.deedeedev.ytreader.ui.home.LibraryItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    viewModel: CollectionsViewModel,
    collectionId: String,
    onSubtitleClick: (Long, Pair<Int, Int>) -> Unit,
    onVideoClick: (String, Pair<Int, Int>) -> Unit,
    onVideoSearchAgain: (String) -> Unit,
    onBack: () -> Unit,
    subtitleRepository: SubtitleRepository,
    videoRepository: VideoRepository,
    noteRepository: NoteRepository,
    modifier: Modifier = Modifier,
    initialScrollPosition: Pair<Int, Int>? = null
) {
    val uiState: CollectionsUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val videoCardSize by viewModel.videoCardSize.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showEpubExport by remember { mutableStateOf(false) }
    var epubExportVideoIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var epubExportTitle by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event: CollectionsEvent ->
            when (event) {
                is CollectionsEvent.ShowMessage -> snackbarHostState.showSnackbar(
                    message = event.message,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val isArchivedCollection = collectionId == ARCHIVED_COLLECTION_ID

    var addToCollectionTargetVideoId by remember { mutableStateOf<String?>(null) }
    val filterState: CollectionFilterState = uiState.collectionFilterStates[collectionId] ?: viewModel.getCollectionFilterState(collectionId)

    var targetCollection: VideoCollection? = null
    for (c: VideoCollection in uiState.collections) {
        if (c.id == collectionId) {
            targetCollection = c
            break
        }
    }
    val collection: VideoCollection? = targetCollection

    val collectionVideoIds: List<String> = if (collection != null) collection.videoIds else emptyList()

    val uniqueChannels: List<String> by remember(collectionVideoIds) {
        viewModel.observeCollectionChannels(collectionVideoIds)
    }.collectAsStateWithLifecycle(initialValue = emptyList<String>())

    val sortedItems: List<LibraryItem> by remember(
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

    var onlyInCollectionsCount = 0
    if (collection != null) {
        val coll: VideoCollection = collection
        val saved: List<SubtitleEntity> = uiState.savedSubtitles
        onlyInCollectionsCount = coll.videoIds.count { vid: String ->
            val hasNonLibrary = saved.any { sub: SubtitleEntity -> sub.videoId == vid && !sub.isInLibrary }
            hasNonLibrary
        }
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

            var collectionScrollPosition by remember { mutableStateOf<Pair<Int, Int>>(0 to 0) }

            LibraryListSection(
                items = filteredSortedItems,
                emptyText = collectionEmptyText(
                    resources = context.resources,
                    totalCollectionVideoCount = collection.videoIds.size,
                    selectedChannelFilter = filterState.selectedChannelFilter
                ),
                modifier = Modifier.fillMaxSize(),
                key = { it.videoId },
                initialScrollPosition = initialScrollPosition,
                onGetScrollPosition = { position ->
                    collectionScrollPosition = position
                }
            ) { item ->
                        LibraryItemCard(
                            item = item,
                            onSubtitleClick = { id, _ -> onSubtitleClick(id, collectionScrollPosition) },
                            onVideoClick = { id, _ -> onVideoClick(id, collectionScrollPosition) },
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
                            onRestoreToLibrary = if (!item.isInLibrary && !isArchivedCollection) {
                                { viewModel.restoreLibraryItem(item.subtitles) }
                            } else {
                                null
                            },
                            onRemoveFromCollection = if (!isArchivedCollection) {
                                {
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
                                }
                            } else {
                                null
                            },
                            onArchive = null,
                            onUnarchive = if (isArchivedCollection) {
                                {
                                    viewModel.unarchiveVideo(item.videoId)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.library_unarchived),
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            } else {
                                null
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
                            },
                            compact = videoCardSize == VideoCardSize.COMPACT
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
            subtitleRepository = subtitleRepository,
            videoRepository = videoRepository,
            noteRepository = noteRepository,
            onDismiss = { showEpubExport = false }
        )
    }

}
