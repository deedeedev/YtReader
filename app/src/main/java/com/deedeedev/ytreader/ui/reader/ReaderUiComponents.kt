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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HighlightSelectionToolbar(
    modifier: Modifier = Modifier,
    onColorSelected: (HighlightColor) -> Unit,
    showDelete: Boolean,
    onDeleteHighlight: () -> Unit
) {
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
                        contentDescription = "Delete highlight"
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
                text = "$number.",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = excerpt,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$progressPercent%",
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
            text = "$percent% $currentPage/$totalPages",
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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            IconButton(onClick = onPrevious, enabled = canGoPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous search result"
                )
            }
            Text(
                text = "$currentIndex/$totalResults",
                modifier = Modifier.testTag(READER_SEARCH_RESULTS_COUNT_TAG),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next search result"
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close search results"
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
