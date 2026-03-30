package com.deedeedev.ytreader.ui.reader

import com.deedeedev.ytreader.data.local.BookmarkEntity

internal fun SelectableHighlightTextView.bindOriginalFallback(
    fontSize: Float,
    lineHeightMultiplier: Float,
    fontFamily: String,
    textColor: Int,
    backgroundColor: Int,
    content: String,
    searchResultRange: SelectionRange?,
    activeOwner: Int?,
    onPlainTextTap: (ReaderTapPosition) -> Unit,
    onSelectionOwnerChanged: (Int?) -> Unit
) {
    textSize = fontSize
    setLineSpacing(0f, lineHeightMultiplier)
    applyTypeface(fontFamily)
    setReadableColors(textColor = textColor, backgroundColor = backgroundColor)
    onHighlightTappedListener = null
    onTextTapListener = { tapOutcome, tapPosition ->
        if (tapOutcome == TextTapOutcome.PLAIN_TEXT && activeOwner == null) {
            onPlainTextTap(tapPosition)
        }
    }
    onSelectionChangedListener = { start, end ->
        val hasSelection = minOf(start, end) >= 0 && maxOf(start, end) > minOf(start, end)
        onSelectionOwnerChanged(if (hasSelection) -1 else null)
    }
    setContentWithHighlights(
        content = content,
        highlights = emptyList(),
        searchResultRange = searchResultRange,
        redColor = 0,
        blueColor = 0,
        greenColor = 0,
        yellowColor = 0,
        searchResultColor = searchResultSpanColor()
    )
}

internal fun SelectableHighlightTextView.bindOriginalSegment(
    segmentIndex: Int,
    fontSize: Float,
    lineHeightMultiplier: Float,
    fontFamily: String,
    textColor: Int,
    backgroundColor: Int,
    content: String,
    searchResultRange: SelectionRange?,
    activeOwner: Int?,
    clearSelectionForOwner: (Int) -> Unit,
    onPlainTextTap: (ReaderTapPosition) -> Unit,
    onSelectionOwnerChanged: (Int?) -> Unit
) {
    textSize = fontSize
    setLineSpacing(0f, lineHeightMultiplier)
    applyTypeface(fontFamily)
    setJustificationEnabled(false)
    setReadableColors(textColor = textColor, backgroundColor = backgroundColor)
    onHighlightTappedListener = null
    onTextTapListener = { tapOutcome, tapPosition ->
        if (tapOutcome == TextTapOutcome.PLAIN_TEXT && activeOwner != null) {
            if (activeOwner != segmentIndex) {
                clearSelectionForOwner(activeOwner)
                onSelectionOwnerChanged(null)
            }
        } else if (tapOutcome == TextTapOutcome.PLAIN_TEXT) {
            onPlainTextTap(tapPosition)
        }
    }
    onSelectionChangedListener = { start, end ->
        val normalizedStart = minOf(start, end)
        val normalizedEnd = maxOf(start, end)
        val hasSelection = normalizedStart >= 0 && normalizedEnd > normalizedStart
        onSelectionOwnerChanged(if (hasSelection) segmentIndex else null)
    }
    setContentWithHighlights(
        content = content,
        highlights = emptyList(),
        searchResultRange = searchResultRange,
        redColor = 0,
        blueColor = 0,
        greenColor = 0,
        yellowColor = 0,
        searchResultColor = searchResultSpanColor()
    )
}

internal fun JustifiedStudyTextView.bindStudyContent(
    fontSize: Float,
    lineHeightMultiplier: Float,
    fontFamily: String,
    textColor: Int,
    backgroundColor: Int,
    content: String,
    highlights: List<TextHighlight>,
    bookmarks: List<BookmarkEntity>,
    searchResultRange: SelectionRange?,
    globalTextOffset: Int,
    onSelectionChanged: (start: Int, end: Int) -> Unit,
    onHighlightTapped: (TextHighlight?) -> Unit,
    onBookmarkTapped: (BookmarkEntity) -> Unit,
    onPlainTextTap: (ReaderTapPosition) -> Unit,
    hasActiveHighlight: () -> Boolean,
    clearActiveHighlight: () -> Unit,
    clearSelectionNow: () -> Unit
) {
    setTextSizeSp(fontSize)
    setLineHeightMultiplier(lineHeightMultiplier)
    applyTypeface(fontFamily)
    setReadableColors(textColor = textColor, backgroundColor = backgroundColor)
    onTextTapListener = { tapOutcome, tapPosition ->
        if (tapOutcome == TextTapOutcome.PLAIN_TEXT) {
            if (hasActiveHighlight() && !isBookmarkCornerTap(tapPosition)) {
                clearActiveHighlight()
            } else {
                onPlainTextTap(tapPosition)
            }
        }
    }
    onSelectionChangedListener = onSelectionChanged
    onHighlightTappedListener = { tappedHighlight ->
        if (tappedHighlight != null) {
            onHighlightTapped(tappedHighlight)
            clearSelectionNow()
        }
    }
    onBookmarkTappedListener = onBookmarkTapped
    this.globalTextOffset = globalTextOffset
    setContentWithHighlights(
        content = content,
        highlights = highlights,
        bookmarks = bookmarks,
        searchResultRange = searchResultRange,
        redColor = highlightSpanColor(HighlightColor.RED),
        blueColor = highlightSpanColor(HighlightColor.BLUE),
        greenColor = highlightSpanColor(HighlightColor.GREEN),
        yellowColor = highlightSpanColor(HighlightColor.YELLOW),
        searchResultColor = searchResultSpanColor(),
        filterToChunkRange = globalTextOffset > 0
    )
}
