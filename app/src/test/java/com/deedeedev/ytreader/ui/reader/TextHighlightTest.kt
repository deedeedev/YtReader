package com.deedeedev.ytreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class TextHighlightTest {

    @Test
    fun serializeAndParse_roundTrip() {
        val highlights = listOf(
            TextHighlight(start = 1, end = 3, color = HighlightColor.RED),
            TextHighlight(start = 5, end = 8, color = HighlightColor.BLUE)
        )

        val serialized = serializeHighlights(highlights)
        val parsed = parseHighlights(serialized)

        assertEquals(highlights, parsed)
    }

    @Test
    fun mergeHighlight_keepsNonOverlappingRanges() {
        val current = listOf(
            TextHighlight(start = 0, end = 2, color = HighlightColor.RED),
            TextHighlight(start = 5, end = 7, color = HighlightColor.GREEN)
        )

        val merged = mergeHighlight(current, start = 8, end = 10, color = HighlightColor.BLUE)

        assertEquals(
            listOf(
                TextHighlight(start = 0, end = 2, color = HighlightColor.RED),
                TextHighlight(start = 5, end = 7, color = HighlightColor.GREEN),
                TextHighlight(start = 8, end = 10, color = HighlightColor.BLUE)
            ),
            merged
        )
    }

    @Test
    fun mergeHighlight_mergesOverlappingWithNewestColor() {
        val current = listOf(
            TextHighlight(start = 0, end = 4, color = HighlightColor.RED),
            TextHighlight(start = 6, end = 10, color = HighlightColor.GREEN)
        )

        val merged = mergeHighlight(current, start = 3, end = 8, color = HighlightColor.BLUE)

        assertEquals(
            listOf(TextHighlight(start = 0, end = 10, color = HighlightColor.BLUE)),
            merged
        )
    }

    @Test
    fun mergeHighlight_doesNotMergeAdjacentRanges() {
        val current = listOf(TextHighlight(start = 0, end = 3, color = HighlightColor.RED))

        val merged = mergeHighlight(current, start = 3, end = 6, color = HighlightColor.GREEN)

        assertEquals(
            listOf(
                TextHighlight(start = 0, end = 3, color = HighlightColor.RED),
                TextHighlight(start = 3, end = 6, color = HighlightColor.GREEN)
            ),
            merged
        )
    }
}
