package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.R

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
