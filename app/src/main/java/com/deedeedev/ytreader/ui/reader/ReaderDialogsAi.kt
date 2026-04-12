package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.R

@Composable
internal fun AiPreviewDialog(
    previewText: String,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    val title = stringResource(R.string.reader_ai_cleaned_text)
    val applyLabel = stringResource(R.string.reader_apply)
    val cancelLabel = stringResource(R.string.cancel)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(previewText)
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) {
                Text(applyLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(cancelLabel)
            }
        }
    )
}

@Composable
internal fun AiErrorDialog(
    errorSummary: String?,
    errorLog: String,
    onCopyError: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = stringResource(R.string.ai_cleaning_failed)
    val copyErrorLabel = stringResource(R.string.reader_copy_error)
    val dismissLabel = stringResource(R.string.reader_dismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                if (!errorSummary.isNullOrBlank()) {
                    Text(
                        text = errorSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(errorLog)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCopyError) {
                Text(copyErrorLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}
