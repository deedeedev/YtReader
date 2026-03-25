package com.deedeedev.ytreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNotesExportTest {

    @Test
    fun buildVideoNotesExportFileName_fallsBackToVideoIdAndSanitizesUnsafeCharacters() {
        val fileName = buildVideoNotesExportFileName(
            title = "Lesson: Intro/Overview?",
            videoId = "video-123",
            extension = ".pdf"
        )

        assertEquals("Lesson-Intro-Overview.pdf", fileName)
    }

    @Test
    fun buildVideoNotesMarkdown_usesVisibleItemsAndSelectedFilterLabel() {
        val markdown = buildVideoNotesMarkdown(
            title = "Sample lesson",
            videoId = "video-123",
            selectedTypes = setOf(VideoAnnotationType.NOTE),
            items = listOf(
                VideoAnnotationItem(
                    key = "note-1",
                    type = VideoAnnotationType.NOTE,
                    navigationTarget = ReaderAnnotationTarget(subtitleId = 1L, highlightStart = 2, highlightEnd = 8),
                    title = "Important concept",
                    note = "Remember this\nfor later",
                    updatedAt = 0L,
                    progressPercent = 42
                )
            )
        )

        assertTrue(markdown.contains("# Sample lesson"))
        assertTrue(markdown.contains("- Filter: Notes"))
        assertTrue(markdown.contains("- Exported items: 1"))
        assertTrue(markdown.contains("## 1. Note"))
        assertTrue(markdown.contains("- Note: Remember this for later"))
    }
}
