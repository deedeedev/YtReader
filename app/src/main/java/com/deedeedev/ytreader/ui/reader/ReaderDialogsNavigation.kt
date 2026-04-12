package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.R

@Composable
internal fun JumpToTimeDialog(
    maxTimeMillis: Long,
    onJumpToTime: (Long) -> Unit,
    onJumpToStart: () -> Unit,
    onJumpToEnd: () -> Unit,
    onDismiss: () -> Unit
) {
    var hoursText by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    var secondsText by remember { mutableStateOf("") }

    val title = stringResource(R.string.reader_jump_to_time)
    val hoursLabel = stringResource(R.string.reader_hours)
    val minutesLabel = stringResource(R.string.reader_minutes)
    val secondsLabel = stringResource(R.string.reader_seconds)
    val startLabel = stringResource(R.string.reader_start)
    val endLabel = stringResource(R.string.reader_end)
    val cancelLabel = stringResource(R.string.cancel)
    val jumpLabel = stringResource(R.string.reader_jump_to_time)
    val maxTimeLabel = stringResource(R.string.reader_max_time, formatTime(maxTimeMillis))
    val fieldWidth = 72.dp

    fun performJump() {
        val hours = hoursText.toIntOrNull() ?: 0
        val minutes = minutesText.toIntOrNull() ?: 0
        val seconds = secondsText.toIntOrNull() ?: 0
        val totalMillis = (hours * 3600L + minutes * 60L + seconds) * 1000L
        onJumpToTime(totalMillis)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.width(fieldWidth),
                        label = { Text(hoursLabel) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.width(fieldWidth),
                        label = { Text(minutesLabel) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { secondsText = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.width(fieldWidth),
                        label = { Text(secondsLabel) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { performJump() }),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.padding(top = 12.dp))
                Text(
                    text = maxTimeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onJumpToStart) {
                    Text(startLabel)
                }
                TextButton(onClick = onJumpToEnd) {
                    Text(endLabel)
                }
                TextButton(onClick = { performJump() }) {
                    Text(jumpLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        }
    )
}

@Composable
internal fun SearchInOriginalDialog(
    query: String,
    results: List<SearchInOriginalResult>,
    totalSegments: Int,
    onSelectResult: (SearchInOriginalResult) -> Unit,
    onOpenInYoutube: (SearchInOriginalResult) -> Unit,
    onDismiss: () -> Unit
) {
    val title = stringResource(R.string.reader_search_in_original_title, query.take(40))
    val noResultsLabel = stringResource(R.string.reader_search_in_original_no_results)
    val closeLabel = stringResource(R.string.reader_search_in_original_close)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = {
            if (results.isEmpty()) {
                Text(noResultsLabel)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(results) { result ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clickable { onSelectResult(result) },
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatTime(result.startTime),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onOpenInYoutube(result) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = stringResource(R.string.reader_open_in_youtube),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                Text(
                                    text = result.excerpt,
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(
                                        R.string.reader_search_in_original_segment,
                                        result.segmentIndex + 1,
                                        totalSegments
                                    ),
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(closeLabel)
            }
        }
    )
}
