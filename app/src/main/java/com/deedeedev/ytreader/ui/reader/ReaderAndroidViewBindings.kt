package com.deedeedev.ytreader.ui.reader

internal fun SelectableHighlightTextView.bindOriginalFallback(
    fontSize: Float,
    lineHeightMultiplier: Float,
    fontFamily: String,
    textColor: Int,
    backgroundColor: Int,
    content: String,
    activeOwner: Int?,
    onPlainTextTap: () -> Unit,
    onSelectionOwnerChanged: (Int?) -> Unit
) {
    textSize = fontSize
    setLineSpacing(0f, lineHeightMultiplier)
    applyTypeface(fontFamily)
    setReadableColors(textColor = textColor, backgroundColor = backgroundColor)
    onHighlightTappedListener = null
    onTextTapListener = { tapOutcome ->
        if (tapOutcome == TextTapOutcome.PLAIN_TEXT && activeOwner == null) {
            onPlainTextTap()
        }
    }
    onSelectionChangedListener = { start, end ->
        val hasSelection = minOf(start, end) >= 0 && maxOf(start, end) > minOf(start, end)
        onSelectionOwnerChanged(if (hasSelection) -1 else null)
    }
    setContentWithHighlights(
        content = content,
        highlights = emptyList(),
        redColor = 0,
        blueColor = 0,
        greenColor = 0,
        yellowColor = 0
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
    activeOwner: Int?,
    clearSelectionForOwner: (Int) -> Unit,
    onPlainTextTap: () -> Unit,
    onSelectionOwnerChanged: (Int?) -> Unit
) {
    textSize = fontSize
    setLineSpacing(0f, lineHeightMultiplier)
    applyTypeface(fontFamily)
    setJustificationEnabled(false)
    setReadableColors(textColor = textColor, backgroundColor = backgroundColor)
    onHighlightTappedListener = null
    onTextTapListener = { tapOutcome ->
        if (tapOutcome == TextTapOutcome.PLAIN_TEXT && activeOwner != null) {
            if (activeOwner != segmentIndex) {
                clearSelectionForOwner(activeOwner)
                onSelectionOwnerChanged(null)
            }
        } else if (tapOutcome == TextTapOutcome.PLAIN_TEXT) {
            onPlainTextTap()
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
        redColor = 0,
        blueColor = 0,
        greenColor = 0,
        yellowColor = 0
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
    onSelectionChanged: (start: Int, end: Int) -> Unit,
    onHighlightTapped: (TextHighlight?) -> Unit,
    onPlainTextTap: () -> Unit,
    hasActiveHighlight: () -> Boolean,
    clearActiveHighlight: () -> Unit,
    clearSelectionNow: () -> Unit
) {
    setTextSizeSp(fontSize)
    setLineHeightMultiplier(lineHeightMultiplier)
    applyTypeface(fontFamily)
    setReadableColors(textColor = textColor, backgroundColor = backgroundColor)
    onTextTapListener = { tapOutcome ->
        if (tapOutcome == TextTapOutcome.DISMISSED_SELECTION) {
            Unit
        }
    }
    onSelectionChangedListener = onSelectionChanged
    onHighlightTappedListener = { tappedHighlight ->
        if (tappedHighlight != null) {
            onHighlightTapped(tappedHighlight)
            clearSelectionNow()
        } else if (hasActiveHighlight()) {
            clearActiveHighlight()
        } else {
            onPlainTextTap()
        }
    }
    setContentWithHighlights(
        content = content,
        highlights = highlights,
        redColor = highlightSpanColor(HighlightColor.RED),
        blueColor = highlightSpanColor(HighlightColor.BLUE),
        greenColor = highlightSpanColor(HighlightColor.GREEN),
        yellowColor = highlightSpanColor(HighlightColor.YELLOW)
    )
}
