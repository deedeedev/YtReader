package com.deedeedev.ytreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.EpubExportMode
import com.deedeedev.ytreader.data.exportEpub
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.VideoDao
import com.deedeedev.ytreader.data.shareEpub
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun EpubExportDialog(
    bookTitle: String,
    videoIds: List<String>,
    subtitleDao: SubtitleDao,
    videoDao: VideoDao,
    highlightNoteDao: HighlightNoteDao,
    bookmarkDao: BookmarkDao,
    onDismiss: () -> Unit
) {
    var isExporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = { Text(stringResource(R.string.epub_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.epub_export_mode_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.epub_export_clean),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.epub_export_clean_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isExporting = true
                                error = null
                                try {
                                    val file = exportEpub(
                                        context = context,
                                        subtitleDao = subtitleDao,
                                        videoDao = videoDao,
                                        highlightNoteDao = highlightNoteDao,
                                        bookmarkDao = bookmarkDao,
                                        videoIds = videoIds,
                                        mode = EpubExportMode.CLEAN,
                                        bookTitle = bookTitle
                                    )
                                    shareEpub(context, file)
                                    onDismiss()
                                } catch (e: Exception) {
                                    error = e.message ?: context.getString(R.string.epub_export_error)
                                    isExporting = false
                                }
                            }
                        },
                        enabled = !isExporting
                    ) {
                        Text(stringResource(R.string.epub_export_clean))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.epub_export_annotated),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.epub_export_annotated_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isExporting = true
                                error = null
                                try {
                                    val file = exportEpub(
                                        context = context,
                                        subtitleDao = subtitleDao,
                                        videoDao = videoDao,
                                        highlightNoteDao = highlightNoteDao,
                                        bookmarkDao = bookmarkDao,
                                        videoIds = videoIds,
                                        mode = EpubExportMode.ANNOTATED,
                                        bookTitle = bookTitle
                                    )
                                    shareEpub(context, file)
                                    onDismiss()
                                } catch (e: Exception) {
                                    error = e.message ?: context.getString(R.string.epub_export_error)
                                    isExporting = false
                                }
                            }
                        },
                        enabled = !isExporting
                    ) {
                        Text(stringResource(R.string.epub_export_annotated))
                    }
                }
                if (isExporting) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.epub_exporting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                error?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
