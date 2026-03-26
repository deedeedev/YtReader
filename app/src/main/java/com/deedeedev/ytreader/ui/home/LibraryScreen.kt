package com.deedeedev.ytreader.ui.home

import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.local.SubtitleEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Check

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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var addToCollectionTargetVideoId by remember { mutableStateOf<String?>(null) }

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
                sortOption = uiState.sortOption,
                isAscending = uiState.isAscending,
                onChannelFilterChange = viewModel::setChannelFilter,
                onVisibilityFilterChange = viewModel::setLibraryVisibilityFilter,
                onSortOptionChange = viewModel::setSortOption,
                onSortDirectionToggle = viewModel::toggleSortOrder,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LibraryListSection(
                items = libraryItems,
                emptyText = "No saved subtitles found.",
                modifier = Modifier.fillMaxSize(),
                key = { it.videoId }
            ) { item ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                when (it) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        viewModel.markVideoAsRead(item.videoId)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = "Marked as read",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                        false
                                    }
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        val deletedSubtitles = item.subtitles
                                        viewModel.removeLibraryItem(deletedSubtitles)

                                        coroutineScope.launch {
                                            val autoDismissJob = launch {
                                                delay(5_000)
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                            }
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Removed from Library",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Indefinite
                                            )
                                            autoDismissJob.cancel()
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restoreLibraryItem(deletedSubtitles)
                                            }
                                        }
                                        true
                                    }
                                    SwipeToDismissBoxValue.Settled -> false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = true,
                            backgroundContent = {
                                val isStartToEnd = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                                val isEndToStart = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                val color = when {
                                    isStartToEnd -> MaterialTheme.colorScheme.secondaryContainer
                                    isEndToStart -> MaterialTheme.colorScheme.errorContainer
                                    else -> Color.Transparent
                                }
                                val icon = when {
                                    isStartToEnd -> Icons.Default.Check
                                    isEndToStart -> Icons.Default.Delete
                                    else -> null
                                }
                                val alignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                val tint = when {
                                    isStartToEnd -> MaterialTheme.colorScheme.onSecondaryContainer
                                    isEndToStart -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> Color.Transparent
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = alignment
                                ) {
                                    icon?.let {
                                        Icon(
                                            imageVector = it,
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
                                onAddToCollection = { addToCollectionTargetVideoId = item.videoId },
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
            onAddToCollection = { collectionId ->
                val added = viewModel.addVideoToCollection(collectionId, videoId)
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryItemCard(
    item: LibraryItem,
    onSubtitleClick: (Long) -> Unit,
    onVideoClick: (String) -> Unit,
    onVideoSearchAgain: (String) -> Unit,
    onAddToCollection: () -> Unit,
    onResetProgress: () -> Unit,
    showLibraryStatusBadge: Boolean = true,
    showCollectionBadge: Boolean = true,
    onRemoveFromLibrary: (() -> Unit)? = null,
    onRestoreToLibrary: (() -> Unit)? = null,
    onRemoveFromCollection: (() -> Unit)? = null,
    onSubtitleDelete: (SubtitleEntity) -> Unit,
    onSubtitleDownloadAgain: (SubtitleEntity) -> Unit,
    downloadingSubtitleIds: Set<Long>
) {
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

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
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (item.channelName.isNotBlank()) {
                    Text(
                        text = item.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if ((showLibraryStatusBadge && !item.isInLibrary) || (showCollectionBadge && item.isInCollections)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (showLibraryStatusBadge && !item.isInLibrary) {
                            Text(
                                text = "Removed from Library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        if (showCollectionBadge && item.isInCollections) {
                            Text(
                                text = if (item.collectionCount == 1) "In 1 collection"
                                else "In ${item.collectionCount} collections",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                if (item.isRead) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReadingStatusBadge(text = "Read")
                    }
                } else {
                    LibraryReadingProgress(
                        percent = item.readingProgressPercent,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
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

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Search again") },
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
                text = { Text("Copy video URL") },
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
                text = { Text("Share video URL") },
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
                text = { Text("Add to collection") },
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
            DropdownMenuItem(
                text = { Text("Reset progress") },
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
                    text = { Text("Restore to Library") },
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
                    text = { Text("Remove from Library") },
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
                    text = { Text("Remove from this collection") },
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
private fun ReadingProgressText(percent: Int) {
    Text(
        text = "$percent%",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun LibraryReadingProgress(
    percent: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ReadingProgressText(percent = percent)
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
        title = { Text("Add to collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    singleLine = true,
                    label = { Text("New collection") },
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
                    Text("Create")
                }

                if (collections.isEmpty()) {
                    Text(
                        text = "No collections yet. Create one above.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Choose a collection",
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
                Text("Close")
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
                            append(" (AUTO)")
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
                text = { Text("Copy subtitles") },
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
                text = { Text("Download again") },
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
                text = { Text("Delete subtitles") },
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
            title = { Text("Overwrite subtitles?") },
            text = { Text("This will replace the current version with a freshly downloaded one.") },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadAgainDialog = false
                    onDownloadAgain()
                }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadAgainDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete subtitles?") },
            text = { Text("This will permanently remove the subtitles from your library.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
