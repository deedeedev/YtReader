package com.deedeedev.ytreader.ui.home

import android.content.Intent
import java.io.File
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.VideoThumbnailStore
import com.deedeedev.ytreader.data.local.SubtitleEntity

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryItemCard(
    item: LibraryItem,
    onSubtitleClick: (Long, Pair<Int, Int>) -> Unit,
    onVideoClick: (String, Pair<Int, Int>) -> Unit,
    onVideoSearchAgain: (String) -> Unit,
    onMarkAsRead: (() -> Unit)? = null,
    onAddToCollection: () -> Unit,
    onDownloadThumbnail: (() -> Unit)? = null,
    onResetProgress: () -> Unit,
    showLibraryStatusBadge: Boolean = true,
    showCollectionBadge: Boolean = true,
    onRemoveFromLibrary: (() -> Unit)? = null,
    onRestoreToLibrary: (() -> Unit)? = null,
    onRemoveFromCollection: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onUnarchive: (() -> Unit)? = null,
    onRemoveFromHistory: (() -> Unit)? = null,
    onSubtitleDelete: (SubtitleEntity) -> Unit,
    onSubtitleDownloadAgain: (SubtitleEntity) -> Unit,
    downloadingSubtitleIds: Set<Long>,
    isDownloadingThumbnail: Boolean = false,
    onExportEpub: ((String, String) -> Unit)? = null,
    compact: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val removedFromLibraryLabel = stringResource(R.string.library_removed)
    val readLabel = stringResource(R.string.library_read)
    val thumbnailFile = remember(item.thumbnailLocalPath) {
        VideoThumbnailStore.resolve(context, item.thumbnailLocalPath)
    }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onVideoClick(item.videoId, 0 to 0) },
                    onLongClick = { showMenu = true }
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            if (compact) {
                CompactCardContent(
                    item = item,
                    thumbnailFile = thumbnailFile,
                    onSubtitleClick = onSubtitleClick,
                    onSubtitleDelete = onSubtitleDelete,
                    onSubtitleDownloadAgain = onSubtitleDownloadAgain,
                    downloadingSubtitleIds = downloadingSubtitleIds,
                    showLibraryStatusBadge = showLibraryStatusBadge,
                    showCollectionBadge = showCollectionBadge,
                    removedFromLibraryLabel = removedFromLibraryLabel
                )
            } else {
                LargeCardContent(
                    item = item,
                    thumbnailFile = thumbnailFile,
                    onSubtitleClick = onSubtitleClick,
                    onSubtitleDelete = onSubtitleDelete,
                    onSubtitleDownloadAgain = onSubtitleDownloadAgain,
                    downloadingSubtitleIds = downloadingSubtitleIds,
                    showLibraryStatusBadge = showLibraryStatusBadge,
                    showCollectionBadge = showCollectionBadge,
                    removedFromLibraryLabel = removedFromLibraryLabel,
                    readLabel = readLabel
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_search_again)) },
                onClick = {
                    onVideoSearchAgain(item.videoUrl)
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_copy_video_url)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(item.videoUrl))
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_share_video_url)) },
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, item.videoUrl)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null
                    )
                }
            )
            onExportEpub?.let { exportFn ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.epub_export_video)) },
                    onClick = {
                        exportFn(item.videoId, item.title)
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.IosShare,
                            contentDescription = null
                        )
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.collection_add)) },
                onClick = {
                    onAddToCollection()
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null
                    )
                }
            )
            onArchive?.let { archive ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_archive)) },
                    onClick = {
                        archive()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Outbox,
                            contentDescription = null
                        )
                    }
                )
            }
            onUnarchive?.let { unarchive ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_unarchive)) },
                    onClick = {
                        unarchive()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null
                        )
                    }
                )
            }
            if (item.thumbnailLocalPath == null) {
                onDownloadThumbnail?.let { downloadThumbnail ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isDownloadingThumbnail) {
                                    stringResource(R.string.library_downloading_thumbnail)
                                } else {
                                    stringResource(R.string.library_download_thumbnail)
                                }
                            )
                        },
                        onClick = {
                            if (!isDownloadingThumbnail) {
                                downloadThumbnail()
                                showMenu = false
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
            if (!item.isRead) {
                onMarkAsRead?.let { markAsRead ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_mark_as_read)) },
                        onClick = {
                            markAsRead()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Done,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_reset_progress)) },
                onClick = {
                    onResetProgress()
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null
                    )
                }
            )
            onRestoreToLibrary?.let { restoreToLibrary ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_restore)) },
                    onClick = {
                        restoreToLibrary()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = null
                        )
                    }
                )
            }
            onRemoveFromLibrary?.let { removeFromLibrary ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_remove)) },
                    onClick = {
                        removeFromLibrary()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null
                        )
                    }
                )
            }
            onRemoveFromCollection?.let { removeFromCollection ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.collection_remove_from_this)) },
                    onClick = {
                        removeFromCollection()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null
                        )
                    }
                )
            }
            onRemoveFromHistory?.let { removeFromHistory ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_remove)) },
                    onClick = {
                        removeFromHistory()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LargeCardContent(
    item: LibraryItem,
    thumbnailFile: File?,
    onSubtitleClick: (Long, Pair<Int, Int>) -> Unit,
    onSubtitleDelete: (SubtitleEntity) -> Unit,
    onSubtitleDownloadAgain: (SubtitleEntity) -> Unit,
    downloadingSubtitleIds: Set<Long>,
    showLibraryStatusBadge: Boolean,
    showCollectionBadge: Boolean,
    removedFromLibraryLabel: String,
    readLabel: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ThumbnailPreview(
            thumbnailFile = thumbnailFile,
            title = item.title,
            cornerRadius = null,
            bottomStartEndCornersZero = true,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (item.channelName.isNotBlank()) {
                Text(
                    text = item.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if ((showLibraryStatusBadge && !item.isInLibrary) || (showCollectionBadge && item.isInCollections)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    if (showLibraryStatusBadge && !item.isInLibrary) {
                        MetadataBadge(text = removedFromLibraryLabel)
                    }
                    if (showCollectionBadge && item.isInCollections) {
                        MetadataBadge(
                            text = if (item.collectionCount == 1) {
                                stringResource(R.string.library_in_collection_one)
                            } else {
                                stringResource(R.string.library_in_collection_many, item.collectionCount)
                            }
                        )
                    }
                }
            }

            if (item.isRead) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReadingStatusBadge(text = readLabel)
                }
            } else {
                LibraryReadingProgress(
                    percent = item.readingProgressPercent,
                    currentPage = item.currentPage,
                    totalPages = item.totalPages,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item.subtitles.forEach { subtitle ->
                    SubtitleChip(
                        subtitle = subtitle,
                        onClick = { onSubtitleClick(subtitle.id, 0 to 0) },
                        onDelete = { onSubtitleDelete(subtitle) },
                        onDownloadAgain = { onSubtitleDownloadAgain(subtitle) },
                        isDownloading = downloadingSubtitleIds.contains(subtitle.id)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun CompactCardContent(
    item: LibraryItem,
    thumbnailFile: File?,
    onSubtitleClick: (Long, Pair<Int, Int>) -> Unit,
    onSubtitleDelete: (SubtitleEntity) -> Unit,
    onSubtitleDownloadAgain: (SubtitleEntity) -> Unit,
    downloadingSubtitleIds: Set<Long>,
    showLibraryStatusBadge: Boolean,
    showCollectionBadge: Boolean,
    removedFromLibraryLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThumbnailPreview(
            thumbnailFile = thumbnailFile,
            title = item.title,
            cornerRadius = 0f,
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight()
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (item.channelName.isNotBlank()) {
                Text(
                    text = item.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showLibraryStatusBadge && !item.isInLibrary) {
                    MetadataBadge(text = removedFromLibraryLabel)
                }
                if (showCollectionBadge && item.isInCollections) {
                    MetadataBadge(
                        text = if (item.collectionCount == 1) {
                            stringResource(R.string.library_in_collection_one)
                        } else {
                            stringResource(R.string.library_in_collection_many, item.collectionCount)
                        }
                    )
                }
                if (item.subtitles.isNotEmpty()) {
                    item.subtitles.take(4).forEach { subtitle ->
                        CompactSubtitleChip(
                            subtitle = subtitle,
                            onClick = { onSubtitleClick(subtitle.id, 0 to 0) },
                            isDownloading = downloadingSubtitleIds.contains(subtitle.id)
                        )
                    }
                    if (item.subtitles.size > 4) {
                        Text(
                            text = "+${item.subtitles.size - 4}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            if (!item.isRead && item.readingProgressPercent > 0) {
                Text(
                    text = stringResource(R.string.library_reading_progress, item.readingProgressPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            LinearProgressIndicator(
                progress = { item.readingProgressPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                drawStopIndicator = {}
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSubtitleChip(
    subtitle: SubtitleEntity,
    onClick: () -> Unit,
    isDownloading: Boolean
) {
    val autoLabel = stringResource(R.string.library_subtitle_auto)
    val languageLabel = buildString {
        append(subtitle.languageCode.uppercase())
        if (subtitle.isAutoGenerated) {
            append(autoLabel)
        }
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .height(20.dp)
            .combinedClickable(
                onClick = { if (!isDownloading) onClick() },
                onClickLabel = null
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.dp
                )
            } else {
                Text(
                    text = languageLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun ThumbnailPreview(
    thumbnailFile: File?,
    title: String,
    cornerRadius: Float? = null,
    bottomStartEndCornersZero: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = when {
        bottomStartEndCornersZero -> RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
        cornerRadius != null -> RoundedCornerShape(cornerRadius)
        else -> MaterialTheme.shapes.medium
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape
    ) {
        if (thumbnailFile != null) {
            AsyncImage(
                model = thumbnailFile,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun MetadataBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ReadingStatusBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun ReadingProgressText(
    percent: Int,
    currentPage: Int,
    totalPages: Int
) {
    Text(
        text = if (currentPage > 0 && totalPages > 0) {
            stringResource(R.string.reader_page_progress, percent, currentPage, totalPages)
        } else {
            stringResource(R.string.library_reading_progress, percent)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
internal fun LibraryReadingProgress(
    percent: Int,
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ReadingProgressText(
            percent = percent,
            currentPage = currentPage,
            totalPages = totalPages
        )
        LinearProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            drawStopIndicator = {}
        )
    }
}
