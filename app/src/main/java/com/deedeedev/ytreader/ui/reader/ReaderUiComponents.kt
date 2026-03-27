package com.deedeedev.ytreader.ui.reader

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deedeedev.ytreader.R

@Composable
internal fun HighlightSelectionToolbar(
    modifier: Modifier = Modifier,
    onColorSelected: (HighlightColor) -> Unit,
    onNoteClick: () -> Unit,
    hasNote: Boolean,
    showDelete: Boolean,
    onDeleteHighlight: () -> Unit
) {
    val editNoteLabel = stringResource(R.string.reader_edit_note)
    val deleteHighlightLabel = stringResource(R.string.reader_remove_highlight)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HighlightColorButton(color = HighlightColor.RED, onClick = onColorSelected)
            HighlightColorButton(color = HighlightColor.BLUE, onClick = onColorSelected)
            HighlightColorButton(color = HighlightColor.GREEN, onClick = onColorSelected)
            HighlightColorButton(color = HighlightColor.YELLOW, onClick = onColorSelected)
            FilledTonalButton(
                onClick = onNoteClick,
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (hasNote) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (hasNote) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                    contentDescription = editNoteLabel
                )
            }
            if (showDelete) {
                FilledTonalButton(
                    onClick = onDeleteHighlight,
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = deleteHighlightLabel
                    )
                }
            }
        }
    }
}

@Composable
internal fun FindResultRow(
    number: Int,
    excerpt: String,
    progressPercent: Int,
    onClick: () -> Unit
) {
    val previousSearchResultLabel = stringResource(R.string.reader_previous_search_result)
    val nextSearchResultLabel = stringResource(R.string.reader_next_search_result)
    val closeSearchResultsLabel = stringResource(R.string.reader_search_results_close)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.reader_find_result_number, number),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = excerpt,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.reader_progress_percent, progressPercent),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
internal fun TinyProgressIndicator(
    percent: Int,
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = stringResource(R.string.reader_page_progress, percent, currentPage, totalPages),
            modifier = Modifier
                .testTag(READER_PAGE_PROGRESS_TAG)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
internal fun TinyValueIndicator(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
internal fun SearchResultsToolbar(
    currentIndex: Int,
    totalResults: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onReturnToOrigin: (() -> Unit)?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplaceCurrent: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val returnToOriginLabel = stringResource(R.string.reader_return_to_search_origin)
    val previousSearchResultLabel = stringResource(R.string.reader_previous_search_result)
    val nextSearchResultLabel = stringResource(R.string.reader_next_search_result)
    val closeSearchResultsLabel = stringResource(R.string.reader_search_results_close)
    val replaceThisLabel = stringResource(R.string.reader_replace_this)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (onReturnToOrigin != null) {
                IconButton(onClick = onReturnToOrigin) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = returnToOriginLabel
                    )
                }
            }
            IconButton(onClick = onPrevious, enabled = canGoPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = previousSearchResultLabel
                )
            }
            Text(
                text = stringResource(R.string.reader_search_results_counter, currentIndex, totalResults),
                modifier = Modifier.testTag(READER_SEARCH_RESULTS_COUNT_TAG),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = nextSearchResultLabel
                )
            }
            if (onReplaceCurrent != null) {
                TextButton(
                    onClick = onReplaceCurrent,
                    modifier = Modifier.testTag(READER_REPLACE_CURRENT_TAG),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(replaceThisLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = closeSearchResultsLabel
                )
            }
        }
    }
}

@Composable
internal fun JumpBackToolbar(
    onJumpBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jumpBackLabel = stringResource(R.string.reader_return_to_previous_position)
    val jumpBackShortLabel = stringResource(R.string.reader_jump_back)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onJumpBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = jumpBackLabel
                )
                Text(
                    text = jumpBackShortLabel,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun HighlightColorButton(
    color: HighlightColor,
    onClick: (HighlightColor) -> Unit
) {
    Button(
        onClick = { onClick(color) },
        modifier = Modifier.size(44.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = highlightButtonColor(color)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Spacer(modifier = Modifier.size(1.dp))
    }
}

private fun highlightButtonColor(color: HighlightColor): Color = when (color) {
    HighlightColor.RED -> Color(0xFFE57373)
    HighlightColor.BLUE -> Color(0xFF64B5F6)
    HighlightColor.GREEN -> Color(0xFF81C784)
    HighlightColor.YELLOW -> Color(0xFFFFF176)
}

internal fun highlightSpanColor(color: HighlightColor): Int = when (color) {
    HighlightColor.RED -> AndroidColor.parseColor("#66E57373")
    HighlightColor.BLUE -> AndroidColor.parseColor("#6664B5F6")
    HighlightColor.GREEN -> AndroidColor.parseColor("#6681C784")
    HighlightColor.YELLOW -> AndroidColor.parseColor("#66FFF176")
}

internal fun searchResultSpanColor(): Int = AndroidColor.parseColor("#99FFB300")
