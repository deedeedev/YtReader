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

    @Test
    fun `replaceSingleMatch replaces text at specified range`() {
        val result = replaceSingleMatch(
            text = "hello world",
            start = 6,
            end = 11,
            replacement = "there"
        )
        assertEquals("hello there", result)
    }

    @Test
    fun `replaceSingleMatch handles replacement shorter than match`() {
        val result = replaceSingleMatch(
            text = "foo bar baz",
            start = 4,
            end = 7,
            replacement = "X"
        )
        assertEquals("foo X baz", result)
    }

    @Test
    fun `replaceSingleMatch handles replacement longer than match`() {
        val result = replaceSingleMatch(
            text = "a b c",
            start = 2,
            end = 3,
            replacement = "XYZ"
        )
        assertEquals("a XYZ c", result)
    }

    @Test
    fun `replaceSingleMatch returns original text for invalid range`() {
        val original = "hello"
        assertEquals(original, replaceSingleMatch(original, -1, 3, "x"))
        assertEquals(original, replaceSingleMatch(original, 3, 2, "x"))
        assertEquals(original, replaceSingleMatch(original, 0, 99, "x"))
    }

    @Test
    fun `findLiteralInSegments returns empty for blank query`() {
        val segments = listOf(SubtitleSegment(0, 1000, "hello world"))
        assertEquals(emptyList<SearchInOriginalResult>(), findLiteralInSegments("", segments, "..."))
        assertEquals(emptyList<SearchInOriginalResult>(), findLiteralInSegments("   ", segments, "..."))
    }

    @Test
    fun `findLiteralInSegments returns empty for empty segments`() {
        assertEquals(emptyList<SearchInOriginalResult>(), findLiteralInSegments("hello", emptyList(), "..."))
    }

    @Test
    fun `findLiteralInSegments returns empty when no matches`() {
        val segments = listOf(SubtitleSegment(0, 1000, "hello world"))
        assertEquals(emptyList<SearchInOriginalResult>(), findLiteralInSegments("xyz", segments, "..."))
    }

    @Test
    fun `findLiteralInSegments finds single match with correct offsets`() {
        val segments = listOf(SubtitleSegment(0, 1000, "hello world"))
        val results = findLiteralInSegments("world", segments, "...")
        assertEquals(1, results.size)
        assertEquals(0, results[0].segmentIndex)
        assertEquals(0L, results[0].startTime)
        assertEquals(6, results[0].matchStart)
        assertEquals(11, results[0].matchEnd)
    }

    @Test
    fun `findLiteralInSegments returns one result per segment`() {
        val segments = listOf(
            SubtitleSegment(0, 1000, "hello there"),
            SubtitleSegment(1000, 2000, "hello again")
        )
        val results = findLiteralInSegments("hello", segments, "...")
        assertEquals(2, results.size)
        assertEquals(0, results[0].segmentIndex)
        assertEquals(1, results[1].segmentIndex)
    }

    @Test
    fun `findLiteralInSegments is case insensitive`() {
        val segments = listOf(
            SubtitleSegment(0, 1000, "Hello World"),
            SubtitleSegment(1000, 2000, "HELLO AGAIN")
        )
        val results = findLiteralInSegments("hello", segments, "...")
        assertEquals(2, results.size)
    }

    @Test
    fun `findLiteralInSegments escapes regex special characters`() {
        val segments = listOf(SubtitleSegment(0, 1000, "hello.world"))
        val results = findLiteralInSegments("hello.world", segments, "...")
        assertEquals(1, results.size)
        val noResults = findLiteralInSegments("helloXworld", segments, "...")
        assertEquals(0, noResults.size)
    }

    @Test
    fun `findLiteralInSegments returns only first match per segment`() {
        val segments = listOf(SubtitleSegment(0, 1000, "hello hello hello"))
        val results = findLiteralInSegments("hello", segments, "...")
        assertEquals(1, results.size)
        assertEquals(0, results[0].matchStart)
        assertEquals(5, results[0].matchEnd)
    }
}
