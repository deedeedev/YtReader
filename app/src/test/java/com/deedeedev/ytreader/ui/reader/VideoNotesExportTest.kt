package com.deedeedev.ytreader.ui.reader

import android.content.Context
import android.content.res.Resources
import com.deedeedev.ytreader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
        val context = mockVideoNotesContext()
        val markdown = buildVideoNotesMarkdown(
            context = context,
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

    private fun mockVideoNotesContext(): Context {
        val context = mock<Context>()
        val resources = mock<Resources>()
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getString(R.string.video_notes_type_bookmark)).thenReturn("Bookmark")
        whenever(resources.getString(R.string.video_notes_type_note)).thenReturn("Note")
        whenever(resources.getString(R.string.video_notes_type_highlight)).thenReturn("Highlight")
        whenever(resources.getString(R.string.video_notes_filter_all)).thenReturn("All")
        whenever(resources.getString(R.string.video_notes_filter_bookmarks)).thenReturn("Bookmarks")
        whenever(resources.getString(R.string.video_notes_filter_highlights)).thenReturn("Highlights")
        whenever(resources.getString(R.string.video_notes_filter_notes)).thenReturn("Notes")
        whenever(resources.getString(eq(R.string.video_notes_export_video_id_label), any())).thenAnswer {
            "Video ID: ${it.arguments[1]}"
        }
        whenever(resources.getString(eq(R.string.video_notes_export_filter_label), any())).thenAnswer {
            "Filter: ${it.arguments[1]}"
        }
        whenever(resources.getString(eq(R.string.video_notes_export_items_label), any())).thenAnswer {
            "Exported items: ${it.arguments[1]}"
        }
        whenever(resources.getString(eq(R.string.video_notes_export_item_type_format), any(), any())).thenAnswer {
            "${it.arguments[1]}. ${it.arguments[2]}"
        }
        whenever(resources.getString(eq(R.string.video_notes_export_title_label), any())).thenAnswer {
            "Title: ${it.arguments[1]}"
        }
        whenever(resources.getString(eq(R.string.video_notes_export_updated_label), any())).thenAnswer {
            "Updated: ${it.arguments[1]}"
        }
        whenever(resources.getString(eq(R.string.video_notes_export_position_label), any())).thenAnswer {
            "Position: ${it.arguments[1]}%"
        }
        whenever(resources.getString(eq(R.string.video_notes_export_note_label), any())).thenAnswer {
            "Note: ${it.arguments[1]}"
        }
        return context
    }
}
