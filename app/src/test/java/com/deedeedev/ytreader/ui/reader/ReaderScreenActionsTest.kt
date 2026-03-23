package com.deedeedev.ytreader.ui.reader

import com.deedeedev.ytreader.domain.SubtitleSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScreenActionsTest {

    @Test
    fun `executeReaderFindSearch returns error for invalid regex`() {
        val outcome = executeReaderFindSearch(
            query = "(",
            isCaseSensitive = false,
            isOriginalMode = false,
            sourceText = "First line",
            originalSegments = emptyList(),
            originalFallbackText = ""
        )

        assertEquals("Invalid regex.", outcome.errorMessage)
        assertTrue(outcome.findResults.isEmpty())
        assertTrue(outcome.originalSegmentFindResults.isEmpty())
    }

    @Test
    fun `executeReaderFindSearch returns study matches in study mode`() {
        val outcome = executeReaderFindSearch(
            query = "line",
            isCaseSensitive = false,
            isOriginalMode = false,
            sourceText = "First line\nSecond line",
            originalSegments = emptyList(),
            originalFallbackText = ""
        )

        assertEquals(2, outcome.findResults.size)
        assertTrue(outcome.originalSegmentFindResults.isEmpty())
        assertEquals(null, outcome.errorMessage)
    }

    @Test
    fun `executeReaderFindSearch returns segmented matches in original mode`() {
        val outcome = executeReaderFindSearch(
            query = "line",
            isCaseSensitive = false,
            isOriginalMode = true,
            sourceText = "",
            originalSegments = listOf(
                SubtitleSegment(startTime = 0, endTime = 1_000, text = "First line"),
                SubtitleSegment(startTime = 1_000, endTime = 2_000, text = "Second line")
            ),
            originalFallbackText = ""
        )

        assertEquals(2, outcome.originalSegmentFindResults.size)
        assertTrue(outcome.findResults.isEmpty())
    }

    @Test
    fun `executeReaderFindSearch uses fallback when no original segments`() {
        val outcome = executeReaderFindSearch(
            query = "Second",
            isCaseSensitive = true,
            isOriginalMode = true,
            sourceText = "",
            originalSegments = emptyList(),
            originalFallbackText = "First\nSecond"
        )

        assertEquals(1, outcome.findResults.size)
        assertEquals(0, outcome.originalSegmentFindResults.size)
    }
}
