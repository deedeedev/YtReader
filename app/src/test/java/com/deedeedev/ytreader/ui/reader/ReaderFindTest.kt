package com.deedeedev.ytreader.ui.reader

import com.deedeedev.ytreader.domain.SubtitleSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderFindTest {
    private val emptyQueryMessage = "Enter a regex to search."
    private val invalidRegexMessage = "Invalid regex."
    private val excerptEllipsis = "..."

    @Test
    fun `compileFindRegex trims input and ignores case`() {
        val regex = compileFindRegex(
            "  hello  ",
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage
        ).getOrThrow()

        val matches = findRegexMatches("Hello there", regex, excerptEllipsis)

        assertEquals(1, matches.size)
        assertEquals(0, matches.first().start)
        assertEquals(5, matches.first().end)
    }

    @Test
    fun `compileFindRegex respects case sensitivity when enabled`() {
        val regex = compileFindRegex(
            query = "hello",
            isCaseSensitive = true,
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage
        ).getOrThrow()

        val matches = findRegexMatches("Hello hello", regex, excerptEllipsis)

        assertEquals(1, matches.size)
        assertEquals(6, matches.first().start)
        assertEquals(11, matches.first().end)
    }

    @Test
    fun `compileFindRegex returns failure for invalid regex`() {
        val result = compileFindRegex(
            "(",
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage
        )

        assertTrue(result.isFailure)
        assertEquals("Invalid regex.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `findRegexMatches returns numbered results with progress`() {
        val regex = compileFindRegex(
            "hello",
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage
        ).getOrThrow()

        val matches = findRegexMatches("hello there hello again", regex, excerptEllipsis)

        assertEquals(2, matches.size)
        assertEquals(1, matches[0].number)
        assertEquals(2, matches[1].number)
        assertEquals(0, matches[0].progressPercent)
        assertTrue(matches[1].progressPercent > matches[0].progressPercent)
    }

    @Test
    fun `findRegexMatches builds excerpt near text edges`() {
        val regex = compileFindRegex(
            "alpha|omega",
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage
        ).getOrThrow()

        val matches = findRegexMatches("alpha middle section omega", regex, excerptEllipsis)

        assertEquals("alpha middle section omega", matches.first().excerpt)
        assertEquals("alpha middle section omega", matches.last().excerpt)
    }

    @Test
    fun `findRegexMatchesInSegments searches each segment and computes global progress`() {
        val regex = compileFindRegex(
            "line",
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage
        ).getOrThrow()
        val segments = listOf(
            SubtitleSegment(startTime = 0, endTime = 1_000, text = "First line"),
            SubtitleSegment(startTime = 1_000, endTime = 2_000, text = "Second line")
        )

        val matches = findRegexMatchesInSegments(segments, regex, excerptEllipsis)

        assertEquals(2, matches.size)
        assertEquals(0, matches[0].segmentIndex)
        assertEquals(1, matches[1].segmentIndex)
        assertEquals(1, matches[0].number)
        assertEquals(2, matches[1].number)
        assertTrue(matches[1].progressPercent > matches[0].progressPercent)
    }

    @Test
    fun `replaceRegexMatches supports capture groups`() {
        val updated = replaceRegexMatches(
            text = "First line\nSecond line",
            query = "(line)",
            replacement = "[$1]",
            isCaseSensitive = false,
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage
        ).getOrThrow()

        assertEquals("First [line]\nSecond [line]", updated.updatedText)
        assertEquals(2, updated.replacementCount)
    }

    @Test
    fun `replaceRegexMatches returns failure for invalid regex`() {
        val result = replaceRegexMatches(
            text = "First line",
            query = "(",
            replacement = "value",
            isCaseSensitive = false,
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage
        )

        assertTrue(result.isFailure)
        assertEquals("Invalid regex.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `replaceRegexMatches ignores case by default`() {
        val updated = replaceRegexMatches(
            text = "Line line LINE",
            query = "line",
            replacement = "match",
            isCaseSensitive = false,
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage
        ).getOrThrow()

        assertEquals("match match match", updated.updatedText)
        assertEquals(3, updated.replacementCount)
    }

    @Test
    fun `replaceRegexMatches respects case sensitivity`() {
        val updated = replaceRegexMatches(
            text = "Line line LINE",
            query = "line",
            replacement = "match",
            isCaseSensitive = true,
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage
        ).getOrThrow()

        assertEquals("Line match LINE", updated.updatedText)
        assertEquals(1, updated.replacementCount)
    }
}
