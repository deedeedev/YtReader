package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
internal fun UnsavedChangesDialog(
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unsaved changes") },
        text = { Text("Do you want to discard your unsaved changes?") },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun FindDialog(
    modifier: Modifier = Modifier,
    inputTag: String,
    resultsTag: String,
    findQuery: String,
    onFindQueryChange: (String) -> Unit,
    findErrorMessage: String?,
    hasSearchedFind: Boolean,
    findResults: List<ReaderFindResult>,
    originalSegmentFindResults: List<OriginalSegmentFindResult>,
    isCaseSensitive: Boolean,
    onCaseSensitiveChange: (Boolean) -> Unit,
    onRunSearch: () -> Unit,
    onSelectStudyResult: (ReaderFindResult) -> Unit,
    onSelectOriginalFallbackResult: (ReaderFindResult) -> Unit,
    onSelectOriginalSegmentResult: (OriginalSegmentFindResult) -> Unit,
    isOriginalMode: Boolean,
    onClose: () -> Unit
) {
    val noFindResults = hasSearchedFind &&
        findErrorMessage == null &&
        findResults.isEmpty() &&
        originalSegmentFindResults.isEmpty()

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onClose,
        title = { Text("Find") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = onFindQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(inputTag),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { onRunSearch() }),
                        label = { Text("Regex") },
                        isError = findErrorMessage != null,
                        supportingText = {
                            val errorMessage = findErrorMessage
                            if (errorMessage != null) {
                                Text(errorMessage)
                            }
                        }
                    )
                    IconButton(onClick = onRunSearch) {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = "Search")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isCaseSensitive,
                        onCheckedChange = onCaseSensitiveChange
                    )
                    Text("Case sensitive")
                }
                when {
                    originalSegmentFindResults.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .padding(top = 12.dp)
                                .testTag(resultsTag)
                        ) {
                            items(originalSegmentFindResults) { result ->
                                FindResultRow(
                                    number = result.number,
                                    excerpt = result.excerpt,
                                    progressPercent = result.progressPercent,
                                    onClick = { onSelectOriginalSegmentResult(result) }
                                )
                            }
                        }
                    }

                    findResults.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .padding(top = 12.dp)
                                .testTag(resultsTag)
                        ) {
                            items(findResults) { result ->
                                FindResultRow(
                                    number = result.number,
                                    excerpt = result.excerpt,
                                    progressPercent = result.progressPercent,
                                    onClick = {
                                        if (isOriginalMode) {
                                            onSelectOriginalFallbackResult(result)
                                        } else {
                                            onSelectStudyResult(result)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    noFindResults -> {
                        Text(
                            text = "No results.",
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        }
    )
}

@Composable
internal fun FindReplaceDialog(
    inputTag: String,
    replacementTag: String,
    findText: String,
    onFindTextChange: (String) -> Unit,
    replaceText: String,
    onReplaceTextChange: (String) -> Unit,
    findReplaceErrorMessage: String?,
    isCaseSensitive: Boolean,
    onCaseSensitiveChange: (Boolean) -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Find and replace") },
        text = {
            Column {
                TextField(
                    value = findText,
                    onValueChange = onFindTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(inputTag),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None
                    ),
                    label = { Text("Regex") },
                    isError = findReplaceErrorMessage != null,
                    supportingText = {
                        val errorMessage = findReplaceErrorMessage
                        if (errorMessage != null) {
                            Text(errorMessage)
                        }
                    }
                )
                Spacer(modifier = Modifier.padding(top = 12.dp))
                TextField(
                    value = replaceText,
                    onValueChange = onReplaceTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(replacementTag),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None
                    ),
                    label = { Text("Replace with") }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isCaseSensitive,
                        onCheckedChange = onCaseSensitiveChange
                    )
                    Text("Case sensitive")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onReplace) {
                Text("Replace")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun AiPreviewDialog(
    previewText: String,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("AI cleaned text") },
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
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI cleaning failed") },
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
                Text("Copy error")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
internal fun EmptyTextDialog(onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onOk,
        title = { Text("Empty text") },
        text = { Text("Subtitle text cannot be empty.") },
        confirmButton = {
            TextButton(onClick = onOk) {
                Text("OK")
            }
        }
    )
}

@Composable
internal fun HighlightNoteDialog(
    noteText: String,
    hasExistingNote: Boolean,
    onNoteTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Highlight note") },
        text = {
            TextField(
                value = noteText,
                onValueChange = onNoteTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                label = { Text("Note") },
                maxLines = 8
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasExistingNote) {
                    TextButton(onClick = onDelete) {
                        Text("Remove note")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
internal fun BookmarkTitleDialog(
    titleText: String,
    onTitleTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add bookmark") },
        text = {
            TextField(
                value = titleText,
                onValueChange = onTitleTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                label = { Text("Title") },
                supportingText = {
                    Text("Leave empty to use the current top line.")
                },
                maxLines = 4
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
