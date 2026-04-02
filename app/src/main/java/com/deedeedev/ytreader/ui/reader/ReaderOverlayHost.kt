package com.deedeedev.ytreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.R

@Composable
internal fun BoxScope.ReaderOverlayHost(
    isUiVisible: Boolean,
    isEditing: Boolean,
    showSelectionToolbar: Boolean,
    onSelectionColorSelected: (HighlightColor) -> Unit,
    onSelectionNoteClick: () -> Unit,
    selectionHasNote: Boolean,
    onDeleteHighlight: () -> Unit,
    hasActiveHighlight: Boolean,
    showSearchInOriginal: Boolean,
    onSearchInOriginal: () -> Unit,
    showSearchResultsToolbar: Boolean,
    showJumpBackToolbar: Boolean,
    searchResultsCurrentIndex: Int,
    searchResultsTotalCount: Int,
    canNavigateToPreviousSearchResult: Boolean,
    canNavigateToNextSearchResult: Boolean,
    onReturnToSearchOrigin: (() -> Unit)?,
    onNavigateToPreviousSearchResult: () -> Unit,
    onNavigateToNextSearchResult: () -> Unit,
    onCloseSearchResults: () -> Unit,
    onReplaceCurrent: (() -> Unit)?,
    onJumpBack: () -> Unit,
    fullscreenProgressPercent: Int,
    fullscreenPageProgress: PageProgress,
    showBrightnessIndicator: Boolean,
    brightnessIndicatorPercent: Int,
    snackbarHostState: SnackbarHostState
) {
    AnimatedVisibility(
        visible = showSelectionToolbar,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = if (isUiVisible) 84.dp else 16.dp
            )
            .navigationBarsPadding()
    ) {
        HighlightSelectionToolbar(
            modifier = Modifier.testTag(READER_SELECTION_TOOLBAR_TAG),
            onColorSelected = onSelectionColorSelected,
            onNoteClick = onSelectionNoteClick,
            hasNote = selectionHasNote,
            showDelete = hasActiveHighlight,
            onDeleteHighlight = onDeleteHighlight,
            showSearchInOriginal = showSearchInOriginal,
            onSearchInOriginal = onSearchInOriginal
        )
    }

    AnimatedVisibility(
        visible = showSearchResultsToolbar,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = if (isUiVisible) 84.dp else 16.dp
            )
            .navigationBarsPadding()
    ) {
        SearchResultsToolbar(
            currentIndex = searchResultsCurrentIndex,
            totalResults = searchResultsTotalCount,
            canGoPrevious = canNavigateToPreviousSearchResult,
            canGoNext = canNavigateToNextSearchResult,
            onReturnToOrigin = onReturnToSearchOrigin,
            onPrevious = onNavigateToPreviousSearchResult,
            onNext = onNavigateToNextSearchResult,
            onReplaceCurrent = onReplaceCurrent,
            onClose = onCloseSearchResults,
            modifier = Modifier.testTag(READER_SEARCH_RESULTS_BAR_TAG)
        )
    }

    AnimatedVisibility(
        visible = showJumpBackToolbar,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = if (isUiVisible) 84.dp else 16.dp
            )
            .navigationBarsPadding()
    ) {
        JumpBackToolbar(
            onJumpBack = onJumpBack,
            modifier = Modifier.testTag(READER_JUMP_BACK_BAR_TAG)
        )
    }

    if (!isUiVisible && !isEditing) {
        TinyProgressIndicator(
            percent = fullscreenProgressPercent,
            currentPage = fullscreenPageProgress.currentPage,
            totalPages = fullscreenPageProgress.totalPages,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 8.dp, bottom = 8.dp)
        )
    }

    AnimatedVisibility(
        visible = showBrightnessIndicator,
        enter = slideInVertically(initialOffsetY = { it / 2 }),
        exit = slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = if (isUiVisible) 88.dp else 16.dp)
    ) {
        TinyValueIndicator(
            text = stringResource(R.string.reader_progress_percent, brightnessIndicatorPercent),
            modifier = Modifier.testTag(READER_BRIGHTNESS_INDICATOR_TAG)
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
}
