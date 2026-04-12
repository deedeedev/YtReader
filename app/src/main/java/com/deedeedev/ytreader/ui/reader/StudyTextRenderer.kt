package com.deedeedev.ytreader.ui.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.text.StaticLayout

internal class StudyTextRenderer(
    private val textPaint: android.text.TextPaint
) {
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun drawRangeBackground(
        canvas: Canvas,
        textLayout: StaticLayout,
        content: String,
        startOffset: Int,
        endOffset: Int,
        color: Int
    ) {
        if (startOffset >= endOffset || content.isEmpty()) return
        rangePaint.color = color
        val startLine = textLayout.getLineForOffset(startOffset)
        val endCharOffset = (endOffset - 1).coerceIn(0, content.length - 1)
        val endLine = textLayout.getLineForOffset(endCharOffset)
        for (line in startLine..endLine) {
            val left = if (line == startLine) {
                textLayout.getPrimaryHorizontal(startOffset)
            } else {
                textLayout.getLineLeft(line)
            }
            val right = if (line == endLine) {
                if (endOffset >= content.length) textLayout.getLineRight(line)
                else textLayout.getPrimaryHorizontal(endOffset)
            } else {
                textLayout.getLineRight(line)
            }
            val top = textLayout.getLineTop(line).toFloat()
            val bottom = textLayout.getLineBottom(line).toFloat()
            if (right > left) {
                canvas.drawRect(left, top, right, bottom, rangePaint)
            }
        }
    }

    fun drawHighlightBackgrounds(
        canvas: Canvas,
        textLayout: StaticLayout,
        content: String,
        highlights: List<TextHighlight>,
        redColor: Int,
        blueColor: Int,
        greenColor: Int,
        yellowColor: Int,
        visTop: Int,
        visBottom: Int
    ) {
        if (highlights.isEmpty() || content.isEmpty()) return
        val visLineStart = textLayout.getLineForVertical(visTop.coerceAtLeast(0))
        val visLineEnd = textLayout.getLineForVertical(visBottom.coerceAtLeast(0))
        highlights.forEach { highlight ->
            val color = when (highlight.color) {
                HighlightColor.RED -> redColor
                HighlightColor.BLUE -> blueColor
                HighlightColor.GREEN -> greenColor
                HighlightColor.YELLOW -> yellowColor
            }
            val hlStartLine = textLayout.getLineForOffset(highlight.start)
            if (hlStartLine > visLineEnd) return@forEach
            val hlEndCharOffset = (highlight.end - 1).coerceIn(0, content.length - 1)
            val hlEndLine = textLayout.getLineForOffset(hlEndCharOffset)
            if (hlEndLine < visLineStart) return@forEach
            drawRangeBackground(canvas, textLayout, content, highlight.start, highlight.end, color)
        }
    }

    fun drawSearchResultBackground(
        canvas: Canvas,
        textLayout: StaticLayout,
        content: String,
        searchResultRange: TextRange?,
        searchResultColor: Int,
        visTop: Int,
        visBottom: Int
    ) {
        searchResultRange?.let { range ->
            val visLineStart = textLayout.getLineForVertical(visTop.coerceAtLeast(0))
            val visLineEnd = textLayout.getLineForVertical(visBottom.coerceAtLeast(0))
            val startLine = textLayout.getLineForOffset(range.start)
            if (startLine > visLineEnd) return
            val endCharOffset = (range.end - 1).coerceIn(0, content.length - 1)
            val endLine = textLayout.getLineForOffset(endCharOffset)
            if (endLine < visLineStart) return
            drawRangeBackground(canvas, textLayout, content, range.start, range.end, searchResultColor)
        }
    }

    fun drawSelectionBackground(
        canvas: Canvas,
        textLayout: StaticLayout,
        content: String,
        selectionRange: TextRange?,
        selectionColor: Int,
        visTop: Int,
        visBottom: Int
    ) {
        selectionRange?.let { range ->
            val visLineStart = textLayout.getLineForVertical(visTop.coerceAtLeast(0))
            val visLineEnd = textLayout.getLineForVertical(visBottom.coerceAtLeast(0))
            val startLine = textLayout.getLineForOffset(range.start)
            if (startLine > visLineEnd) return
            val endCharOffset = (range.end - 1).coerceIn(0, content.length - 1)
            val endLine = textLayout.getLineForOffset(endCharOffset)
            if (endLine < visLineStart) return
            drawRangeBackground(canvas, textLayout, content, range.start, range.end, selectionColor)
        }
    }

    fun drawHandle(
        canvas: Canvas,
        handle: StudyTextSelectionController.SelectionHandleVisual,
        handlePaint: Paint,
        handleStemWidth: Float,
        handleStemHeight: Float,
        handleRadius: Float
    ) {
        canvas.drawRect(
            handle.centerX - (handleStemWidth / 2f),
            handle.anchorY,
            handle.centerX + (handleStemWidth / 2f),
            handle.anchorY + handleStemHeight,
            handlePaint
        )
        canvas.drawCircle(
            handle.centerX,
            handle.anchorY + handleStemHeight + handleRadius,
            handleRadius,
            handlePaint
        )
    }

    fun drawHighlightNoteIndicators(
        canvas: Canvas,
        textLayout: StaticLayout,
        content: String,
        highlights: List<TextHighlight>,
        noteIndicatorPaint: Paint,
        noteIndicatorStrokePaint: Paint,
        visTop: Int,
        visBottom: Int,
        dpToPx: (Float) -> Float
    ) {
        if (content.isEmpty() || highlights.isEmpty()) return
        val radius = dpToPx(4.5f)
        val horizontalGap = dpToPx(5f)
        val topInset = dpToPx(4f)
        val visLineStart = textLayout.getLineForVertical(visTop.coerceAtLeast(0))
        val visLineEnd = textLayout.getLineForVertical(visBottom.coerceAtLeast(0))
        highlights.forEach { highlight ->
            if (highlight.note.isNullOrBlank()) return@forEach
            val startOffset = highlight.start.coerceIn(0, content.lastIndex)
            val line = textLayout.getLineForOffset(startOffset)
            if (line > visLineEnd || line < visLineStart) return@forEach
            val lineRight = textLayout.getLineRight(line)
            val lineLeft = textLayout.getLineLeft(line)
            val rangeStartX = textLayout.getPrimaryHorizontal(startOffset)
            val x = (rangeStartX - horizontalGap)
                .coerceAtLeast(lineLeft + radius)
                .coerceAtMost(lineRight - radius)
            val y = (textLayout.getLineTop(line) + topInset + radius)
                .coerceAtMost(textLayout.getLineBottom(line) - radius)
            canvas.drawCircle(x, y, radius, noteIndicatorPaint)
            canvas.drawCircle(x, y, radius, noteIndicatorStrokePaint)
        }
    }
}
