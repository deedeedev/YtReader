package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deedeedev.ytreader.domain.SubtitleSegment

@Composable
internal fun ReaderOriginalPane(
    originalSegments: List<SubtitleSegment>,
    originalFallbackText: String,
    showTimestamps: Boolean,
    originalListState: LazyListState,
    originalFallbackScrollState: androidx.compose.foundation.ScrollState,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    fontSize: Float,
    lineHeightMultiplier: Float,
    fontFamilyName: String,
    fontFamily: FontFamily,
    readerTextColor: Int,
    readerBackgroundColor: Int,
    activeOriginalFallbackSearchRange: SelectionRange?,
    activeOriginalSegmentSearchResult: OriginalSegmentFindResult?,
    originalSelectionCoordinator: OriginalSelectionCoordinator,
    onReaderTap: (ReaderTapPosition) -> Unit,
    onOriginalFallbackViewportChanged: (Int) -> Unit
) {
    if (originalSegments.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = topContentPadding,
                    bottom = bottomContentPadding
                )
                .onUnconsumedTap { onReaderTap(it) }
                .onSizeChanged { onOriginalFallbackViewportChanged(it.height) }
                .verticalScroll(originalFallbackScrollState)
        ) {
            AndroidView<SelectableHighlightTextView>(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    SelectableHighlightTextView(context).apply {
                        originalSelectionCoordinator.textViews[-1] = this
                        bindOriginalFallback(
                            fontSize = fontSize,
                            lineHeightMultiplier = lineHeightMultiplier,
                            fontFamily = fontFamilyName,
                            textColor = readerTextColor,
                            backgroundColor = readerBackgroundColor,
                            content = originalFallbackText,
                            searchResultRange = activeOriginalFallbackSearchRange,
                            activeOwner = originalSelectionCoordinator.activeOwner,
                            onPlainTextTap = onReaderTap,
                            onSelectionOwnerChanged = { owner ->
                                val currentOwner = originalSelectionCoordinator.activeOwner
                                originalSelectionCoordinator.activeOwner = if (owner != null) {
                                    owner
                                } else if (currentOwner == -1) {
                                    null
                                } else {
                                    currentOwner
                                }
                            }
                        )
                    }
                },
                update = { textView ->
                    originalSelectionCoordinator.textViews[-1] = textView
                    textView.bindOriginalFallback(
                        fontSize = fontSize,
                        lineHeightMultiplier = lineHeightMultiplier,
                        fontFamily = fontFamilyName,
                        textColor = readerTextColor,
                        backgroundColor = readerBackgroundColor,
                        content = originalFallbackText,
                        searchResultRange = activeOriginalFallbackSearchRange,
                        activeOwner = originalSelectionCoordinator.activeOwner,
                        onPlainTextTap = onReaderTap,
                        onSelectionOwnerChanged = { owner ->
                            val currentOwner = originalSelectionCoordinator.activeOwner
                            originalSelectionCoordinator.activeOwner = if (owner != null) {
                                owner
                            } else if (currentOwner == -1) {
                                null
                            } else {
                                currentOwner
                            }
                        }
                    )
                }
            )
        }
        return
    }

    LazyColumn(
        state = originalListState,
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .onUnconsumedTap { onReaderTap(it) }
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = topContentPadding,
            bottom = bottomContentPadding
        )
    ) {
        itemsIndexed(originalSegments) { index, segment ->
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                if (showTimestamps) {
                    Text(
                        text = formatTime(segment.startTime),
                        fontSize = (fontSize * 0.8f).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        fontFamily = fontFamily
                    )
                }
                AndroidView<SelectableHighlightTextView>(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { context ->
                        SelectableHighlightTextView(context).apply {
                            originalSelectionCoordinator.textViews[index] = this
                            bindOriginalSegment(
                                segmentIndex = index,
                                fontSize = fontSize,
                                lineHeightMultiplier = lineHeightMultiplier,
                                fontFamily = fontFamilyName,
                                textColor = readerTextColor,
                                backgroundColor = readerBackgroundColor,
                                content = segment.text,
                                searchResultRange = activeOriginalSegmentSearchResult
                                    ?.takeIf { it.segmentIndex == index }
                                    ?.let { SelectionRange(start = it.start, end = it.end) },
                                activeOwner = originalSelectionCoordinator.activeOwner,
                                clearSelectionForOwner = { ownerIndex ->
                                    originalSelectionCoordinator.textViews[ownerIndex]?.clearSelection()
                                },
                                onPlainTextTap = onReaderTap,
                                onSelectionOwnerChanged = { owner ->
                                    val currentOwner = originalSelectionCoordinator.activeOwner
                                    originalSelectionCoordinator.activeOwner = if (owner != null) {
                                        owner
                                    } else if (currentOwner == index) {
                                        null
                                    } else {
                                        currentOwner
                                    }
                                }
                            )
                        }
                    },
                    update = { textView ->
                        originalSelectionCoordinator.textViews[index] = textView
                        textView.bindOriginalSegment(
                            segmentIndex = index,
                            fontSize = fontSize,
                            lineHeightMultiplier = lineHeightMultiplier,
                            fontFamily = fontFamilyName,
                            textColor = readerTextColor,
                            backgroundColor = readerBackgroundColor,
                            content = segment.text,
                            searchResultRange = activeOriginalSegmentSearchResult
                                ?.takeIf { it.segmentIndex == index }
                                ?.let { SelectionRange(start = it.start, end = it.end) },
                            activeOwner = originalSelectionCoordinator.activeOwner,
                            clearSelectionForOwner = { ownerIndex ->
                                originalSelectionCoordinator.textViews[ownerIndex]?.clearSelection()
                            },
                            onPlainTextTap = onReaderTap,
                            onSelectionOwnerChanged = { owner ->
                                val currentOwner = originalSelectionCoordinator.activeOwner
                                originalSelectionCoordinator.activeOwner = if (owner != null) {
                                    owner
                                } else if (currentOwner == index) {
                                    null
                                } else {
                                    currentOwner
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
internal fun ReaderStudyPane(
    isEditing: Boolean,
    editText: String,
    onEditTextChange: (String) -> Unit,
    readOnlyContent: String,
    highlights: List<TextHighlight>,
    activeStudySearchRange: SelectionRange?,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    fontSize: Float,
    lineHeightMultiplier: Float,
    lineHeightSp: Float,
    fontFamilyName: String,
    fontFamily: FontFamily,
    readerTextColor: Int,
    readerBackgroundColor: Int,
    editTextFieldTag: String,
    studyScrollState: androidx.compose.foundation.ScrollState,
    onStudyViewportChanged: (Int) -> Unit,
    onReaderTap: (ReaderTapPosition) -> Unit,
    onStudyTextViewReady: (JustifiedStudyTextView) -> Unit,
    onSelectionRangeChanged: (Int, Int) -> Unit,
    onHighlightTapped: (TextHighlight?) -> Unit,
    hasActiveHighlight: () -> Boolean,
    onClearActiveHighlight: () -> Unit,
    clearSelectionNow: () -> Unit
) {
    if (isEditing) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = topContentPadding,
                    bottom = bottomContentPadding
                )
        ) {
            TextField(
                value = editText,
                onValueChange = onEditTextChange,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(editTextFieldTag),
                textStyle = TextStyle(
                    fontSize = fontSize.sp,
                    lineHeight = lineHeightSp.sp,
                    fontFamily = fontFamily
                ),
                colors = TextFieldDefaults.colors()
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = topContentPadding,
                bottom = bottomContentPadding
            )
            .onUnconsumedTap { onReaderTap(it) }
            .onSizeChanged { onStudyViewportChanged(it.height) }
            .verticalScroll(studyScrollState)
    ) {
        AndroidView<JustifiedStudyTextView>(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                JustifiedStudyTextView(context).apply {
                    onStudyTextViewReady(this)
                    bindStudyContent(
                        fontSize = fontSize,
                        lineHeightMultiplier = lineHeightMultiplier,
                        fontFamily = fontFamilyName,
                        textColor = readerTextColor,
                        backgroundColor = readerBackgroundColor,
                        content = readOnlyContent,
                        highlights = highlights,
                        searchResultRange = activeStudySearchRange,
                        onSelectionChanged = onSelectionRangeChanged,
                        onHighlightTapped = onHighlightTapped,
                        onPlainTextTap = onReaderTap,
                        hasActiveHighlight = hasActiveHighlight,
                        clearActiveHighlight = onClearActiveHighlight,
                        clearSelectionNow = { clearSelectionNow() }
                    )
                }
            },
            update = { textView ->
                onStudyTextViewReady(textView)
                textView.bindStudyContent(
                    fontSize = fontSize,
                    lineHeightMultiplier = lineHeightMultiplier,
                    fontFamily = fontFamilyName,
                    textColor = readerTextColor,
                    backgroundColor = readerBackgroundColor,
                    content = readOnlyContent,
                    highlights = highlights,
                    searchResultRange = activeStudySearchRange,
                    onSelectionChanged = onSelectionRangeChanged,
                    onHighlightTapped = onHighlightTapped,
                    onPlainTextTap = onReaderTap,
                    hasActiveHighlight = hasActiveHighlight,
                    clearActiveHighlight = onClearActiveHighlight,
                    clearSelectionNow = { clearSelectionNow() }
                )
            }
        )
    }
}
