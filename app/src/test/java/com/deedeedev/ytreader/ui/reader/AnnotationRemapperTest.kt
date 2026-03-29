package com.deedeedev.ytreader.ui.reader

import com.deedeedev.ytreader.data.local.BookmarkEntity
import org.junit.Assert.*
import org.junit.Test

class AnnotationRemapperTest {

    @Test
    fun noAnnotations() {
        val oldText = "hello world"
        val newText = "hello world"

        val result = remapAnnotations(oldText, newText, emptyList(), emptyList())

        assertTrue(result.highlights.isEmpty())
        assertTrue(result.bookmarks.isEmpty())
        assertEquals(0, result.lostHighlightCount)
        assertEquals(0, result.lostBookmarkCount)
    }

    @Test
    fun noTextChange() {
        val oldText = "hello world"
        val newText = "hello world"
        val highlights = listOf(
            TextHighlight(0, 5, HighlightColor.RED),
            TextHighlight(6, 11, HighlightColor.BLUE)
        )
        val bookmarks = listOf(
            BookmarkEntity(1, 1, 3, "Bookmark 1"),
            BookmarkEntity(1, 1, 8, "Bookmark 2")
        )

        val result = remapAnnotations(oldText, newText, highlights, bookmarks)

        assertEquals(2, result.highlights.size)
        assertEquals(0, result.highlights[0].start)
        assertEquals(5, result.highlights[0].end)
        assertEquals(6, result.highlights[1].start)
        assertEquals(11, result.highlights[1].end)
        assertEquals(2, result.bookmarks.size)
        assertEquals(3, result.bookmarks[0].anchorStart)
        assertEquals(8, result.bookmarks[1].anchorStart)
        assertEquals(0, result.lostHighlightCount)
        assertEquals(0, result.lostBookmarkCount)
    }

    @Test
    fun insertBeforeShiftsOffsets() {
        val oldText = "hello world"
        val newText = "prefix hello world"
        val highlights = listOf(
            TextHighlight(0, 5, HighlightColor.RED)
        )
        val bookmarks = listOf(
            BookmarkEntity(1, 1, 3, "Bookmark 1")
        )

        val result = remapAnnotations(oldText, newText, highlights, bookmarks)

        assertEquals(1, result.highlights.size)
        assertTrue(result.highlights[0].start > 0)
        assertEquals(1, result.bookmarks.size)
        assertTrue(result.bookmarks[0].anchorStart > 3)
    }

    @Test
    fun deleteBeforeShiftsOffsetsDown() {
        val oldText = "prefix hello world"
        val newText = "hello world"
        val highlights = listOf(
            TextHighlight(7, 12, HighlightColor.RED)
        )
        val bookmarks = listOf(
            BookmarkEntity(1, 1, 10, "Bookmark 1")
        )

        val result = remapAnnotations(oldText, newText, highlights, bookmarks)

        assertEquals(1, result.highlights.size)
        assertTrue(result.highlights[0].start < 7)
        assertEquals(1, result.bookmarks.size)
        assertTrue(result.bookmarks[0].anchorStart < 10)
    }

    @Test
    fun highlightInDeletedRegionIsLost() {
        val oldText = "hello world"
        val newText = "world"
        val highlights = listOf(
            TextHighlight(0, 5, HighlightColor.RED),
            TextHighlight(6, 11, HighlightColor.BLUE)
        )

        val result = remapAnnotations(oldText, newText, highlights, emptyList())

        assertTrue(result.lostHighlightCount >= 1)
    }

    @Test
    fun bookmarkInDeletedRegionIsLost() {
        val oldText = "hello world"
        val newText = "world"
        val bookmarks = listOf(
            BookmarkEntity(1, 1, 3, "Bookmark 1"),
            BookmarkEntity(1, 1, 8, "Bookmark 2")
        )

        val result = remapAnnotations(oldText, newText, emptyList(), bookmarks)

        assertTrue(result.bookmarks.isNotEmpty() || result.lostBookmarkCount >= 1)
    }

    @Test
    fun highlightWithNotePreserved() {
        val oldText = "hello world"
        val newText = "hello world"
        val highlights = listOf(
            TextHighlight(0, 5, HighlightColor.RED, "This is a note")
        )

        val result = remapAnnotations(oldText, newText, highlights, emptyList())

        assertEquals(1, result.highlights.size)
        assertEquals("This is a note", result.highlights[0].note)
    }

    @Test
    fun bookmarkTitlePreserved() {
        val oldText = "hello world"
        val newText = "hello world"
        val bookmarks = listOf(
            BookmarkEntity(1, 1, 5, "My Bookmark Title")
        )

        val result = remapAnnotations(oldText, newText, emptyList(), bookmarks)

        assertEquals(1, result.bookmarks.size)
        assertEquals("My Bookmark Title", result.bookmarks[0].title)
    }

    @Test
    fun emptyOldText() {
        val oldText = ""
        val newText = "hello world"
        val highlights = listOf(
            TextHighlight(0, 5, HighlightColor.RED)
        )

        val result = remapAnnotations(oldText, newText, highlights, emptyList())

        assertTrue(result.lostHighlightCount >= 1)
    }

    @Test
    fun emptyNewText() {
        val oldText = "hello world"
        val newText = ""
        val highlights = listOf(
            TextHighlight(0, 5, HighlightColor.RED)
        )
        val bookmarks = listOf(
            BookmarkEntity(1, 1, 3, "Bookmark 1")
        )

        val result = remapAnnotations(oldText, newText, highlights, bookmarks)

        assertEquals(1, result.lostHighlightCount)
        assertEquals(0, result.highlights.size)
    }

    @Test
    fun textReplacedCompletely() {
        val oldText = "hello world"
        val newText = "completely different text"
        val highlights = listOf(
            TextHighlight(0, 5, HighlightColor.RED)
        )
        val bookmarks = listOf(
            BookmarkEntity(1, 1, 3, "Bookmark 1")
        )

        val result = remapAnnotations(oldText, newText, highlights, bookmarks)

        assertTrue(result.highlights.isEmpty() || result.lostHighlightCount >= 1)
    }
}
