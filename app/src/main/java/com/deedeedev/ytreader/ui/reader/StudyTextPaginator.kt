package com.deedeedev.ytreader.ui.reader

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

data class TextChunk(
    val index: Int,
    val text: String,
    val globalStartOffset: Int,
    val globalEndOffset: Int
)

data class StudyTextPagination(
    val chunks: List<TextChunk>,
    val fullText: String
)

fun paginateStudyText(
    text: String,
    textPaint: TextPaint,
    availableWidthPx: Int,
    targetChunkHeightPx: Int
): StudyTextPagination {
    if (text.isEmpty() || availableWidthPx <= 0 || targetChunkHeightPx <= 0) {
        return StudyTextPagination(chunks = emptyList(), fullText = text)
    }

    val width = availableWidthPx.coerceAtLeast(1)
    val measurementLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, textPaint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(0f, 1f)
        .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        .build()

    val chunks = mutableListOf<TextChunk>()
    var currentStartOffset = 0
    var chunkIndex = 0
    var cumulativeHeight = 0

    val lineCount = measurementLayout.lineCount
    var lastParagraphBreakOffset = currentStartOffset

    for (line in 0 until lineCount) {
        val lineTop = measurementLayout.getLineTop(line)
        val lineBottom = measurementLayout.getLineBottom(line)
        val lineHeight = lineBottom - lineTop

        if (cumulativeHeight + lineHeight > targetChunkHeightPx && currentStartOffset < text.length) {
            val splitOffset = findNearestParagraphBreak(text, lastParagraphBreakOffset, currentStartOffset, line, measurementLayout)

            if (splitOffset > currentStartOffset) {
                chunks.add(
                    TextChunk(
                        index = chunkIndex,
                        text = text.substring(currentStartOffset, splitOffset),
                        globalStartOffset = currentStartOffset,
                        globalEndOffset = splitOffset
                    )
                )
                chunkIndex++
                currentStartOffset = splitOffset
                cumulativeHeight = 0
                lastParagraphBreakOffset = currentStartOffset
            } else {
                val lineStart = measurementLayout.getLineStart(line)
                if (lineStart > currentStartOffset) {
                    val maxLookaheadOffset = (targetChunkHeightPx * 1.5f).toInt()
                    val safeLineStart = lineStart.coerceIn(currentStartOffset, currentStartOffset + maxLookaheadOffset)
                    chunks.add(
                        TextChunk(
                            index = chunkIndex,
                            text = text.substring(currentStartOffset, safeLineStart),
                            globalStartOffset = currentStartOffset,
                            globalEndOffset = safeLineStart
                        )
                    )
                    chunkIndex++
                    currentStartOffset = safeLineStart
                    cumulativeHeight = 0
                    lastParagraphBreakOffset = currentStartOffset
                }
            }
        }

        cumulativeHeight += lineHeight

        val lineStart = measurementLayout.getLineStart(line)
        val nextLineStart = if (line + 1 < lineCount) measurementLayout.getLineStart(line + 1) else text.length
        val lineText = text.substring(lineStart, nextLineStart.coerceAtMost(text.length))
        if (lineText.contains("\n\n")) {
            lastParagraphBreakOffset = nextLineStart
        }
    }

    if (currentStartOffset < text.length) {
        chunks.add(
            TextChunk(
                index = chunkIndex,
                text = text.substring(currentStartOffset),
                globalStartOffset = currentStartOffset,
                globalEndOffset = text.length
            )
        )
    }

    return StudyTextPagination(chunks = chunks, fullText = text)
}

private fun findNearestParagraphBreak(
    text: String,
    lastParagraphBreakOffset: Int,
    currentStartOffset: Int,
    line: Int,
    measurementLayout: StaticLayout
): Int {
    val lineStart = measurementLayout.getLineStart(line)
    val searchStart = lastParagraphBreakOffset.coerceAtLeast(currentStartOffset)
    val searchEnd = lineStart

    if (searchEnd <= searchStart) {
        return lineStart
    }

    val searchText = text.substring(searchStart, searchEnd)
    val lastDoubleNewline = searchText.lastIndexOf("\n\n")

    return if (lastDoubleNewline >= 0) {
        searchStart + lastDoubleNewline + 2
    } else {
        lineStart
    }
}

fun buildMeasurementPaint(
    fontSize: Float,
    fontFamilyName: String,
    lineHeightMultiplier: Float
): TextPaint {
    return TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = fontSize
        typeface = android.graphics.Typeface.DEFAULT
    }
}

fun findChunkContainingOffset(
    chunks: List<TextChunk>,
    globalOffset: Int
): Pair<Int, Int>? {
    if (chunks.isEmpty()) return null

    var low = 0
    var high = chunks.lastIndex

    while (low <= high) {
        val mid = (low + high) / 2
        val chunk = chunks[mid]
        when {
            globalOffset < chunk.globalStartOffset -> high = mid - 1
            globalOffset >= chunk.globalEndOffset -> low = mid + 1
            else -> {
                val offsetWithinChunk = globalOffset - chunk.globalStartOffset
                return Pair(mid, offsetWithinChunk)
            }
        }
    }

    val nearestChunk = when {
        low >= chunks.size -> chunks.lastIndex
        high < 0 -> 0
        else -> low.coerceIn(0, chunks.lastIndex)
    }

    val nearest = chunks[nearestChunk]
    val offsetWithinNearest = (globalOffset - nearest.globalStartOffset).coerceIn(0, nearest.text.length - 1)
    return Pair(nearestChunk, offsetWithinNearest)
}
