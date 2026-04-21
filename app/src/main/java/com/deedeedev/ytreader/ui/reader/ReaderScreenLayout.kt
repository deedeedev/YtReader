package com.deedeedev.ytreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.domain.SubtitleSegment
import com.deedeedev.ytreader.ui.reader.webview.WebViewStudyContentPane
import com.deedeedev.ytreader.ui.reader.webview.WebViewOriginalContentPane
import com.deedeedev.ytreader.ui.reader.webview.WebViewReaderJs
import android.webkit.WebView

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
    bookmarks: List<BookmarkEntity>,
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
    onOriginalTimestampTap: (Long) -> Unit,
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
    onReplaceWithClipboard: () -> Unit,
    onRemoveEmptyLines: () -> Unit,
    onShowFind: () -> Unit,
    onShowVideoNotes: () -> Unit,
    onShowFindAndReplace: () -> Unit,
    onStartExternalAiCleaning: (String) -> Unit,
    onStartAiCleaning: (String) -> Unit,
    onRequestNotificationPermission: (String) -> Unit,
    hasTimestampedSegments: Boolean,
    onShowJumpToTime: () -> Unit,
    isAiCleaning: Boolean,
    showSelectionToolbar: Boolean,
    onSelectionColorSelected: (HighlightColor) -> Unit,
    onSelectionNoteClick: () -> Unit,
    selectionHasNote: Boolean,
    onDeleteHighlight: () -> Unit,
    selectedColor: HighlightColor = HighlightColor.RED,
    showSearchInOriginal: Boolean,
    onSearchInOriginal: () -> Unit,
    onBookmarkTapped: (BookmarkEntity) -> Unit,
    showSearchResultsToolbar: Boolean,
    showJumpBackFab: Boolean,
    searchResultsCurrentIndex: Int,
    searchResultsTotalCount: Int,
    canNavigateToPreviousSearchResult: Boolean,
    canNavigateToNextSearchResult: Boolean,
    onNavigateToPreviousSearchResult: () -> Unit,
    onNavigateToNextSearchResult: () -> Unit,
    onCloseSearchResults: () -> Unit,
    onReplaceCurrent: (() -> Unit)?,
    onJumpBack: () -> Unit,
    onUserDrag: () -> Unit,    fullscreenProgressPercent: Int,
    fullscreenPageProgress: PageProgress,
    showProgressIndicator: Boolean,
    showBrightnessIndicator: Boolean,
    brightnessIndicatorPercent: Int,
    snackbarHostState: SnackbarHostState,
    initialScrollPercent: Int = 0,
    annotationScrollOffset: Int? = null,
    onAnnotationNavigated: (() -> Unit)? = null,
    useWebView: Boolean = false,
    onWebViewStudyReady: ((WebView) -> Unit)? = null,
    onWebViewStudyDestroyed: (() -> Unit)? = null,
    onWebViewOriginalReady: ((WebView) -> Unit)? = null,
    onWebViewOriginalDestroyed: (() -> Unit)? = null,
    onWebViewScrollProgress: ((scrollY: Int, totalHeight: Int, viewportHeight: Int) -> Unit)? = null,
    onWebViewVisibleCharOffset: ((Int) -> Unit)? = null,
    onWebViewClearSelection: (() -> Unit)? = null,
    webViewStudyRef: WebView? = null,
    webViewScrollProgress: Float = 0f,
    webViewCanScroll: Boolean = false,
    onWebViewScrollToProgress: (Float) -> Unit = {},
    onSliderDragFinished: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (readerMode == ReaderMode.ORIGINAL) {
            WebViewOriginalContentPane(
                originalSegments = originalSegments,
                originalFallbackText = originalFallbackText,
                showTimestamps = showTimestamps,
                topContentPadding = topContentPadding,
                bottomContentPadding = bottomContentPadding,
                fontSize = fontSize,
                lineHeightMultiplier = lineHeightMultiplier,
                fontFamilyName = fontFamilyName,
                readerTextColor = readerTextColor,
                readerBackgroundColor = readerBackgroundColor,
                onReaderTap = onReaderTap,
                onOriginalTimestampTap = onOriginalTimestampTap,
                onScrollProgress = { scrollY, totalHeight, viewportHeight ->
                    onWebViewScrollProgress?.invoke(scrollY, totalHeight, viewportHeight)
                },
                onWebViewReady = { onWebViewOriginalReady?.invoke(it) },
                onWebViewDestroyed = { onWebViewOriginalDestroyed?.invoke() }
            )
        } else {
            val wv = webViewStudyRef
            WebViewStudyContentPane(
                    readOnlyContent = studyContent,
                    highlights = highlights,
                    bookmarks = bookmarks,
                    activeStudySearchRange = activeStudySearchRange,
                    topContentPadding = topContentPadding,
                    bottomContentPadding = bottomContentPadding,
                    fontSize = fontSize,
                    lineHeightMultiplier = lineHeightMultiplier,
                    fontFamilyName = fontFamilyName,
                    readerTextColor = readerTextColor,
                    readerBackgroundColor = readerBackgroundColor,
                    initialScrollPercent = initialScrollPercent,
                    annotationScrollOffset = annotationScrollOffset,
                    onAnnotationNavigated = onAnnotationNavigated,
                    isEditMode = isEditing,
                    onReaderTap = onReaderTap,
                    onSelectionRangeChanged = onSelectionRangeChanged,
                    onHighlightTapped = onHighlightTapped,
                    onBookmarkTapped = onBookmarkTapped,
                    onScrollProgress = { scrollY, totalHeight, viewportHeight ->
                        onWebViewScrollProgress?.invoke(scrollY, totalHeight, viewportHeight)
                    },
                    onVisibleCharOffsetChanged = onWebViewVisibleCharOffset,
                    onWebViewReady = { onWebViewStudyReady?.invoke(it) },
                    onWebViewDestroyed = { onWebViewStudyDestroyed?.invoke() },
                    onEditTextChanged = { text -> onEditTextChange(text) },
                    onRemoveEmptyLines = if (isEditing && wv != null) {
                        { with(WebViewReaderJs) { wv.removeEmptyLines() } }
                    } else null,
                    onTrimWhitespace = if (isEditing && wv != null) {
                        { with(WebViewReaderJs) { wv.trimWhitespace() } }
                    } else null,
                    onNormalizeSpacing = if (isEditing && wv != null) {
                        { with(WebViewReaderJs) { wv.normalizeSpacing() } }
                    } else null,
                    onCapitalizeFirstLetter = if (isEditing && wv != null) {
                        { with(WebViewReaderJs) { wv.capitalizeFirstLetter() } }
                    } else null,
                    onReplaceWithText = if (isEditing && wv != null) { text, replaceAll ->
                        with(WebViewReaderJs) { wv.replaceWithText(text, replaceAll) }
                    } else null,
                    onGetAllText = if (wv != null) {
                        { with(WebViewReaderJs) { wv.getAllText { } }; "" }
                    } else null,
                    onGetSelectedText = if (wv != null) {
                        { with(WebViewReaderJs) { wv.getSelectedText { } }; "" }
                    } else null,
                    onFindNext = if (wv != null) { query, caseSensitive, callback ->
                        with(WebViewReaderJs) { wv.findNext(query, caseSensitive, callback) }
                    } else null,
                    onFindPrevious = if (wv != null) { query, caseSensitive, callback ->
                        with(WebViewReaderJs) { wv.findPrevious(query, caseSensitive, callback) }
                    } else null,
                    onReplaceSingle = if (wv != null) { searchText, replaceText, caseSensitive, callback ->
                        with(WebViewReaderJs) { wv.replaceSingle(searchText, replaceText, caseSensitive, callback) }
                    } else null,
                    onReplaceAll = if (wv != null) { searchText, replaceText, caseSensitive, callback ->
                        with(WebViewReaderJs) { wv.replaceAll(searchText, replaceText, caseSensitive, callback) }
                    } else null,
                    onGetMatchCount = if (wv != null) { query, caseSensitive, callback ->
                        with(WebViewReaderJs) { wv.getMatchCount(query, caseSensitive, callback) }
                    } else null,
                    onClearFindHighlights = if (wv != null) {
                        { with(WebViewReaderJs) { wv.clearFindHighlights() } }
                    } else null
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

        ReaderScrollSlider(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = READER_BOTTOM_BAR_HEIGHT),
            visible = isUiVisible,
            scrollProgress = webViewScrollProgress,
            enabled = webViewCanScroll,
            onScrollToProgress = onWebViewScrollToProgress,
            onValueChangeFinished = onSliderDragFinished
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
            onReplaceWithClipboard = onReplaceWithClipboard,
            onRemoveEmptyLines = onRemoveEmptyLines,
            onShowFind = onShowFind,
            onShowVideoNotes = onShowVideoNotes,
            onShowFindAndReplace = onShowFindAndReplace,
            onStartExternalAiCleaning = onStartExternalAiCleaning,
            onStartAiCleaning = onStartAiCleaning,
            onRequestNotificationPermission = onRequestNotificationPermission,
            hasTimestampedSegments = hasTimestampedSegments,
            onShowJumpToTime = onShowJumpToTime,
            useWebView = useWebView,
            onEditUnavailable = {
                onRequestAction(PendingAction.ShowWebViewEditUnavailable)
            }
        )

        ReaderAnnotationsSwipeArea(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            isEditing = isEditing,
            gestureTag = READER_ANNOTATIONS_SWIPE_TAG,
            onSwipeUp = onShowVideoNotes
        )

        ReaderModeFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (isUiVisible) READER_FAB_BOTTOM_PADDING_WITH_CHROME else 16.dp)
                .navigationBarsPadding(),
            visible = isUiVisible,
            isOriginalMode = readerMode == ReaderMode.ORIGINAL,
            onSwitchMode = {
                val targetMode = if (readerMode == ReaderMode.STUDY) ReaderMode.ORIGINAL else ReaderMode.STUDY
                onRequestAction(PendingAction.SwitchMode(targetMode))
            }
        )

        AnimatedVisibility(
            visible = showJumpBackFab,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = if (isUiVisible) READER_FAB_BOTTOM_PADDING_WITH_CHROME else 16.dp)
                .navigationBarsPadding()
        ) {
            JumpBackFab(
                onJumpBack = onJumpBack,
                modifier = Modifier.testTag(READER_JUMP_BACK_BAR_TAG)
            )
        }

        ReaderOverlayHost(
            isUiVisible = isUiVisible,
            isEditing = isEditing,
            showSelectionToolbar = showSelectionToolbar,
            onSelectionColorSelected = onSelectionColorSelected,
            onSelectionNoteClick = onSelectionNoteClick,
            selectionHasNote = selectionHasNote,
            onDeleteHighlight = onDeleteHighlight,
            hasActiveHighlight = hasActiveHighlight(),
            selectedColor = selectedColor,
            showSearchInOriginal = showSearchInOriginal,
            onSearchInOriginal = onSearchInOriginal,
            showSearchResultsToolbar = showSearchResultsToolbar,
            searchResultsCurrentIndex = searchResultsCurrentIndex,
            searchResultsTotalCount = searchResultsTotalCount,
            canNavigateToPreviousSearchResult = canNavigateToPreviousSearchResult,
            canNavigateToNextSearchResult = canNavigateToNextSearchResult,
            onNavigateToPreviousSearchResult = onNavigateToPreviousSearchResult,
            onNavigateToNextSearchResult = onNavigateToNextSearchResult,
            onCloseSearchResults = onCloseSearchResults,
            onReplaceCurrent = onReplaceCurrent,
            fullscreenProgressPercent = fullscreenProgressPercent,
            fullscreenPageProgress = fullscreenPageProgress,
            showProgressIndicator = showProgressIndicator,
            showBrightnessIndicator = showBrightnessIndicator,
            brightnessIndicatorPercent = brightnessIndicatorPercent,
            snackbarHostState = snackbarHostState
        )
    }
}
