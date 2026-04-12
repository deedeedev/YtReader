package com.deedeedev.ytreader.ui.reader

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.deedeedev.ytreader.R

@Composable
internal fun UnsavedChangesDialog(
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    val title = stringResource(R.string.reader_unsaved_changes)
    val message = stringResource(R.string.reader_unsaved_changes_message)
    val discard = stringResource(R.string.reader_discard)
    val cancel = stringResource(R.string.cancel)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(discard)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(cancel)
            }
        }
    )
}

@Composable
internal fun EmptyTextDialog(onOk: () -> Unit) {
    val title = stringResource(R.string.reader_empty_text)
    val message = stringResource(R.string.reader_subtitle_text_empty)
    val okLabel = stringResource(R.string.reader_ok)
    AlertDialog(
        onDismissRequest = onOk,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onOk) {
                Text(okLabel)
            }
        }
    )
}
