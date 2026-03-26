package com.deedeedev.ytreader.ui.home

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.R

fun collectionEmptyText(
    resources: Resources,
    totalCollectionVideoCount: Int,
    selectedChannelFilter: String?
): String {
    return when {
        totalCollectionVideoCount == 0 -> resources.getString(R.string.library_empty_collection)
        selectedChannelFilter != null -> resources.getString(R.string.library_empty_collection_channel)
        else -> resources.getString(R.string.library_empty_collection_filtered)
    }
}

@Composable
fun <T> LibraryListSection(
    items: List<T>,
    emptyText: String,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (items.isEmpty()) {
            item {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(
                items = items,
                key = key
            ) { item ->
                itemContent(item)
            }
        }
    }
}
