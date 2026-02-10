package com.deedeedev.ytreader.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.extractor.stream.SubtitlesStream

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun SearchScreen(
    viewModel: HomeViewModel,
    onSubtitleClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search Area
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.url,
                onValueChange = viewModel::onUrlChange,
                placeholder = { Text("YouTube URL") },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        viewModel.searchVideo()
                    }
                ),
                trailingIcon = {
                    if (uiState.url.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onUrlChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.searchVideo()
                },
                enabled = !uiState.isLoading,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(4.dp)
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxSize())
            }
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Available Subtitles
        uiState.streamInfo?.let { info ->
            Text(
                text = info.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Text(
                text = "Available Subtitles",
                style = MaterialTheme.typography.labelLarge
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                val allSubtitles: List<SubtitlesStream> = try {
                        (info.subtitles ?: emptyList()).sortedBy { it.languageTag }
                } catch (e: Exception) {
                    emptyList()
                }

                val favoriteSubtitles = allSubtitles.filter { it.languageTag in uiState.favoriteLanguages }
                val otherSubtitles = allSubtitles.filter { it.languageTag !in uiState.favoriteLanguages }
                
                items(favoriteSubtitles) { subtitle ->
                    val savedSubtitle = uiState.savedSubtitles.find { 
                        it.videoId == info.url && it.languageCode == subtitle.languageTag 
                    }
                    
                    SubtitleItem(
                        subtitle = subtitle,
                        isDownloaded = savedSubtitle != null,
                        isFavorite = true,
                        onDownload = { viewModel.downloadSubtitle(subtitle) },
                        onView = { savedSubtitle?.let { onSubtitleClick(it.id) } },
                        onToggleFavorite = { viewModel.toggleFavoriteLanguage(subtitle.languageTag) }
                    )
                }

                if (favoriteSubtitles.isNotEmpty() && otherSubtitles.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                items(otherSubtitles) { subtitle ->
                    val savedSubtitle = uiState.savedSubtitles.find { 
                        it.videoId == info.url && it.languageCode == subtitle.languageTag 
                    }
                    
                    SubtitleItem(
                        subtitle = subtitle,
                        isDownloaded = savedSubtitle != null,
                        isFavorite = false,
                        onDownload = { viewModel.downloadSubtitle(subtitle) },
                        onView = { savedSubtitle?.let { onSubtitleClick(it.id) } },
                        onToggleFavorite = { viewModel.toggleFavoriteLanguage(subtitle.languageTag) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubtitleItem(
    subtitle: SubtitlesStream,
    isDownloaded: Boolean,
    isFavorite: Boolean,
    onDownload: () -> Unit,
    onView: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .combinedClickable(
                    onClick = {
                        if (isDownloaded) {
                            onView()
                        } else {
                            onDownload()
                        }
                    },
                    onLongClick = { showMenu = true }
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val languageCode = subtitle.languageTag ?: "Unknown"
                val displayName = subtitle.displayLanguageName
                val text = if (!displayName.isNullOrBlank() && displayName != languageCode) {
                    "$languageCode - $displayName"
                } else {
                    languageCode
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = text)
                    if (isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (subtitle.isAutoGenerated) {
                    Text(text = "Auto-generated", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isDownloaded) {
                IconButton(onClick = onView) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "Read Subtitle"
                    )
                }
            } else {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Subtitle"
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Remove language from favorites" else "Add language to favorites") },
                onClick = {
                    onToggleFavorite()
                    showMenu = false
                }
            )
        }
    }
}
