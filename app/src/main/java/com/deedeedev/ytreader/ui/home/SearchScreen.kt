package com.deedeedev.ytreader.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.schabi.newpipe.extractor.stream.SubtitlesStream

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.local.SearchHistoryEntity
import android.net.Uri
import android.text.format.DateUtils

private fun isYouTubeUrl(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false

    val uri = Uri.parse(trimmed)
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
    val pathSegments = uri.pathSegments ?: emptyList()

    return when (host) {
        "youtube.com", "m.youtube.com" -> {
            when (pathSegments.firstOrNull()) {
                "watch" -> {
                    val videoId = uri.getQueryParameter("v")
                    val playlistId = uri.getQueryParameter("list")
                    !videoId.isNullOrBlank() && playlistId.isNullOrBlank()
                }

                "shorts" -> pathSegments.getOrNull(1)?.isNotBlank() == true
                else -> false
            }
        }

        "youtu.be" -> {
            val videoId = pathSegments.firstOrNull()
            !videoId.isNullOrBlank() && uri.getQueryParameter("list").isNullOrBlank()
        }

        else -> false
    }
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onSubtitleClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val clipboardText = clipboardManager.getText()?.text ?: ""
    val hasYouTubeUrlInClipboard = isYouTubeUrl(clipboardText)
    val youtubeUrlLabel = stringResource(R.string.search_youtube_url)
    val clearSearchLabel = stringResource(R.string.clear_search)
    val pasteYoutubeLinkLabel = stringResource(R.string.paste_youtube_link)
    val searchLabel = stringResource(R.string.search)
    val availableSubtitlesLabel = stringResource(R.string.search_available_subtitles)

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
                placeholder = { Text(youtubeUrlLabel) },
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
                                contentDescription = clearSearchLabel
                            )
                        }
                    } else if (hasYouTubeUrlInClipboard) {
                        IconButton(onClick = { viewModel.onUrlChange(clipboardText.trim()) }) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = pasteYoutubeLinkLabel
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
                    contentDescription = searchLabel
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

        if (uiState.streamInfo == null && uiState.error == null && !uiState.isLoading) {
            val showHistoryLabel = stringResource(
                if (uiState.showHistory) R.string.search_hide_history else R.string.search_show_history
            )
            val historyEmptyLabel = stringResource(R.string.search_history_empty)
            val deleteLabel = stringResource(R.string.delete)

            TextButton(
                onClick = { viewModel.toggleHistory() },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
            ) {
                Text(text = showHistoryLabel)
            }

            if (uiState.showHistory) {
                if (uiState.searchHistory.isEmpty()) {
                    Text(
                        text = historyEmptyLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(uiState.searchHistory, key = { it.id }) { entry ->
                            HistoryItem(
                                entry = entry,
                                onClick = { viewModel.searchFromHistory(entry.url) },
                                onDelete = { viewModel.deleteHistoryEntry(entry.id) }
                            )
                        }
                    }
                }
            }
        }

        // Available Subtitles
        uiState.streamInfo?.let { info ->
            Text(
                text = info.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Text(
                text = availableSubtitlesLabel,
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
                        SubtitleIdentityMatcher.matchesSavedSubtitle(it, info.url, subtitle)
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
                        SubtitleIdentityMatcher.matchesSavedSubtitle(it, info.url, subtitle)
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
    val unknownLabel = stringResource(R.string.library_unknown)
    val favoriteLabel = stringResource(R.string.favorite)
    val autoGeneratedLabel = stringResource(R.string.search_auto_generated)
    val readSubtitleLabel = stringResource(R.string.library_subtitle_read)
    val downloadSubtitleLabel = stringResource(R.string.library_subtitle_download)
    val addFavoriteLabel = stringResource(R.string.language_favorite_add)
    val removeFavoriteLabel = stringResource(R.string.language_favorite_remove)

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
                val languageCode = subtitle.languageTag ?: unknownLabel
                val displayName = subtitle.displayLanguageName
                val text = if (!displayName.isNullOrBlank() && displayName != languageCode) {
                    stringResource(R.string.search_language_display_format, languageCode, displayName)
                } else {
                    languageCode
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = text)
                    if (isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = favoriteLabel,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (subtitle.isAutoGenerated) {
                    Text(text = autoGeneratedLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isDownloaded) {
                IconButton(onClick = onView) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = readSubtitleLabel
                    )
                }
            } else {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = downloadSubtitleLabel
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) removeFavoriteLabel else addFavoriteLabel) },
                onClick = {
                    onToggleFavorite()
                    showMenu = false
                }
            )
        }
    }
}

@Composable
private fun HistoryItem(
    entry: SearchHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val deleteLabel = stringResource(R.string.delete)
    val relativeTime = remember(entry.searchedAt) {
        DateUtils.getRelativeTimeSpanString(
            entry.searchedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = entry.videoTitle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = deleteLabel,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
