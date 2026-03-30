package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deedeedev.ytreader.data.local.BookmarkEntity
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
    onOriginalFallbackViewportChanged: (Int) -> Unit,
    onOriginalTimestampTap: (Long) -> Unit,
    onUserDrag: () -> Unit
) {
    val memoizedOnReaderTap by rememberUpdatedState(onReaderTap)
    val memoizedOnUserDrag by rememberUpdatedState(onUserDrag)

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
            .onUserDrag { onUserDrag() }
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
                        onPlainTextTap = memoizedOnReaderTap,
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
            .onUserDrag { onUserDrag() }
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
                        modifier = Modifier.clickable { onOriginalTimestampTap(segment.startTime) },
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
                            onPlainTextTap = memoizedOnReaderTap,
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
    bookmarks: List<BookmarkEntity>,
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
    studyLazyListState: LazyListState,
    chunks: List<TextChunk>,
    studySelectionCoordinator: StudySelectionCoordinator,
    onStudyViewportChanged: (androidx.compose.ui.unit.IntSize) -> Unit,
    onReaderTap: (ReaderTapPosition) -> Unit,
    onSelectionRangeChanged: (Int, Int) -> Unit,
    onHighlightTapped: (TextHighlight?) -> Unit,
    onBookmarkTapped: (BookmarkEntity) -> Unit,
    hasActiveHighlight: () -> Boolean,
    onClearActiveHighlight: () -> Unit,
    clearSelectionNow: () -> Unit,
    onUserDrag: () -> Unit
) {
    val memoizedOnReaderTap by rememberUpdatedState(onReaderTap)
    val memoizedOnSelectionRangeChanged by rememberUpdatedState(onSelectionRangeChanged)
    val memoizedOnHighlightTapped by rememberUpdatedState(onHighlightTapped)
    val memoizedOnBookmarkTapped by rememberUpdatedState(onBookmarkTapped)
    val memoizedHasActiveHighlight by rememberUpdatedState(hasActiveHighlight)
    val memoizedOnClearActiveHighlight by rememberUpdatedState(onClearActiveHighlight)
    val memoizedClearSelectionNow by rememberUpdatedState(clearSelectionNow)
    val memoizedOnUserDrag by rememberUpdatedState(onUserDrag)

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

    LazyColumn(
        state = studyLazyListState,
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = topContentPadding,
                bottom = bottomContentPadding
            )
            .onSizeChanged { onStudyViewportChanged(it) }
            .onUserDrag { onUserDrag() },
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed<TextChunk>(
            items = chunks,
            key = { index, chunk -> "study_chunk_${chunk.globalStartOffset}" }
        ) { index, chunk ->
            AndroidView<JustifiedStudyTextView>(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    JustifiedStudyTextView(context).apply {
                        studySelectionCoordinator.registerTextView(index, this)
                        bindStudyContent(
                            fontSize = fontSize,
                            lineHeightMultiplier = lineHeightMultiplier,
                            fontFamily = fontFamilyName,
                            textColor = readerTextColor,
                            backgroundColor = readerBackgroundColor,
                            content = chunk.text,
                            highlights = highlights,
                            bookmarks = bookmarks,
                            searchResultRange = activeStudySearchRange,
                            globalTextOffset = chunk.globalStartOffset,
                            onSelectionChanged = { start, end ->
                                studySelectionCoordinator.handleSelectionChanged(index, start, end, chunk.globalStartOffset)
                            },
                            onHighlightTapped = { tappedHighlight ->
                                memoizedOnHighlightTapped(tappedHighlight)
                            },
                            onBookmarkTapped = { tappedBookmark ->
                                memoizedOnBookmarkTapped(tappedBookmark)
                            },
                            onPlainTextTap = memoizedOnReaderTap,
                            hasActiveHighlight = memoizedHasActiveHighlight,
                            clearActiveHighlight = memoizedOnClearActiveHighlight,
                            clearSelectionNow = memoizedClearSelectionNow
                        )
                    }
                },
                update = { textView ->
                    studySelectionCoordinator.registerTextView(index, textView)
                    textView.bindStudyContent(
                        fontSize = fontSize,
                        lineHeightMultiplier = lineHeightMultiplier,
                        fontFamily = fontFamilyName,
                        textColor = readerTextColor,
                        backgroundColor = readerBackgroundColor,
                        content = chunk.text,
                        highlights = highlights,
                        bookmarks = bookmarks,
                        searchResultRange = activeStudySearchRange,
                        globalTextOffset = chunk.globalStartOffset,
                        onSelectionChanged = { start, end ->
                            studySelectionCoordinator.handleSelectionChanged(index, start, end, chunk.globalStartOffset)
                        },
                        onHighlightTapped = { tappedHighlight ->
                            memoizedOnHighlightTapped(tappedHighlight)
                        },
                        onBookmarkTapped = { tappedBookmark ->
                            memoizedOnBookmarkTapped(tappedBookmark)
                        },
                        onPlainTextTap = memoizedOnReaderTap,
                        hasActiveHighlight = memoizedHasActiveHighlight,
                        clearActiveHighlight = memoizedOnClearActiveHighlight,
                        clearSelectionNow = memoizedClearSelectionNow
                    )
                }
            )
        }
    }
}
