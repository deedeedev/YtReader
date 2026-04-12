package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.R

@Composable
internal fun HighlightNoteDialog(
    noteText: String,
    hasExistingNote: Boolean,
    onNoteTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val title = stringResource(R.string.reader_highlight_note)
    val noteLabel = stringResource(R.string.reader_note_label)
    val saveLabel = stringResource(R.string.save)
    val removeNoteLabel = stringResource(R.string.reader_remove_note)
    val cancelLabel = stringResource(R.string.cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = noteText,
                onValueChange = onNoteTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                label = { Text(noteLabel) },
                maxLines = 8
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(saveLabel)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasExistingNote) {
                    TextButton(onClick = onDelete) {
                        Text(removeNoteLabel)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(cancelLabel)
                }
            }
        }
    )
}

@Composable
internal fun BookmarkTitleDialog(
    isEditing: Boolean,
    titleText: String,
    onTitleTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val title = stringResource(
        if (isEditing) R.string.reader_edit_bookmark else R.string.reader_add_bookmark
    )
    val bookmarkTitleLabel = stringResource(R.string.reader_bookmark_title_label)
    val bookmarkSupportingText = stringResource(R.string.reader_bookmark_title_supporting)
    val saveLabel = stringResource(R.string.save)
    val deleteLabel = stringResource(R.string.delete)
    val cancelLabel = stringResource(R.string.cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = titleText,
                onValueChange = onTitleTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                label = { Text(bookmarkTitleLabel) },
                supportingText = {
                    Text(bookmarkSupportingText)
                },
                maxLines = 4
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(saveLabel)
            }
        },
        dismissButton = {
            if (isEditing) {
                TextButton(onClick = onDelete) {
                    Text(deleteLabel)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(cancelLabel)
                }
            }
        }
    )
}
