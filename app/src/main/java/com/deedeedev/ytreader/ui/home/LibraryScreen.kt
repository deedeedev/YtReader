package com.deedeedev.ytreader.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.data.local.SubtitleEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    viewModel: HomeViewModel,
    onSubtitleClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Group subtitles by videoId
    val libraryItems = remember(uiState.savedSubtitles) {
        uiState.savedSubtitles.groupBy { it.videoId }
            .map { (_, subtitles) ->
                // Assume all subtitles for the same video share the same title and channel name
                // Use the most recent one for metadata just in case
                val first = subtitles.first()
                LibraryItem(
                    videoId = first.videoId,
                    title = first.title,
                    channelName = first.channelName,
                    subtitles = subtitles.sortedBy { it.languageCode }
                )
            }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (libraryItems.isEmpty()) {
            item {
                Text(
                    text = "No saved subtitles yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(libraryItems) { item ->
                LibraryItemCard(item = item, onSubtitleClick = onSubtitleClick)
            }
        }
    }
}

data class LibraryItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val subtitles: List<SubtitleEntity>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryItemCard(
    item: LibraryItem,
    onSubtitleClick: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    SuggestionChip(
                        onClick = { onSubtitleClick(subtitle.id) },
                        label = { Text(subtitle.languageCode.uppercase()) }
                    )
                }
            }
        }
    }
}
