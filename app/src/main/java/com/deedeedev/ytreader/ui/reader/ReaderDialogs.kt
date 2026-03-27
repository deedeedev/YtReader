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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
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
    val findLabel = stringResource(R.string.find)
    val regexLabel = stringResource(R.string.reader_regex)
    val searchLabel = stringResource(R.string.reader_search)
    val caseSensitiveLabel = stringResource(R.string.reader_case_sensitive)
    val noResultsLabel = stringResource(R.string.reader_no_results)
    val closeLabel = stringResource(R.string.reader_close)
    val noFindResults = hasSearchedFind &&
        findErrorMessage == null &&
        findResults.isEmpty() &&
        originalSegmentFindResults.isEmpty()

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onClose,
        title = { Text(findLabel) },
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
                        label = { Text(regexLabel) },
                        isError = findErrorMessage != null,
                        supportingText = {
                            val errorMessage = findErrorMessage
                            if (errorMessage != null) {
                                Text(errorMessage)
                            }
                        }
                    )
                    IconButton(onClick = onRunSearch) {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = searchLabel)
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
                    Text(caseSensitiveLabel)
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
                            text = noResultsLabel,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(closeLabel)
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
    showInteractiveReplace: Boolean,
    onInteractiveReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onCancel: () -> Unit
) {
    val title = stringResource(R.string.find_and_replace)
    val regexLabel = stringResource(R.string.reader_regex)
    val replaceWithLabel = stringResource(R.string.reader_replace_with)
    val caseSensitiveLabel = stringResource(R.string.reader_case_sensitive)
    val replaceAllLabel = stringResource(R.string.reader_replace_all)
    val replaceLabel = stringResource(R.string.reader_replace)
    val cancelLabel = stringResource(R.string.cancel)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
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
                    label = { Text(regexLabel) },
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
                    label = { Text(replaceWithLabel) }
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
                    Text(caseSensitiveLabel)
                }
            }
        },
        confirmButton = {
            Row {
                if (showInteractiveReplace) {
                    TextButton(onClick = onInteractiveReplace) {
                        Text(replaceLabel)
                    }
                }
                TextButton(onClick = onReplaceAll) {
                    Text(replaceAllLabel)
                }
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
