package com.deedeedev.ytreader.ui.home

import android.content.res.Resources
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.R

fun sortLabel(resources: Resources, sortOption: SortOption): String = when (sortOption) {
    SortOption.TITLE -> resources.getString(R.string.library_sort_title)
    SortOption.CHANNEL_NAME -> resources.getString(R.string.library_sort_channel_name)
    SortOption.DATE_PUBLISHED -> resources.getString(R.string.library_sort_date_published)
    SortOption.DOWNLOADED -> resources.getString(R.string.library_sort_downloaded)
    SortOption.LAST_OPENED -> resources.getString(R.string.library_sort_last_opened)
}

fun visibilityLabel(resources: Resources, filter: LibraryVisibilityFilter): String = when (filter) {
    LibraryVisibilityFilter.ALL -> resources.getString(R.string.library_visibility_all)
    LibraryVisibilityFilter.NOT_IN_COLLECTIONS -> resources.getString(R.string.library_visibility_not_in_collections)
    LibraryVisibilityFilter.IN_COLLECTIONS -> resources.getString(R.string.library_visibility_in_collections)
}

val libraryVisibilityFilters: List<LibraryVisibilityFilter> = listOf(
    LibraryVisibilityFilter.ALL,
    LibraryVisibilityFilter.NOT_IN_COLLECTIONS,
    LibraryVisibilityFilter.IN_COLLECTIONS
)

val sortOptions: List<SortOption> = listOf(
    SortOption.TITLE,
    SortOption.CHANNEL_NAME,
    SortOption.DATE_PUBLISHED,
    SortOption.DOWNLOADED,
    SortOption.LAST_OPENED
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryListControls(
    channels: List<String>,
    selectedChannelFilter: String?,
    visibilityFilter: LibraryVisibilityFilter? = null,
    sortOption: SortOption,
    isAscending: Boolean,
    onChannelFilterChange: (String?) -> Unit,
    onVisibilityFilterChange: ((LibraryVisibilityFilter) -> Unit)? = null,
    onSortOptionChange: (SortOption) -> Unit,
    onSortDirectionToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = context.resources
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var expandedFilter by remember { mutableStateOf(false) }

            Box(modifier = Modifier.weight(1f)) {
                ExposedDropdownMenuBox(
                    expanded = expandedFilter,
                    onExpandedChange = { expandedFilter = !expandedFilter }
                ) {
                    OutlinedTextField(
                        value = selectedChannelFilter ?: context.getString(R.string.library_all_channels),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFilter)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                        label = { Text(context.getString(R.string.library_filter_by_channel)) }
                    )
                    ExposedDropdownMenu(
                        expanded = expandedFilter,
                        onDismissRequest = { expandedFilter = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.library_all_channels)) },
                            onClick = {
                                onChannelFilterChange(null)
                                expandedFilter = false
                            }
                        )
                        channels.forEach { channel ->
                            DropdownMenuItem(
                                text = { Text(channel) },
                                onClick = {
                                    onChannelFilterChange(channel)
                                    expandedFilter = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            var expandedSort by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { expandedSort = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = context.getString(R.string.library_sort)
                    )
                }
                DropdownMenu(
                    expanded = expandedSort,
                    onDismissRequest = { expandedSort = false }
                ) {
                    sortOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(resources, option)) },
                            onClick = {
                                onSortOptionChange(option)
                                expandedSort = false
                            },
                            trailingIcon = {
                                if (sortOption == option) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = context.getString(R.string.selected)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onSortDirectionToggle) {
                Icon(
                    imageVector = if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (isAscending) {
                        context.getString(R.string.library_sort_ascending)
                    } else {
                        context.getString(R.string.library_sort_descending)
                    }
                )
            }
        }

        if (visibilityFilter != null && onVisibilityFilterChange != null) {
            var expandedVisibility by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedVisibility,
                onExpandedChange = { expandedVisibility = !expandedVisibility }
            ) {
                OutlinedTextField(
                    value = visibilityLabel(resources, visibilityFilter),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVisibility)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                    label = { Text(context.getString(R.string.library_visibility)) }
                )
                ExposedDropdownMenu(
                    expanded = expandedVisibility,
                    onDismissRequest = { expandedVisibility = false }
                ) {
                    libraryVisibilityFilters.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(visibilityLabel(resources, filter)) },
                            onClick = {
                                onVisibilityFilterChange(filter)
                                expandedVisibility = false
                            },
                            trailingIcon = {
                                if (filter == visibilityFilter) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = context.getString(R.string.selected)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
