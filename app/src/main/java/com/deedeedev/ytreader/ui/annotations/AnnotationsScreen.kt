package com.deedeedev.ytreader.ui.annotations

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.ui.reader.HighlightColor
import com.deedeedev.ytreader.ui.reader.formatVideoAnnotationUpdatedAt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AnnotationsScreen(
    viewModel: AnnotationsViewModel,
    onAnnotationClick: (com.deedeedev.ytreader.ui.reader.ReaderAnnotationTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredItems by viewModel.filteredItems.collectAsStateWithLifecycle()
    val groupedItems by viewModel.groupedItems.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }

    val deletedLabel = stringResource(R.string.annotations_deleted)
    val undoLabel = stringResource(R.string.annotations_undo)

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AnnotationsTopBar(
                isSearchExpanded = uiState.isSearchExpanded,
                searchQuery = uiState.searchQuery,
                onToggleSearch = viewModel::toggleSearch,
                onSearchQueryChange = viewModel::setSearchQuery,
                groupByVideo = uiState.groupByVideo,
                onToggleGroupByVideo = viewModel::toggleGroupByVideo,
                sortOption = uiState.sortOption,
                onSortOptionChange = viewModel::setSortOption,
                showSortMenu = showSortMenu,
                onShowSortMenuChange = { showSortMenu = it }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnnotationsSummaryRow(
                counts = counts,
                selectedTypes = uiState.typeFilter,
                onTypeToggle = viewModel::toggleTypeFilter
            )

            AnnotationsFilterRow(
                selectedTypes = uiState.typeFilter,
                onTypeToggle = viewModel::toggleTypeFilter
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.video_notes_loading))
                }
            } else if (groupedItems.isEmpty() && filteredItems.isEmpty()) {
                AnnotationsEmptyState(
                    hasData = counts.total > 0,
                    searchQuery = uiState.searchQuery
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.groupByVideo) {
                        groupedItems.forEach { group ->
                            stickyHeader(key = "header_${group.videoId}") {
                                VideoGroupHeader(
                                    videoTitle = group.videoTitle,
                                    channelName = group.channelName,
                                    annotationCount = group.items.size
                                )
                            }
                            items(group.items, key = { it.key }) { item ->
                                AnnotationCard(
                                    item = item,
                                    showVideoInfo = false,
                                    onClick = { onAnnotationClick(item.navigationTarget) },
                                    onDelete = {
                                        viewModel.deleteAnnotation(item)
                                        coroutineScope.launch {
                                            val autoDismissJob = launch {
                                                delay(5_000)
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                            }
                                            val result = snackbarHostState.showSnackbar(
                                                message = deletedLabel,
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Indefinite
                                            )
                                            autoDismissJob.cancel()
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restoreAnnotation(item)
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredItems, key = { it.key }) { item ->
                            AnnotationCard(
                                item = item,
                                showVideoInfo = true,
                                onClick = { onAnnotationClick(item.navigationTarget) },
                                onDelete = {
                                    viewModel.deleteAnnotation(item)
                                    coroutineScope.launch {
                                        val autoDismissJob = launch {
                                            delay(5_000)
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                        }
                                        val result = snackbarHostState.showSnackbar(
                                            message = deletedLabel,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Indefinite
                                        )
                                        autoDismissJob.cancel()
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreAnnotation(item)
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AnnotationsTopBar(
    isSearchExpanded: Boolean,
    searchQuery: String,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    groupByVideo: Boolean,
    onToggleGroupByVideo: () -> Unit,
    sortOption: AnnotationSortOption,
    onSortOptionChange: (AnnotationSortOption) -> Unit,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit
) {
    TopAppBar(
        title = {
            if (isSearchExpanded) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.annotations_search_hint)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(stringResource(R.string.screen_annotations))
            }
        },
        navigationIcon = {
            if (isSearchExpanded) {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        actions = {
            if (isSearchExpanded) {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            } else {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                }
                IconButton(onClick = onToggleGroupByVideo) {
                    Icon(
                        imageVector = if (groupByVideo) Icons.Filled.ViewAgenda else Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = if (groupByVideo) {
                            stringResource(R.string.annotations_flat_view)
                        } else {
                            stringResource(R.string.annotations_group_by_video)
                        }
                    )
                }
                Box {
                    IconButton(onClick = { onShowSortMenuChange(true) }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.library_sort))
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { onShowSortMenuChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.annotations_sort_newest)) },
                            onClick = {
                                onSortOptionChange(AnnotationSortOption.NEWEST)
                                onShowSortMenuChange(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.annotations_sort_oldest)) },
                            onClick = {
                                onSortOptionChange(AnnotationSortOption.OLDEST)
                                onShowSortMenuChange(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.annotations_sort_video)) },
                            onClick = {
                                onSortOptionChange(AnnotationSortOption.VIDEO_TITLE)
                                onShowSortMenuChange(false)
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun AnnotationsSummaryRow(
    counts: AnnotationCounts,
    selectedTypes: Set<AnnotationType>,
    onTypeToggle: (AnnotationType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = AnnotationType.BOOKMARK in selectedTypes,
            onClick = { onTypeToggle(AnnotationType.BOOKMARK) },
            label = { Text(pluralStringResource(R.plurals.annotations_bookmarks_count, counts.bookmarks, counts.bookmarks)) }
        )
        FilterChip(
            selected = AnnotationType.HIGHLIGHT in selectedTypes,
            onClick = { onTypeToggle(AnnotationType.HIGHLIGHT) },
            label = { Text(pluralStringResource(R.plurals.annotations_highlights_count, counts.highlights, counts.highlights)) }
        )
        FilterChip(
            selected = AnnotationType.NOTE in selectedTypes,
            onClick = { onTypeToggle(AnnotationType.NOTE) },
            label = { Text(pluralStringResource(R.plurals.annotations_notes_count, counts.notes, counts.notes)) }
        )
    }
}

@Composable
private fun AnnotationsFilterRow(
    selectedTypes: Set<AnnotationType>,
    onTypeToggle: (AnnotationType) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnnotationFilterToggle(
            type = AnnotationType.BOOKMARK,
            selected = AnnotationType.BOOKMARK in selectedTypes,
            onClick = { onTypeToggle(AnnotationType.BOOKMARK) }
        )
        AnnotationFilterToggle(
            type = AnnotationType.HIGHLIGHT,
            selected = AnnotationType.HIGHLIGHT in selectedTypes,
            onClick = { onTypeToggle(AnnotationType.HIGHLIGHT) }
        )
        AnnotationFilterToggle(
            type = AnnotationType.NOTE,
            selected = AnnotationType.NOTE in selectedTypes,
            onClick = { onTypeToggle(AnnotationType.NOTE) }
        )
    }
}

@Composable
private fun AnnotationFilterToggle(
    type: AnnotationType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val label = annotationTypeLabel(type)
    IconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
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
private fun VideoGroupHeader(
    videoTitle: String,
    channelName: String,
    annotationCount: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = videoTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channelName.isNotBlank()) {
                    Text(
                        text = channelName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = annotationCount.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AnnotationCard(
    item: AnnotationItem,
    showVideoInfo: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
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
        },
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            onClick = onClick
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
                            text = formatVideoAnnotationUpdatedAt(item.createdAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    item.noteText?.let { noteText ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = noteText,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (showVideoInfo) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.videoTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.channelName.isNotBlank()) {
                            Text(
                                text = item.channelName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
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
}

@Composable
private fun AnnotationsEmptyState(
    hasData: Boolean,
    searchQuery: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasData) {
                    stringResource(R.string.annotations_no_results)
                } else {
                    stringResource(R.string.annotations_empty)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasData) {
                    stringResource(R.string.annotations_no_results_description)
                } else {
                    stringResource(R.string.annotations_empty_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun annotationTypeLabel(type: AnnotationType): String = when (type) {
    AnnotationType.BOOKMARK -> stringResource(R.string.video_notes_type_bookmark)
    AnnotationType.HIGHLIGHT -> stringResource(R.string.video_notes_type_highlight)
    AnnotationType.NOTE -> stringResource(R.string.video_notes_type_note)
}

private fun annotationFilterIcon(type: AnnotationType) = when (type) {
    AnnotationType.BOOKMARK -> Icons.Filled.Bookmark
    AnnotationType.HIGHLIGHT -> Icons.Filled.FormatColorText
    AnnotationType.NOTE -> Icons.AutoMirrored.Filled.StickyNote2
}

private fun annotationBarColor(item: AnnotationItem): Color = when (item.type) {
    AnnotationType.BOOKMARK -> Color(0xFFC4302B)
    AnnotationType.NOTE,
    AnnotationType.HIGHLIGHT -> when (item.color) {
        HighlightColor.RED -> Color(0xFFE57373)
        HighlightColor.BLUE -> Color(0xFF64B5F6)
        HighlightColor.GREEN -> Color(0xFF81C784)
        HighlightColor.YELLOW, null -> Color(0xFFFFF176)
    }
}
