package com.deedeedev.ytreader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.NoteRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.VideoRepository
import com.deedeedev.ytreader.ui.components.EpubExportDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSubtitleClick: (Long, Pair<Int, Int>) -> Unit,
    onVideoClick: (String, Pair<Int, Int>) -> Unit,
    onVideoSearchAgain: (String) -> Unit,
    subtitleRepository: SubtitleRepository,
    videoRepository: VideoRepository,
    noteRepository: NoteRepository,
    modifier: Modifier = Modifier,
    initialScrollPosition: Pair<Int, Int>? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uniqueChannels by viewModel.libraryChannels.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val videoCardSize by viewModel.videoCardSize.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var addToCollectionTargetVideoId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showEpubExport by remember { mutableStateOf(false) }
    var epubExportVideoIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var epubExportTitle by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LibraryEvent.ShowMessage -> snackbarHostState.showSnackbar(
                    message = event.message,
                    duration = SnackbarDuration.Short
                )
            }
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
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.library),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val ids = viewModel.getAllLibraryVideoIds()
                            if (ids.isEmpty()) {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.epub_export_empty),
                                    duration = SnackbarDuration.Short
                                )
                            } else {
                                epubExportVideoIds = ids
                                epubExportTitle = context.getString(R.string.library)
                                showEpubExport = true
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = stringResource(R.string.epub_export_library)
                        )
                    }
                }

                LibraryListControls(
                channels = uniqueChannels,
                selectedChannelFilter = uiState.selectedChannelFilter,
                visibilityFilter = uiState.libraryVisibilityFilter,
                readStatusFilter = uiState.libraryReadStatusFilter,
                sortOption = uiState.sortOption,
                isAscending = uiState.isAscending,
                onChannelFilterChange = viewModel::setChannelFilter,
                onVisibilityFilterChange = viewModel::setLibraryVisibilityFilter,
                onReadStatusFilterChange = viewModel::setLibraryReadStatusFilter,
                onSortOptionChange = viewModel::setSortOption,
                onSortDirectionToggle = viewModel::toggleSortOrder,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val filteredLibraryItems: List<LibraryItem>? = remember(libraryItems, searchQuery) {
                libraryItems?.filterByTitle(searchQuery)
            }

            var libraryScrollPosition by remember { mutableStateOf<Pair<Int, Int>>(0 to 0) }

            if (filteredLibraryItems != null) {
                LibraryListSection(
                    items = filteredLibraryItems,
                    emptyText = stringResource(R.string.library_empty),
                    modifier = Modifier.fillMaxSize(),
                    key = { it.videoId },
                    initialScrollPosition = initialScrollPosition,
                    onGetScrollPosition = { position ->
                        libraryScrollPosition = position
                    }
                ) { item ->
                    LibraryItemCard(
                            item = item,
                            onSubtitleClick = { id, _ -> onSubtitleClick(id, libraryScrollPosition) },
                            onVideoClick = { id, _ -> onVideoClick(id, libraryScrollPosition) },
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
                            onRemoveFromLibrary = {
                                val deletedSubtitles = item.subtitles
                                viewModel.removeLibraryItem(deletedSubtitles)

                                coroutineScope.launch {
                                    val autoDismissJob = launch {
                                        delay(5_000)
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                    }
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.library_removed),
                                        actionLabel = context.getString(R.string.undo),
                                        duration = SnackbarDuration.Indefinite
                                    )
                                    autoDismissJob.cancel()
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreLibraryItem(deletedSubtitles)
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
                            },
                            compact = videoCardSize == VideoCardSize.COMPACT
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
                            message = context.getString(R.string.collection_create_error),
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                created
            },
            onAddToCollection = { collectionId ->
                val added = viewModel.addVideoToCollection(collectionId, videoId)
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
                            message = context.getString(R.string.collection_add_error),
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
