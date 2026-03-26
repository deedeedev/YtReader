package com.deedeedev.ytreader.ui.reader

import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.data.local.HighlightNoteEntity
import com.deedeedev.ytreader.data.local.SubtitleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoNotesViewModelTest {

    @Test
    fun buildVideoAnnotationItems_ordersAnnotationsByTextProgress() {
        val subtitle = SubtitleEntity(
            id = 1L,
            videoId = "video-123",
            title = "Sample",
            languageCode = "en",
            content = "alpha beta gamma delta epsilon zeta eta theta iota kappa",
            studyContent = "alpha beta gamma delta epsilon zeta eta theta iota kappa",
            highlights = "30,35,BLUE|12,17,YELLOW|45,50,RED",
            createdAt = 100L
        )

        val note = HighlightNoteEntity(
            id = 10L,
            subtitleId = 1L,
            highlightStart = 30,
            highlightEnd = 35,
            noteText = "Keep this",
            updatedAt = 500L
        )

        val bookmark = BookmarkEntity(
            id = 20L,
            subtitleId = 1L,
            anchorStart = 5,
            title = "Bookmark early",
            updatedAt = 300L
        )

        val items = buildVideoAnnotationItems(
            subtitles = listOf(subtitle),
            notes = listOf(note),
            bookmarks = listOf(bookmark)
        )

        assertEquals(
            listOf(
                VideoAnnotationType.BOOKMARK,
                VideoAnnotationType.HIGHLIGHT,
                VideoAnnotationType.NOTE,
                VideoAnnotationType.HIGHLIGHT
            ),
            items.map { item -> item.type }
        )
        assertEquals(
            listOf(5, 12, 30, 45),
            items.map { item -> item.navigationTarget.bookmarkStart ?: item.navigationTarget.highlightStart }
        )
    }
}
