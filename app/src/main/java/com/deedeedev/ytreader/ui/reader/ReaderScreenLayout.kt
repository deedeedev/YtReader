package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.domain.SubtitleSegment

@Composable
internal fun ReaderScreenMainLayer(
    readerMode: ReaderMode,
    isUiVisible: Boolean,
    isEditing: Boolean,
    showTimestamps: Boolean,
    subtitleTitle: String,
    fontSize: Float,
    fontFamilyName: String,
    fontFamily: FontFamily,
    lineHeightMultiplier: Float,
    lineHeightSp: Float,
    readerTextColor: Int,
    readerBackgroundColor: Int,
    originalSegments: List<SubtitleSegment>,
    originalFallbackText: String,
    studyContent: String,
    highlights: List<TextHighlight>,
    activeStudySearchRange: SelectionRange?,
    activeOriginalFallbackSearchRange: SelectionRange?,
    activeOriginalSegmentSearchResult: OriginalSegmentFindResult?,
    editText: String,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    originalListState: LazyListState,
    originalFallbackScrollState: ScrollState,
    studyScrollState: ScrollState,
    originalSelectionCoordinator: OriginalSelectionCoordinator,
    appBrightnessPreference: Float,
    gestureBrightness: Float?,
    currentEffectiveBrightness: () -> Float,
    onGestureBrightnessChanged: (Float?) -> Unit,
    onShowBrightnessValue: (Float) -> Unit,
    onScheduleHideBrightnessValue: () -> Unit,
    onApplyReaderBrightness: (Float) -> Unit,
    onPersistBrightnessPreference: (Float) -> Unit,
    onReaderTap: (ReaderTapPosition) -> Unit,
    onSelectionRangeChanged: (Int, Int) -> Unit,
    onHighlightTapped: (TextHighlight?) -> Unit,
    hasActiveHighlight: () -> Boolean,
    onClearActiveHighlight: () -> Unit,
    clearSelectionNow: () -> Unit,
    onStudyTextViewReady: (JustifiedStudyTextView) -> Unit,
    onEditTextChange: (String) -> Unit,
    onOriginalFallbackViewportChanged: (Int) -> Unit,
    onStudyViewportChanged: (Int) -> Unit,
    onRequestAction: (PendingAction) -> Unit,
    onToggleTimestamps: () -> Unit,
    onEditSaveTap: () -> Unit,
    onDecreaseFontSize: () -> Unit,
    onIncreaseFontSize: () -> Unit,
    onChangeFontFamily: (String) -> Unit,
    isNotificationPermissionGranted: Boolean,
    currentText: String,
    onCopyText: (AnnotatedString) -> Unit,
    onShareText: (String) -> Unit,
    onRemoveEmptyLines: () -> Unit,
    onShowFind: () -> Unit,
    onShowFindAndReplace: () -> Unit,
    onStartAiCleaning: (String) -> Unit,
    onRequestNotificationPermission: (String) -> Unit,
    isAiCleaning: Boolean,
    showSelectionToolbar: Boolean,
    onSelectionColorSelected: (HighlightColor) -> Unit,
    onDeleteHighlight: () -> Unit,
    showSearchResultsToolbar: Boolean,
    searchResultsCurrentIndex: Int,
    searchResultsTotalCount: Int,
    canNavigateToPreviousSearchResult: Boolean,
    canNavigateToNextSearchResult: Boolean,
    onNavigateToPreviousSearchResult: () -> Unit,
    onNavigateToNextSearchResult: () -> Unit,
    onCloseSearchResults: () -> Unit,
    fullscreenProgressPercent: Int,
    fullscreenPageProgress: PageProgress,
    showBrightnessIndicator: Boolean,
    brightnessIndicatorPercent: Int,
    snackbarHostState: SnackbarHostState
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (readerMode == ReaderMode.ORIGINAL) {
            ReaderOriginalPane(
                originalSegments = originalSegments,
                originalFallbackText = originalFallbackText,
                showTimestamps = showTimestamps,
                originalListState = originalListState,
                originalFallbackScrollState = originalFallbackScrollState,
                topContentPadding = topContentPadding,
                bottomContentPadding = bottomContentPadding,
                fontSize = fontSize,
                lineHeightMultiplier = lineHeightMultiplier,
                fontFamilyName = fontFamilyName,
                fontFamily = fontFamily,
                readerTextColor = readerTextColor,
                readerBackgroundColor = readerBackgroundColor,
                activeOriginalFallbackSearchRange = activeOriginalFallbackSearchRange,
                activeOriginalSegmentSearchResult = activeOriginalSegmentSearchResult,
                originalSelectionCoordinator = originalSelectionCoordinator,
                onReaderTap = onReaderTap,
                onOriginalFallbackViewportChanged = onOriginalFallbackViewportChanged
            )
        } else {
            ReaderStudyPane(
                isEditing = isEditing,
                editText = editText,
                onEditTextChange = onEditTextChange,
                readOnlyContent = studyContent,
                highlights = highlights,
                activeStudySearchRange = activeStudySearchRange,
                topContentPadding = topContentPadding,
                bottomContentPadding = bottomContentPadding,
                fontSize = fontSize,
                lineHeightMultiplier = lineHeightMultiplier,
                lineHeightSp = lineHeightSp,
                fontFamilyName = fontFamilyName,
                fontFamily = fontFamily,
                readerTextColor = readerTextColor,
                readerBackgroundColor = readerBackgroundColor,
                editTextFieldTag = READER_EDIT_TEXT_FIELD_TAG,
                studyScrollState = studyScrollState,
                onStudyViewportChanged = onStudyViewportChanged,
                onReaderTap = onReaderTap,
                onStudyTextViewReady = onStudyTextViewReady,
                onSelectionRangeChanged = onSelectionRangeChanged,
                onHighlightTapped = onHighlightTapped,
                hasActiveHighlight = hasActiveHighlight,
                onClearActiveHighlight = onClearActiveHighlight,
                clearSelectionNow = clearSelectionNow
            )
        }

        var activeBrightness by remember { mutableStateOf(0f) }
        ReaderBrightnessGestureArea(
            modifier = Modifier.align(Alignment.CenterStart),
            isEditing = isEditing,
            appBrightnessPreference = appBrightnessPreference,
            gestureTag = READER_BRIGHTNESS_GESTURE_TAG,
            onStart = {
                activeBrightness = (gestureBrightness ?: currentEffectiveBrightness())
                    .coerceIn(MIN_READER_BRIGHTNESS, 1f)
                onGestureBrightnessChanged(activeBrightness)
                onShowBrightnessValue(activeBrightness)
            },
            onDrag = { dragAmount ->
                val updated = (activeBrightness - (dragAmount / BRIGHTNESS_SWIPE_FULL_RANGE_PX))
                    .coerceIn(MIN_READER_BRIGHTNESS, 1f)
                activeBrightness = updated
                onGestureBrightnessChanged(updated)
                onApplyReaderBrightness(updated)
                onShowBrightnessValue(updated)
            },
            onEnd = {
                gestureBrightness?.let { onPersistBrightnessPreference(it.coerceIn(MIN_READER_BRIGHTNESS, 1f)) }
                onGestureBrightnessChanged(null)
                onScheduleHideBrightnessValue()
            },
            onCancel = {
                gestureBrightness?.let { onPersistBrightnessPreference(it.coerceIn(MIN_READER_BRIGHTNESS, 1f)) }
                onGestureBrightnessChanged(null)
                onScheduleHideBrightnessValue()
            }
        )

        ReaderTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            visible = isUiVisible,
            title = subtitleTitle,
            topBarTag = READER_TOP_BAR_TAG,
            showCancelAction = readerMode == ReaderMode.STUDY && isEditing,
            onBack = { onRequestAction(PendingAction.ExitScreen) },
            onCancelEditing = { onRequestAction(PendingAction.ExitEditing) }
        )

        ReaderBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = isUiVisible,
            isOriginalMode = readerMode == ReaderMode.ORIGINAL,
            isEditing = isEditing,
            showTimestamps = showTimestamps,
            fontSize = fontSize,
            selectedFontFamily = fontFamilyName,
            isAiCleaning = isAiCleaning,
            isNotificationPermissionGranted = isNotificationPermissionGranted,
            currentText = currentText,
            onCopyText = onCopyText,
            onToggleTimestamps = onToggleTimestamps,
            onEditSaveTap = onEditSaveTap,
            onDecreaseFontSize = onDecreaseFontSize,
            onIncreaseFontSize = onIncreaseFontSize,
            onChangeFontFamily = onChangeFontFamily,
            onShareText = onShareText,
            onRemoveEmptyLines = onRemoveEmptyLines,
            onShowFind = onShowFind,
            onShowFindAndReplace = onShowFindAndReplace,
            onStartAiCleaning = onStartAiCleaning,
            onRequestNotificationPermission = onRequestNotificationPermission
        )

        ReaderModeFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (isUiVisible) 88.dp else 16.dp)
                .navigationBarsPadding(),
            visible = isUiVisible,
            isOriginalMode = readerMode == ReaderMode.ORIGINAL,
            onSwitchMode = {
                val targetMode = if (readerMode == ReaderMode.STUDY) ReaderMode.ORIGINAL else ReaderMode.STUDY
                onRequestAction(PendingAction.SwitchMode(targetMode))
            }
        )

        ReaderOverlayHost(
            isUiVisible = isUiVisible,
            isEditing = isEditing,
            showSelectionToolbar = showSelectionToolbar,
            onSelectionColorSelected = onSelectionColorSelected,
            onDeleteHighlight = onDeleteHighlight,
            hasActiveHighlight = hasActiveHighlight(),
            showSearchResultsToolbar = showSearchResultsToolbar,
            searchResultsCurrentIndex = searchResultsCurrentIndex,
            searchResultsTotalCount = searchResultsTotalCount,
            canNavigateToPreviousSearchResult = canNavigateToPreviousSearchResult,
            canNavigateToNextSearchResult = canNavigateToNextSearchResult,
            onNavigateToPreviousSearchResult = onNavigateToPreviousSearchResult,
            onNavigateToNextSearchResult = onNavigateToNextSearchResult,
            onCloseSearchResults = onCloseSearchResults,
            fullscreenProgressPercent = fullscreenProgressPercent,
            fullscreenPageProgress = fullscreenPageProgress,
            showBrightnessIndicator = showBrightnessIndicator,
            brightnessIndicatorPercent = brightnessIndicatorPercent,
            snackbarHostState = snackbarHostState
        )
    }
}
