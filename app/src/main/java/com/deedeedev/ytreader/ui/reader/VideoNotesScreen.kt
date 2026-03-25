package com.deedeedev.ytreader.ui.reader

import android.content.Intent
import android.text.format.DateFormat
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.SubtitleDao

private enum class VideoNotesFilter {
    ALL,
    WITH_NOTES
}

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(VideoNotesFilter.ALL) }

    val filteredItems = remember(uiState.items, selectedFilter) {
        when (selectedFilter) {
            VideoNotesFilter.ALL -> uiState.items
            VideoNotesFilter.WITH_NOTES -> uiState.items.filter { item -> item.type == VideoAnnotationType.NOTE }
        }
    }

    val markdownExport = remember(filteredItems, uiState.title, videoId, selectedFilter) {
        buildVideoNotesMarkdown(
            title = uiState.title,
            videoId = videoId,
            filter = selectedFilter,
            items = filteredItems
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.title.isNotBlank()) uiState.title else "Highlights & notes",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Most recent",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/markdown"
                                putExtra(Intent.EXTRA_TEXT, markdownExport)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export annotations"))
                        },
                        enabled = filteredItems.isNotEmpty()
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export annotations")
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
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading annotations...")
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
        VideoNotesStatChip(text = "$totalBookmarks bookmarks")
        VideoNotesStatChip(text = "$totalHighlights highlights")
        VideoNotesStatChip(text = "$totalNotes notes")
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
    selectedFilter: VideoNotesFilter,
    onFilterSelected: (VideoNotesFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == VideoNotesFilter.ALL,
            onClick = { onFilterSelected(VideoNotesFilter.ALL) },
            label = { Text("All") }
        )
        FilterChip(
            selected = selectedFilter == VideoNotesFilter.WITH_NOTES,
            onClick = { onFilterSelected(VideoNotesFilter.WITH_NOTES) },
            label = { Text("With notes") }
        )
    }
}

@Composable
private fun VideoNotesEmptyState(
    hasAnyAnnotations: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasAnyAnnotations) "No annotations match this filter." else "No annotations yet.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasAnyAnnotations) {
                    "Switch to All to review every bookmark, highlight, and note."
                } else {
                    "Create bookmarks, highlights, or notes in the reader to review them here."
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.progressPercent}% through track",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    TextButton(onClick = onOpenAnnotation) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

private fun buildVideoNotesMarkdown(
    title: String,
    videoId: String,
    filter: VideoNotesFilter,
    items: List<VideoAnnotationItem>
): String {
    val exportTitle = title.ifBlank { videoId }
    return buildString {
        append("# ")
        append(exportTitle)
        append("\n\n")
        append("- Video ID: ")
        append(videoId)
        append("\n")
        append("- Filter: ")
        append(
            when (filter) {
                VideoNotesFilter.ALL -> "All"
                VideoNotesFilter.WITH_NOTES -> "With notes"
            }
        )
        append("\n")
        append("- Exported items: ")
        append(items.size)
        append("\n\n")

        items.forEachIndexed { index, item ->
            append("## ")
            append(index + 1)
            append(". ")
            append(annotationTypeLabel(item.type))
            append("\n\n")
            append("- Title: ")
            append(item.title)
            append("\n")
            append("- Updated: ")
            append(formatVideoAnnotationUpdatedAt(item.updatedAt))
            append("\n")
            append("- Position: ")
            append(item.progressPercent)
            append("%\n")
            item.note?.let { note ->
                append("- Note: ")
                append(note.replace("\n", " "))
                append("\n")
            }
            append("\n")
        }
    }
}

private fun formatVideoAnnotationUpdatedAt(updatedAt: Long): String {
    return if (updatedAt > 0L) {
        DateFormat.format("yyyy-MM-dd HH:mm", updatedAt).toString()
    } else {
        ""
    }
}

private fun annotationTypeLabel(type: VideoAnnotationType): String = when (type) {
    VideoAnnotationType.BOOKMARK -> "Bookmark"
    VideoAnnotationType.NOTE -> "Note"
    VideoAnnotationType.HIGHLIGHT -> "Highlight"
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
