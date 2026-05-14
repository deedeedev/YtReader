package com.deedeedev.ytreader.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.ARCHIVED_COLLECTION_ID
import com.deedeedev.ytreader.data.NoteRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.VideoRepository
import com.deedeedev.ytreader.ui.components.EpubExportDialog
import com.deedeedev.ytreader.ui.home.CollectionsUiState
import com.deedeedev.ytreader.ui.home.CollectionsEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onCollectionClick: (String) -> Unit,
    subtitleRepository: SubtitleRepository,
    videoRepository: VideoRepository,
    noteRepository: NoteRepository,
    modifier: Modifier = Modifier
) {
    val uiState: CollectionsUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val visibleCollections = remember { mutableStateListOf<VideoCollection>() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VideoCollection?>(null) }
    var deleteTarget by remember { mutableStateOf<VideoCollection?>(null) }
    var showEpubExport by remember { mutableStateOf(false) }
    var epubExportVideoIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var epubExportTitle by remember { mutableStateOf("") }

    LaunchedEffect(uiState.collections) {
        visibleCollections.clear()
        visibleCollections.addAll(uiState.collections)
    }

    val dragDropState = rememberCollectionsDragDropState(
        listState = listState,
        collections = visibleCollections,
        onOrderChanged = { orderedIds ->
            viewModel.reorderCollections(orderedIds)
        }
    )

    val videosOnlyInCollections = remember(uiState.savedSubtitles) {
        val subs: List<com.deedeedev.ytreader.data.local.SubtitleEntity> = uiState.savedSubtitles
        subs.groupBy { sub -> sub.videoId }
            .mapValues { (_, subtitles) -> subtitles.any { sub -> !sub.isInLibrary } }
    }
    val readVideosById = remember(uiState.savedSubtitles, uiState.readingProgressByVideoId) {
        val subs: List<com.deedeedev.ytreader.data.local.SubtitleEntity> = uiState.savedSubtitles
        subs.groupBy { sub -> sub.videoId }
            .mapValues { (_, subtitles) -> uiState.readingProgressByVideoId[subtitles.first().videoId] ?: 0 }
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
                    text = stringResource(R.string.collections),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                ElevatedButton(onClick = { showCreateDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.collection_new))
                }
            }

            if (visibleCollections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.collections_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (visibleCollections.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.collections_empty),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    itemsIndexed(items = visibleCollections, key = { _, item -> item.id }) { index, collection ->
                        val onlyInCollectionsCount = collection.videoIds.count {
                            videosOnlyInCollections[it] == true
                        }
                        val readCount = collection.videoIds.count {
                            (readVideosById[it] ?: 0) >= 100
                        }
                        CollectionCard(
                            collection = collection,
                            readCount = readCount,
                            onlyInCollectionsCount = onlyInCollectionsCount,
                            dragDropState = dragDropState,
                            index = index,
                            isSystem = collection.id == ARCHIVED_COLLECTION_ID,
                            onOpen = { onCollectionClick(collection.id) },
                            onRename = { renameTarget = collection },
                            onDelete = { deleteTarget = collection },
                            onExport = {
                                epubExportVideoIds = collection.videoIds
                                epubExportTitle = collection.name
                                showEpubExport = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CollectionNameDialog(
            title = context.getString(R.string.collection_new_label),
            confirmLabel = context.getString(R.string.collection_create),
            initialValue = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                val created = viewModel.createCollection(name)
                showCreateDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = if (created) {
                            context.getString(R.string.collection_created)
                        } else {
                            context.getString(R.string.collection_create_error)
                        },
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    renameTarget?.let { target ->
        CollectionNameDialog(
            title = context.getString(R.string.collection_rename),
            confirmLabel = context.getString(R.string.save),
            initialValue = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                val renamed = viewModel.renameCollection(target.id, name)
                renameTarget = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = if (renamed) {
                            context.getString(R.string.collection_renamed)
                        } else {
                            context.getString(R.string.collection_rename_error)
                        },
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.collection_delete_title)) },
            text = { Text(stringResource(R.string.collection_delete_message, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCollection(target.id)
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showEpubExport && epubExportVideoIds.isNotEmpty()) {
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

@Composable
private fun CollectionCard(
    collection: VideoCollection,
    readCount: Int,
    onlyInCollectionsCount: Int,
    dragDropState: CollectionsDragDropState,
    index: Int,
    isSystem: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDragging = dragDropState.currentIndex == index
    val rawOffset = if (isDragging) {
        dragDropState.draggedDistance
    } else {
        dragDropState.displacementFor(collection.id)
    }
    val animatedOffset by animateFloatAsState(
        targetValue = rawOffset,
        animationSpec = if (isDragging) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "dragOffset"
    )
    val videosCountLabel = pluralStringResource(
        R.plurals.collection_videos_count,
        collection.videoIds.size,
        collection.videoIds.size
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDragging) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardDefaults.shape)
                } else {
                    Modifier
                }
            )
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = animatedOffset
            }
            .pointerInput(collection.id) {
                if (!isSystem) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            showMenu = false
                            dragDropState.startDragging(collection.id)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragDropState.onDrag(dragAmount.y)
                        },
                        onDragEnd = { dragDropState.onDragStopped() },
                        onDragCancel = { dragDropState.onDragStopped() }
                    )
                }
            }
            .clickable(onClick = onOpen),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = videosCountLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = stringResource(
                            R.string.collection_read_progress,
                            readCount,
                            collection.videoIds.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (onlyInCollectionsCount > 0) {
                        Text(
                            text = stringResource(
                                R.string.collection_only_in_collections,
                                onlyInCollectionsCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.collection_options)
                    )
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (!isSystem) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.collection_rename)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.epub_export_collection)) },
                    onClick = {
                        showMenu = false
                        onExport()
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.IosShare, contentDescription = null)
                    }
                )
            }
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
                label = { Text(stringResource(R.string.collection_name)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun rememberCollectionsDragDropState(
    listState: LazyListState,
    collections: SnapshotStateList<VideoCollection>,
    onOrderChanged: (List<String>) -> Unit
): CollectionsDragDropState {
    val scope = rememberCoroutineScope()
    return remember(listState, collections, onOrderChanged, scope) {
        CollectionsDragDropState(
            listState = listState,
            collections = collections,
            onOrderChanged = onOrderChanged,
            scope = scope
        )
    }
}

private class CollectionsDragDropState(
    private val listState: LazyListState,
    private val collections: SnapshotStateList<VideoCollection>,
    private val onOrderChanged: (List<String>) -> Unit,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    private var _draggedDistance by mutableFloatStateOf(0f)
    val draggedDistance: Float get() = _draggedDistance
    private var scrollJob: Job? = null
    private var _displacements by mutableStateOf<Map<String, Float>>(emptyMap())
    private var _currentIndex by mutableIntStateOf(-1)
    val currentIndex: Int get() = _currentIndex

    fun startDragging(collectionId: String) {
        val index = collections.indexOfFirst { it.id == collectionId }
        if (index >= 0) {
            _currentIndex = index
            _draggedDistance = 0f
            _displacements = emptyMap()
        }
    }

    fun displacementFor(collectionId: String): Float {
        return _displacements[collectionId] ?: 0f
    }

    fun onDrag(dragAmount: Float) {
        val activeIndex = _currentIndex
        if (activeIndex !in collections.indices) return

        _draggedDistance += dragAmount
        val currentItem = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == activeIndex } ?: return
        val targetItem = currentTargetItem(currentItem)

        if (targetItem != null && targetItem.index in collections.indices) {
            val moveDirection = targetItem.index - activeIndex
            val spacingPx = computeItemSpacing()
            val displacementAmount = if (moveDirection > 0) {
                -(currentItem.size + spacingPx)
            } else {
                currentItem.size + spacingPx
            }.toFloat()

            val range = if (moveDirection > 0) {
                (activeIndex + 1)..targetItem.index
            } else {
                targetItem.index until activeIndex
            }

            val newDisplacements = _displacements.toMutableMap()
            for (i in range) {
                val id = collections[i].id
                newDisplacements[id] = displacementAmount
            }
            _displacements = newDisplacements

            collections.move(activeIndex, targetItem.index)
            _draggedDistance += currentItem.offset - targetItem.offset
            _currentIndex = targetItem.index
        }

        scheduleAutoScroll()
    }

    fun onDragStopped() {
        scrollJob?.cancel()
        scrollJob = null

        if (_currentIndex != -1) {
            onOrderChanged(collections.map { it.id })
        }

        _currentIndex = -1
        _draggedDistance = 0f
        _displacements = emptyMap()
    }

    private fun currentTargetItem(currentItem: LazyListItemInfo): LazyListItemInfo? {
        val startOffset = currentItem.offset + _draggedDistance
        val endOffset = startOffset + currentItem.size
        val middleOffset = startOffset + ((endOffset - startOffset) / 2f)

        return listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != currentItem.index && middleOffset in item.offset.toFloat()..(item.offset + item.size).toFloat()
        }
    }

    private fun computeItemSpacing(): Int {
        val items = listState.layoutInfo.visibleItemsInfo.sortedBy { it.index }
        if (items.size < 2) return 0
        return items[1].offset - items[0].offset - items[0].size
    }

    private fun calculateOverscroll(): Float {
        val activeIndex = _currentIndex
        if (activeIndex !in collections.indices) return 0f
        val currentItem = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == activeIndex } ?: return 0f
        val layoutInfo = listState.layoutInfo
        val startOffset = currentItem.offset + _draggedDistance
        val endOffset = startOffset + currentItem.size
        return when {
            _draggedDistance > 0 -> (endOffset - layoutInfo.viewportEndOffset).coerceAtLeast(0f)
            _draggedDistance < 0 -> (startOffset - layoutInfo.viewportStartOffset).coerceAtMost(0f)
            else -> 0f
        }
    }

    private fun scheduleAutoScroll() {
        scrollJob?.cancel()
        scrollJob = scope.launch {
            while (true) {
                val overscroll = calculateOverscroll()
                if (overscroll == 0f) break
                val speed = overscroll.coerceIn(-MAX_SCROLL_SPEED, MAX_SCROLL_SPEED)
                listState.scrollBy(speed)
                delay(AUTO_SCROLL_INTERVAL_MS)
            }
        }
    }

    companion object {
        private const val MAX_SCROLL_SPEED = 20f
        private const val AUTO_SCROLL_INTERVAL_MS = 16L
    }
}

private fun <T> SnapshotStateList<T>.move(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) {
        return
    }
    add(toIndex, removeAt(fromIndex))
}
