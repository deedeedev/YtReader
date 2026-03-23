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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
                sortOption = uiState.sortOption,
                isAscending = uiState.isAscending,
                onChannelFilterChange = viewModel::setChannelFilter,
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
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    val deletedSubtitles = item.subtitles
                                    viewModel.deleteLibraryItem(deletedSubtitles.first())

                                    coroutineScope.launch {
                                        val autoDismissJob = launch {
                                            delay(5_000)
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                        }
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Video removed",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Indefinite
                                        )
                                        autoDismissJob.cancel()
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreLibraryItem(deletedSubtitles)
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
                                    viewModel.deleteLibraryItem(item.subtitles.first())
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
    onAddToCollection: () -> Unit,
    onDelete: () -> Unit,
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
                    onClick = { onVideoClick(item.videoUrl) },
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
                text = { Text("Delete entry") },
                onClick = {
                    onDelete()
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
