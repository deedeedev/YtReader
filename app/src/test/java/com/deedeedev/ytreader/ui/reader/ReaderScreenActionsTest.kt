package com.deedeedev.ytreader.ui.reader

import com.deedeedev.ytreader.domain.SubtitleSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScreenActionsTest {
    private val emptyQueryMessage = "Enter a regex to search."
    private val invalidRegexMessage = "Invalid regex."
    private val excerptEllipsis = "..."

    @Test
    fun `classifyReaderTapZone returns toggle for center taps`() {
        val zone = classifyReaderTapZone(ReaderTapPosition(xFraction = 0.5f, yFraction = 0.5f))

        assertEquals(ReaderTapZone.TOGGLE_UI, zone)
    }

    @Test
    fun `classifyReaderTapZone uses horizontal precedence for corners`() {
        val topRight = classifyReaderTapZone(ReaderTapPosition(xFraction = 0.9f, yFraction = 0.1f))
        val bottomLeft = classifyReaderTapZone(ReaderTapPosition(xFraction = 0.1f, yFraction = 0.9f))

        assertEquals(ReaderTapZone.NEXT_PAGE, topRight)
        assertEquals(ReaderTapZone.PREVIOUS_PAGE, bottomLeft)
    }

    @Test
    fun `classifyReaderTapZone uses vertical edges inside center column`() {
        val topCenter = classifyReaderTapZone(ReaderTapPosition(xFraction = 0.5f, yFraction = 0.1f))
        val bottomCenter = classifyReaderTapZone(ReaderTapPosition(xFraction = 0.5f, yFraction = 0.9f))

        assertEquals(ReaderTapZone.PREVIOUS_PAGE, topCenter)
        assertEquals(ReaderTapZone.NEXT_PAGE, bottomCenter)
    }

    @Test
    fun `targetScrollForPageStep moves by viewport and clamps`() {
        val next = targetScrollForPageStep(
            currentValue = 250,
            maxValue = 900,
            viewportHeightPx = 300,
            isForward = true
        )
        val previous = targetScrollForPageStep(
            currentValue = 250,
            maxValue = 900,
            viewportHeightPx = 300,
            isForward = false
        )
        val clampedEnd = targetScrollForPageStep(
            currentValue = 850,
            maxValue = 900,
            viewportHeightPx = 300,
            isForward = true
        )

        assertEquals(550, next)
        assertEquals(0, previous)
        assertEquals(900, clampedEnd)
    }

    @Test
    fun `targetListIndexForPageStep moves by visible count and clamps`() {
        val next = targetListIndexForPageStep(
            currentFirstVisibleItemIndex = 4,
            totalItems = 20,
            visibleItemsCount = 5,
            isForward = true
        )
        val previous = targetListIndexForPageStep(
            currentFirstVisibleItemIndex = 4,
            totalItems = 20,
            visibleItemsCount = 5,
            isForward = false
        )
        val clampedEnd = targetListIndexForPageStep(
            currentFirstVisibleItemIndex = 18,
            totalItems = 20,
            visibleItemsCount = 5,
            isForward = true
        )

        assertEquals(9, next)
        assertEquals(0, previous)
        assertEquals(19, clampedEnd)
    }

    @Test
    fun `executeReaderFindSearch returns error for invalid regex`() {
        val outcome = executeReaderFindSearch(
            query = "(",
            isCaseSensitive = false,
            isOriginalMode = false,
            sourceText = "First line",
            originalSegments = emptyList(),
            originalFallbackText = "",
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage,
            excerptEllipsis = excerptEllipsis
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
            originalFallbackText = "",
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage,
            excerptEllipsis = excerptEllipsis
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
            originalFallbackText = "",
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage,
            excerptEllipsis = excerptEllipsis
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
            originalFallbackText = "First\nSecond",
            emptyQueryMessage = emptyQueryMessage,
            invalidRegexMessage = invalidRegexMessage,
            excerptEllipsis = excerptEllipsis
        )

        assertEquals(1, outcome.findResults.size)
        assertEquals(0, outcome.originalSegmentFindResults.size)
    }

    @Test
    fun `buildExternalAiCleaningShareText joins trimmed prompt and study text`() {
        val shareText = buildExternalAiCleaningShareText(
            prompt = "  Clean this text carefully.  ",
            studyText = "  First line\nSecond line  "
        )

        assertEquals("Clean this text carefully.\n\nFirst line\nSecond line", shareText)
    }

    @Test
    fun `buildExternalAiCleaningShareText returns available content when one side is blank`() {
        assertEquals(
            "Only text",
            buildExternalAiCleaningShareText(prompt = "   ", studyText = " Only text ")
        )
        assertEquals(
            "Only prompt",
            buildExternalAiCleaningShareText(prompt = " Only prompt ", studyText = "   ")
        )
    }

    @Test
    fun `buildTimestampedYoutubeUrl appends youtube timestamp parameter`() {
        val url = buildTimestampedYoutubeUrl(videoId = "abc123DEF45", timestampMillis = 83_000)

        assertEquals("https://www.youtube.com/watch?v=abc123DEF45&t=83s", url)
    }

    @Test
    fun `buildTimestampedYoutubeUrl clamps negative timestamps and blank video ids`() {
        assertEquals("", buildTimestampedYoutubeUrl(videoId = "   ", timestampMillis = 10_000))
        assertEquals(
            "https://www.youtube.com/watch?v=abc123DEF45&t=0s",
            buildTimestampedYoutubeUrl(videoId = "abc123DEF45", timestampMillis = -1_000)
        )
    }

    @Test
    fun `search results navigation clamps at edges`() {
        val initial = SearchResultsMode.Study(
            results = listOf(
                ReaderFindResult(0, 4, 1, "first", 0),
                ReaderFindResult(10, 14, 2, "second", 50)
            ),
            activeIndex = 0
        )

        assertEquals(false, canNavigateToPreviousSearchResult(initial))
        assertEquals(true, canNavigateToNextSearchResult(initial))

        val next = moveToNextSearchResult(initial)
        assertEquals(1, next.activeIndex)
        assertEquals(false, canNavigateToNextSearchResult(next))

        val clampedNext = moveToNextSearchResult(next)
        val clampedPrevious = moveToPreviousSearchResult(initial)

        assertEquals(1, clampedNext.activeIndex)
        assertEquals(0, clampedPrevious.activeIndex)
    }

    @Test
    fun `activePendingFindSelection mirrors active search result`() {
        val segmentMode = SearchResultsMode.OriginalSegment(
            results = listOf(
                OriginalSegmentFindResult(3, 2, 8, 1, "match", 40)
            ),
            activeIndex = 0
        )

        val selection = activePendingFindSelection(segmentMode)

        assertEquals(
            PendingFindSelection.OriginalSegment(segmentIndex = 3, start = 2, end = 8),
            selection
        )
    }
}
