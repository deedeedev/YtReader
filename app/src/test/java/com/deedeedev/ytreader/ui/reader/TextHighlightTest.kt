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
            ?: error("Expected merge result")

        assertEquals(
            listOf(
                TextHighlight(start = 0, end = 2, color = HighlightColor.RED),
                TextHighlight(start = 5, end = 7, color = HighlightColor.GREEN),
                TextHighlight(start = 8, end = 10, color = HighlightColor.BLUE)
            ),
            merged.highlights
        )
    }

    @Test
    fun mergeHighlight_mergesOverlappingWithNewestColor() {
        val current = listOf(
            TextHighlight(start = 0, end = 4, color = HighlightColor.RED),
            TextHighlight(start = 6, end = 10, color = HighlightColor.GREEN)
        )

        val merged = mergeHighlight(current, start = 3, end = 8, color = HighlightColor.BLUE)
            ?: error("Expected merge result")

        assertEquals(
            listOf(TextHighlight(start = 0, end = 10, color = HighlightColor.BLUE)),
            merged.highlights
        )
    }

    @Test
    fun mergeHighlight_doesNotMergeAdjacentRanges() {
        val current = listOf(TextHighlight(start = 0, end = 3, color = HighlightColor.RED))

        val merged = mergeHighlight(current, start = 3, end = 6, color = HighlightColor.GREEN)
            ?: error("Expected merge result")

        assertEquals(
            listOf(
                TextHighlight(start = 0, end = 3, color = HighlightColor.RED),
                TextHighlight(start = 3, end = 6, color = HighlightColor.GREEN)
            ),
            merged.highlights
        )
    }

    @Test
    fun mergeHighlight_concatenatesNotesWithBlankLine() {
        val current = listOf(
            TextHighlight(start = 0, end = 4, color = HighlightColor.RED, note = "First note"),
            TextHighlight(start = 6, end = 10, color = HighlightColor.GREEN, note = "Second note")
        )

        val merged = mergeHighlight(current, start = 3, end = 8, color = HighlightColor.BLUE)
            ?: error("Expected merge result")

        assertEquals(
            TextHighlight(
                start = 0,
                end = 10,
                color = HighlightColor.BLUE,
                note = "First note\n\nSecond note"
            ),
            merged.mergedHighlight
        )
    }

    @Test
    fun mergeHighlight_includesNewNoteWhenCreatingHighlightFromSelection() {
        val merged = mergeHighlight(
            current = emptyList(),
            start = 2,
            end = 6,
            color = HighlightColor.YELLOW,
            note = "Fresh note"
        ) ?: error("Expected merge result")

        assertEquals(
            TextHighlight(start = 2, end = 6, color = HighlightColor.YELLOW, note = "Fresh note"),
            merged.mergedHighlight
        )
    }

    @Test
    fun findHighlightAtOffset_returnsHighlightWhenInsideRange() {
        val highlights = listOf(
            TextHighlight(start = 0, end = 4, color = HighlightColor.RED),
            TextHighlight(start = 6, end = 10, color = HighlightColor.BLUE)
        )

        val found = findHighlightAtOffset(highlights, offset = 7)

        assertEquals(TextHighlight(start = 6, end = 10, color = HighlightColor.BLUE), found)
    }

    @Test
    fun recolorHighlight_updatesOnlyMatchingHighlight() {
        val highlights = listOf(
            TextHighlight(start = 0, end = 4, color = HighlightColor.RED),
            TextHighlight(start = 6, end = 10, color = HighlightColor.BLUE)
        )

        val recolored = recolorHighlight(
            highlights = highlights,
            target = TextHighlight(start = 6, end = 10, color = HighlightColor.BLUE),
            newColor = HighlightColor.GREEN
        )

        assertEquals(
            listOf(
                TextHighlight(start = 0, end = 4, color = HighlightColor.RED),
                TextHighlight(start = 6, end = 10, color = HighlightColor.GREEN)
            ),
            recolored
        )
    }

    @Test
    fun deleteHighlightFromList_removesOnlyMatchingHighlight() {
        val highlights = listOf(
            TextHighlight(start = 0, end = 4, color = HighlightColor.RED),
            TextHighlight(start = 6, end = 10, color = HighlightColor.BLUE)
        )

        val updated = deleteHighlightFromList(
            highlights = highlights,
            target = TextHighlight(start = 0, end = 4, color = HighlightColor.RED)
        )

        assertEquals(
            listOf(TextHighlight(start = 6, end = 10, color = HighlightColor.BLUE)),
            updated
        )
    }
}
