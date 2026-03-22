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
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Extract filtering logic
    val uniqueChannels = remember(uiState.savedSubtitles) {
        uiState.savedSubtitles.map { it.channelName }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    
    // Apply filter
    val filteredSubtitles = if (uiState.selectedChannelFilter == null) {
        uiState.savedSubtitles
    } else {
        uiState.savedSubtitles.filter { it.channelName == uiState.selectedChannelFilter }
    }

    // Group subtitles by videoId
    val libraryItems = remember(filteredSubtitles) {
        filteredSubtitles.groupBy { it.videoId }
            .map { (_, subtitles) ->
                val first = subtitles.first()
                LibraryItem(
                    videoId = first.videoId,
                    title = first.title,
                    channelName = first.channelName,
                    subtitles = subtitles.sortedBy { it.languageCode },
                    uploadDate = first.uploadDate,
                    lastDownloaded = subtitles.maxOf { it.createdAt },
                    lastOpenedAt = subtitles.maxOf { it.lastOpenedAt }
                )
            }
    }

    // Sort library items
    val sortedLibraryItems = remember(libraryItems, uiState.sortOption, uiState.isAscending) {
        val sorted = when (uiState.sortOption) {
            SortOption.TITLE -> libraryItems.sortedBy { it.title }
            SortOption.CHANNEL_NAME -> libraryItems.sortedBy { it.channelName }
            SortOption.DATE_PUBLISHED -> libraryItems.sortedBy { it.uploadDate }
            SortOption.DOWNLOADED -> libraryItems.sortedBy { it.lastDownloaded }
            SortOption.LAST_OPENED -> libraryItems.sortedBy { it.lastOpenedAt }
        }
        if (uiState.isAscending) sorted else sorted.reversed()
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
            // Filter and Sort Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel Filter Dropdown
                var expandedFilter by remember { mutableStateOf(false) }

                Box(modifier = Modifier.weight(1f)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedFilter,
                        onExpandedChange = { expandedFilter = !expandedFilter }
                    ) {
                        OutlinedTextField(
                            value = uiState.selectedChannelFilter ?: "All Channels",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFilter) },
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
                                    viewModel.setChannelFilter(null)
                                    expandedFilter = false
                                }
                            )
                            uniqueChannels.forEach { channel ->
                                DropdownMenuItem(
                                    text = { Text(channel) },
                                    onClick = {
                                        viewModel.setChannelFilter(channel)
                                        expandedFilter = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Sort Menu
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
                                    viewModel.setSortOption(option)
                                    expandedSort = false
                                },
                                trailingIcon = {
                                    if (uiState.sortOption == option) {
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

                // Order Toggle
                IconButton(onClick = { viewModel.toggleSortOrder() }) {
                    Icon(
                        imageVector = if (uiState.isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = if (uiState.isAscending) "Ascending" else "Descending"
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (sortedLibraryItems.isEmpty()) {
                    item {
                        Text(
                            text = "No saved subtitles found.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(
                        items = sortedLibraryItems,
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
        }
    }
}

data class LibraryItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val subtitles: List<SubtitleEntity>,
    val uploadDate: Long,
    val lastDownloaded: Long,
    val lastOpenedAt: Long
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryItemCard(
    item: LibraryItem,
    onSubtitleClick: (Long) -> Unit,
    onVideoClick: (String) -> Unit,
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
                    clipboardManager.setText(AnnotatedString(item.videoId))
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
                        putExtra(Intent.EXTRA_TEXT, item.videoId)
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
