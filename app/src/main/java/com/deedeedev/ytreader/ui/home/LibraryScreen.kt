package com.deedeedev.ytreader.ui.home

import android.content.Intent
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.VideoThumbnailStore
import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.local.SubtitleEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: HomeViewModel,
    onSubtitleClick: (Long) -> Unit,
    onVideoClick: (String) -> Unit,
    onVideoSearchAgain: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uniqueChannels by viewModel.libraryChannels.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var addToCollectionTargetVideoId by remember { mutableStateOf<String?>(null) }

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
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LibraryListSection(
                items = libraryItems,
                emptyText = stringResource(R.string.library_empty),
                modifier = Modifier.fillMaxSize(),
                key = { it.videoId }
            ) { item ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                when (it) {
                                    SwipeToDismissBoxValue.EndToStart -> {
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
                                        true
                                    }
                                    SwipeToDismissBoxValue.StartToEnd,
                                    SwipeToDismissBoxValue.Settled -> false
                                }
                            },
                            positionalThreshold = { totalDistance -> totalDistance * 0.8f }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val isEndToStart = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                val color = if (isEndToStart) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    Color.Transparent
                                }
                                val tint = if (isEndToStart) {
                                    MaterialTheme.colorScheme.onErrorContainer
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
                                    if (isEndToStart) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = tint
                                        )
                                    }
                                }
                            }
                        ) {
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
                                onRemoveFromLibrary = {
                                    viewModel.removeLibraryItem(item.subtitles)
                                },
                                onSubtitleDelete = { subtitle ->
                                    viewModel.deleteSubtitle(subtitle)
                                },
                                onSubtitleDownloadAgain = { subtitle ->
                                    viewModel.downloadSubtitleAgain(subtitle)
                                },
                                downloadingSubtitleIds = uiState.downloadingSubtitleIds,
                                isDownloadingThumbnail = uiState.downloadingThumbnailVideoIds.contains(item.videoId)
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

}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryItemCard(
    item: LibraryItem,
    onSubtitleClick: (Long) -> Unit,
    onVideoClick: (String) -> Unit,
    onVideoSearchAgain: (String) -> Unit,
    onMarkAsRead: (() -> Unit)? = null,
    onAddToCollection: () -> Unit,
    onDownloadThumbnail: (() -> Unit)? = null,
    onResetProgress: () -> Unit,
    showLibraryStatusBadge: Boolean = true,
    showCollectionBadge: Boolean = true,
    onRemoveFromLibrary: (() -> Unit)? = null,
    onRestoreToLibrary: (() -> Unit)? = null,
    onRemoveFromCollection: (() -> Unit)? = null,
    onSubtitleDelete: (SubtitleEntity) -> Unit,
    onSubtitleDownloadAgain: (SubtitleEntity) -> Unit,
    downloadingSubtitleIds: Set<Long>,
    isDownloadingThumbnail: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val removedFromLibraryLabel = stringResource(R.string.library_removed)
    val readLabel = stringResource(R.string.library_read)
    val thumbnailFile = remember(item.thumbnailLocalPath) {
        VideoThumbnailStore.resolve(context, item.thumbnailLocalPath)
    }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onVideoClick(item.videoId) },
                    onLongClick = { showMenu = true }
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                ThumbnailPreview(
                    thumbnailFile = thumbnailFile,
                    title = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (item.channelName.isNotBlank()) {
                        Text(
                            text = item.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if ((showLibraryStatusBadge && !item.isInLibrary) || (showCollectionBadge && item.isInCollections)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            if (showLibraryStatusBadge && !item.isInLibrary) {
                                MetadataBadge(text = removedFromLibraryLabel)
                            }
                            if (showCollectionBadge && item.isInCollections) {
                                MetadataBadge(
                                    text = if (item.collectionCount == 1) {
                                        stringResource(R.string.library_in_collection_one)
                                    } else {
                                        stringResource(R.string.library_in_collection_many, item.collectionCount)
                                    }
                                )
                            }
                        }
                    }

                    if (item.isRead) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReadingStatusBadge(text = readLabel)
                        }
                    } else {
                        LibraryReadingProgress(
                            percent = item.readingProgressPercent,
                            currentPage = item.currentPage,
                            totalPages = item.totalPages,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item.subtitles.forEach { subtitle ->
                            SubtitleChip(
                                subtitle = subtitle,
                                onClick = { onSubtitleClick(subtitle.id) },
                                onDelete = { onSubtitleDelete(subtitle) },
                                onDownloadAgain = { onSubtitleDownloadAgain(subtitle) },
                                isDownloading = downloadingSubtitleIds.contains(subtitle.id)
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_search_again)) },
                onClick = {
                    onVideoSearchAgain(item.videoUrl)
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_copy_video_url)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(item.videoUrl))
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_share_video_url)) },
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, item.videoUrl)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.collection_add)) },
                onClick = {
                    onAddToCollection()
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null
                    )
                }
            )
            if (item.thumbnailLocalPath == null) {
                onDownloadThumbnail?.let { downloadThumbnail ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isDownloadingThumbnail) {
                                    stringResource(R.string.library_downloading_thumbnail)
                                } else {
                                    stringResource(R.string.library_download_thumbnail)
                                }
                            )
                        },
                        onClick = {
                            if (!isDownloadingThumbnail) {
                                downloadThumbnail()
                                showMenu = false
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
            if (!item.isRead) {
                onMarkAsRead?.let { markAsRead ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_mark_as_read)) },
                        onClick = {
                            markAsRead()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Done,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_reset_progress)) },
                onClick = {
                    onResetProgress()
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null
                    )
                }
            )
            onRestoreToLibrary?.let { restoreToLibrary ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_restore)) },
                    onClick = {
                        restoreToLibrary()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = null
                        )
                    }
                )
            }
            onRemoveFromLibrary?.let { removeFromLibrary ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_removed)) },
                    onClick = {
                        removeFromLibrary()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null
                        )
                    }
                )
            }
            onRemoveFromCollection?.let { removeFromCollection ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.collection_remove_from_this)) },
                    onClick = {
                        removeFromCollection()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ThumbnailPreview(
    thumbnailFile: File?,
    title: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        if (thumbnailFile != null) {
            AsyncImage(
                model = thumbnailFile,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetadataBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReadingStatusBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReadingProgressText(
    percent: Int,
    currentPage: Int,
    totalPages: Int
) {
    Text(
        text = if (currentPage > 0 && totalPages > 0) {
            stringResource(R.string.reader_page_progress, percent, currentPage, totalPages)
        } else {
            stringResource(R.string.library_reading_progress, percent)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun LibraryReadingProgress(
    percent: Int,
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ReadingProgressText(
            percent = percent,
            currentPage = currentPage,
            totalPages = totalPages
        )
        LinearProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            drawStopIndicator = {}
        )
    }
}

@Composable
fun AddToCollectionDialog(
    collections: List<VideoCollection>,
    onDismiss: () -> Unit,
    onCreateCollection: (String) -> Boolean,
    onAddToCollection: (String) -> Unit
) {
    var newCollectionName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collection_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.collection_new_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        val created = onCreateCollection(newCollectionName)
                        if (created) {
                            newCollectionName = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.collection_create))
                }

                if (collections.isEmpty()) {
                    Text(
                        text = stringResource(R.string.collections_empty_dialog),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.collection_choose),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        collections.forEach { collection ->
                            TextButton(
                                onClick = { onAddToCollection(collection.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(collection.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reader_close))
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubtitleChip(
    subtitle: SubtitleEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDownloadAgain: () -> Unit,
    isDownloading: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDownloadAgainDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val autoLabel = stringResource(R.string.library_subtitle_auto)

    Box {
        Surface(
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = Color.Transparent,
            modifier = Modifier
                .height(32.dp)
                .combinedClickable(
                    onClick = { if (!isDownloading) onClick() },
                    onLongClick = { if (!isDownloading) showMenu = true }
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    val languageLabel = buildString {
                        append(subtitle.languageCode.uppercase())
                        if (subtitle.isAutoGenerated) {
                            append(autoLabel)
                        }
                    }
                    Text(
                        text = languageLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu && !isDownloading,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_copy_subtitles)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(subtitle.content))
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_again)) },
                onClick = {
                    showMenu = false
                    showDownloadAgainDialog = true
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_delete_subtitles)) },
                onClick = {
                    showMenu = false
                    showDeleteDialog = true
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null
                    )
                }
            )
        }
    }

    if (showDownloadAgainDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadAgainDialog = false },
            title = { Text(stringResource(R.string.library_overwrite_subtitles_title)) },
            text = { Text(stringResource(R.string.library_overwrite_subtitles_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadAgainDialog = false
                    onDownloadAgain()
                }) {
                    Text(stringResource(R.string.download))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadAgainDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.library_delete_subtitles_title)) },
            text = { Text(stringResource(R.string.library_delete_subtitles_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
