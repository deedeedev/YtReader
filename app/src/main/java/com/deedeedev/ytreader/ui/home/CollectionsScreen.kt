package com.deedeedev.ytreader.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.data.VideoCollection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    viewModel: HomeViewModel,
    onSubtitleClick: (Long) -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VideoCollection?>(null) }
    var deleteTarget by remember { mutableStateOf<VideoCollection?>(null) }

    val libraryItemsByVideoId = remember(uiState.savedSubtitles) {
        uiState.savedSubtitles
            .groupBy { it.videoId }
            .mapValues { (_, subtitles) ->
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                ElevatedButton(onClick = { showCreateDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.collections.isEmpty()) {
                    item {
                        Text(
                            text = "No collections yet. Create one to organize videos.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    items(items = uiState.collections, key = { it.id }) { collection ->
                        val videosInCollection = collection.videoIds.mapNotNull { libraryItemsByVideoId[it] }
                        val missingCount = collection.videoIds.size - videosInCollection.size
                        CollectionCard(
                            collection = collection,
                            videos = videosInCollection,
                            missingCount = missingCount,
                            onRename = { renameTarget = collection },
                            onDelete = { deleteTarget = collection },
                            onVideoClick = onVideoClick,
                            onSubtitleClick = onSubtitleClick,
                            onRemoveVideo = { videoId ->
                                viewModel.removeVideoFromCollection(collection.id, videoId)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Removed from ${collection.name}",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CollectionNameDialog(
            title = "New collection",
            confirmLabel = "Create",
            initialValue = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                val created = viewModel.createCollection(name)
                showCreateDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = if (created) "Collection created" else "Could not create collection",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    renameTarget?.let { target ->
        CollectionNameDialog(
            title = "Rename collection",
            confirmLabel = "Save",
            initialValue = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                val renamed = viewModel.renameCollection(target.id, name)
                renameTarget = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = if (renamed) "Collection renamed" else "Could not rename collection",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete collection?") },
            text = { Text("${target.name} will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCollection(target.id)
                        deleteTarget = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CollectionCard(
    collection: VideoCollection,
    videos: List<LibraryItem>,
    missingCount: Int,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onVideoClick: (String) -> Unit,
    onSubtitleClick: (Long) -> Unit,
    onRemoveVideo: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${videos.size} videos",
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
                }

                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Collection options")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (videos.isEmpty()) {
                Text(
                    text = "No videos in this collection.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    videos.forEach { video ->
                        CollectionVideoCard(
                            item = video,
                            onVideoClick = onVideoClick,
                            onSubtitleClick = onSubtitleClick,
                            onRemove = { onRemoveVideo(video.videoId) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CollectionVideoCard(
    item: LibraryItem,
    onVideoClick: (String) -> Unit,
    onSubtitleClick: (Long) -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onVideoClick(item.videoId) }, onLongClick = { showMenu = true }),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (item.channelName.isNotBlank()) {
                        Text(
                            text = item.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Video options")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item.subtitles.forEach { subtitle ->
                    FilterChip(
                        selected = false,
                        onClick = { onSubtitleClick(subtitle.id) },
                        label = {
                            val suffix = if (subtitle.isAutoGenerated) " (AUTO)" else ""
                            Text("${subtitle.languageCode.uppercase()}$suffix")
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        }
                    )
                }
            }
        }

        androidx.compose.material3.DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Remove from collection") },
                onClick = {
                    showMenu = false
                    onRemove()
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.RemoveCircleOutline, contentDescription = null)
                }
            )
        }
    }
}

@Composable
private fun CollectionNameDialog(
    title: String,
    confirmLabel: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
