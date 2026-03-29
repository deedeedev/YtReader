package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.SubtitleDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoNotesSheetRoute(
    videoId: String,
    subtitleDao: SubtitleDao,
    highlightNoteDao: HighlightNoteDao,
    bookmarkDao: BookmarkDao,
    onOpenAnnotation: (ReaderAnnotationTarget) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        VideoNotesScreen(
            videoId = videoId,
            subtitleDao = subtitleDao,
            highlightNoteDao = highlightNoteDao,
            bookmarkDao = bookmarkDao,
            onOpenAnnotation = onOpenAnnotation,
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoNotesScreen(
    videoId: String,
    subtitleDao: SubtitleDao,
    highlightNoteDao: HighlightNoteDao,
    bookmarkDao: BookmarkDao,
    onOpenAnnotation: (ReaderAnnotationTarget) -> Unit,
    onDismiss: () -> Unit,
    viewModel: VideoNotesViewModel = viewModel(
        key = "VideoNotes_$videoId",
        factory = VideoNotesViewModel.provideFactory(
            subtitleDao = subtitleDao,
            highlightNoteDao = highlightNoteDao,
            bookmarkDao = bookmarkDao,
            videoId = videoId
        )
    )
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTypes by remember { mutableStateOf(setOf<VideoAnnotationType>()) }
    var showExportMenu by remember { mutableStateOf(false) }
    val defaultTitle = stringResource(R.string.video_notes_default_title)
    val subtitleLabel = stringResource(R.string.video_notes_from_start_to_finish)
    val closeLabel = stringResource(R.string.video_notes_close)
    val exportAnnotationsLabel = stringResource(R.string.video_notes_export_annotations)
    val markdownLabel = stringResource(R.string.video_notes_format_markdown)
    val pdfLabel = stringResource(R.string.video_notes_format_pdf)
    val jsonLabel = stringResource(R.string.video_notes_format_json)
    val csvLabel = stringResource(R.string.video_notes_format_csv)
    val loadingLabel = stringResource(R.string.video_notes_loading)
    val deletedBookmarkLabel = stringResource(R.string.video_notes_deleted_bookmark)
    val deletedHighlightLabel = stringResource(R.string.video_notes_deleted_highlight)
    val deletedNoteLabel = stringResource(R.string.video_notes_deleted_note)
    val undoLabel = stringResource(R.string.undo)

    val filteredItems = remember(uiState.items, selectedTypes) {
        if (selectedTypes.isEmpty()) {
            uiState.items
        } else {
            uiState.items.filter { item -> item.type in selectedTypes }
        }
    }

    val markdownExport = remember(filteredItems, uiState.title, videoId, selectedTypes) {
        buildVideoNotesMarkdown(
            context = context,
            title = uiState.title,
            videoId = videoId,
            selectedTypes = selectedTypes,
            items = filteredItems
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.title.isNotBlank()) uiState.title else defaultTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitleLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = closeLabel)
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            enabled = filteredItems.isNotEmpty()
                        ) {
                            Icon(Icons.Default.IosShare, contentDescription = exportAnnotationsLabel)
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(markdownLabel) },
                                onClick = {
                                    showExportMenu = false
                                    shareVideoNotesMarkdown(context, markdownExport)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(pdfLabel) },
                                onClick = {
                                    showExportMenu = false
                                    shareVideoNotesPdf(
                                        context = context,
                                        title = uiState.title,
                                        channelName = uiState.channelName,
                                        uploadDate = uiState.uploadDate,
                                        videoId = videoId,
                                        selectedTypes = selectedTypes,
                                        items = filteredItems
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(jsonLabel) },
                                onClick = {
                                    showExportMenu = false
                                    shareVideoNotesJson(
                                        context = context,
                                        title = uiState.title,
                                        videoId = videoId,
                                        selectedTypes = selectedTypes,
                                        items = filteredItems
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(csvLabel) },
                                onClick = {
                                    showExportMenu = false
                                    shareVideoNotesCsv(
                                        context = context,
                                        title = uiState.title,
                                        videoId = videoId,
                                        selectedTypes = selectedTypes,
                                        items = filteredItems
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = 16.dp)
        ) {
            VideoNotesSummary(
                totalBookmarks = uiState.totalBookmarks,
                totalHighlights = uiState.totalHighlights,
                totalNotes = uiState.totalNotes,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            VideoNotesFilterRow(
                selectedTypes = selectedTypes,
                onToggleType = { type ->
                    selectedTypes = selectedTypes.toMutableSet().apply {
                        if (!add(type)) remove(type)
                    }.toSet()
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(loadingLabel)
                }
            } else if (filteredItems.isEmpty()) {
                VideoNotesEmptyState(
                    hasAnyAnnotations = uiState.items.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredItems, key = { item -> item.key }) { item ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                when (dismissValue) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        viewModel.deleteAnnotation(item)
                                        coroutineScope.launch {
                                            val autoDismissJob = launch {
                                                delay(5_000)
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                            }
                                            val result = snackbarHostState.showSnackbar(
                                                message = annotationDeletedMessage(
                                                    item = item,
                                                    bookmarkMessage = deletedBookmarkLabel,
                                                    highlightMessage = deletedHighlightLabel,
                                                    noteMessage = deletedNoteLabel
                                                ),
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Indefinite
                                            )
                                            autoDismissJob.cancel()
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restoreAnnotation(item)
                                            }
                                        }
                                        true
                                    }

                                    SwipeToDismissBoxValue.StartToEnd,
                                    SwipeToDismissBoxValue.Settled -> false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val isEndToStart =
                                    dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
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
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = tint
                                    )
                                }
                            }
                        ) {
                            VideoAnnotationCard(
                                item = item,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                onOpenAnnotation = { onOpenAnnotation(item.navigationTarget) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoNotesSummary(
    totalBookmarks: Int,
    totalHighlights: Int,
    totalNotes: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VideoNotesStatChip(
            text = pluralStringResource(R.plurals.video_notes_bookmarks_count, totalBookmarks, totalBookmarks)
        )
        VideoNotesStatChip(
            text = pluralStringResource(R.plurals.video_notes_highlights_count, totalHighlights, totalHighlights)
        )
        VideoNotesStatChip(
            text = pluralStringResource(R.plurals.video_notes_notes_count, totalNotes, totalNotes)
        )
    }
}

@Composable
private fun VideoNotesStatChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VideoNotesFilterRow(
    selectedTypes: Set<VideoAnnotationType>,
    onToggleType: (VideoAnnotationType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnnotationFilterToggle(
            type = VideoAnnotationType.BOOKMARK,
            selected = VideoAnnotationType.BOOKMARK in selectedTypes,
            onClick = { onToggleType(VideoAnnotationType.BOOKMARK) }
        )
        AnnotationFilterToggle(
            type = VideoAnnotationType.HIGHLIGHT,
            selected = VideoAnnotationType.HIGHLIGHT in selectedTypes,
            onClick = { onToggleType(VideoAnnotationType.HIGHLIGHT) }
        )
        AnnotationFilterToggle(
            type = VideoAnnotationType.NOTE,
            selected = VideoAnnotationType.NOTE in selectedTypes,
            onClick = { onToggleType(VideoAnnotationType.NOTE) }
        )
    }
}

@Composable
private fun AnnotationFilterToggle(
    type: VideoAnnotationType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = annotationTypeLabel(type)
    IconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = modifier,
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(
            imageVector = annotationFilterIcon(type),
            contentDescription = label
        )
    }
}

@Composable
private fun VideoNotesEmptyState(
    hasAnyAnnotations: Boolean,
    modifier: Modifier = Modifier
) {
    val emptyMessage = stringResource(R.string.video_notes_empty_message)
    val emptySupporting = stringResource(R.string.video_notes_empty_supporting)
    val filteredMessage = stringResource(R.string.video_notes_empty_filtered_message)
    val filteredSupporting = stringResource(R.string.video_notes_empty_filtered_supporting)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasAnyAnnotations) filteredMessage else emptyMessage,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasAnyAnnotations) {
                    filteredSupporting
                } else {
                    emptySupporting
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun VideoAnnotationCard(
    item: VideoAnnotationItem,
    onOpenAnnotation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onOpenAnnotation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .background(annotationBarColor(item))
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = annotationTypeLabel(item.type),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatVideoAnnotationUpdatedAt(item.updatedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                item.note?.let { noteText ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = noteText,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.video_notes_progress, item.progressPercent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

private fun annotationDeletedMessage(
    item: VideoAnnotationItem,
    bookmarkMessage: String,
    highlightMessage: String,
    noteMessage: String
): String = when (item.type) {
    VideoAnnotationType.BOOKMARK -> bookmarkMessage
    VideoAnnotationType.HIGHLIGHT -> highlightMessage
    VideoAnnotationType.NOTE -> noteMessage
}

@Composable
private fun annotationTypeLabel(type: VideoAnnotationType): String = when (type) {
    VideoAnnotationType.BOOKMARK -> stringResource(R.string.video_notes_type_bookmark)
    VideoAnnotationType.HIGHLIGHT -> stringResource(R.string.video_notes_type_highlight)
    VideoAnnotationType.NOTE -> stringResource(R.string.video_notes_type_note)
}

private fun annotationFilterIcon(type: VideoAnnotationType) = when (type) {
    VideoAnnotationType.BOOKMARK -> Icons.Filled.Bookmark
    VideoAnnotationType.HIGHLIGHT -> Icons.Filled.FormatColorText
    VideoAnnotationType.NOTE -> Icons.AutoMirrored.Filled.StickyNote2
}

private fun annotationBarColor(item: VideoAnnotationItem): Color = when (item.type) {
    VideoAnnotationType.BOOKMARK -> Color(0xFFC4302B)
    VideoAnnotationType.NOTE,
    VideoAnnotationType.HIGHLIGHT -> when (item.color) {
        HighlightColor.RED -> Color(0xFFE57373)
        HighlightColor.BLUE -> Color(0xFF64B5F6)
        HighlightColor.GREEN -> Color(0xFF81C784)
        HighlightColor.YELLOW, null -> Color(0xFFFFF176)
    }
}
