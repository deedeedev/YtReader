package com.deedeedev.ytreader.ui.reader

import com.deedeedev.ytreader.data.local.BookmarkEntity

data class RemapResult(
    val highlights: List<TextHighlight>,
    val bookmarks: List<BookmarkEntity>,
    val lostHighlightCount: Int,
    val lostBookmarkCount: Int
)

fun remapAnnotations(
    oldText: String,
    newText: String,
    highlights: List<TextHighlight>,
    bookmarks: List<BookmarkEntity>
): RemapResult {
    val hunks = diff(oldText, newText)

    var lostHighlightCount = 0
    val remappedHighlights = highlights.mapNotNull { highlight ->
        val newRange = remapRange(hunks, highlight.start, highlight.end, oldText, newText)
        if (newRange != null) {
            highlight.copy(start = newRange.first, end = newRange.last + 1)
        } else {
            lostHighlightCount++
            null
        }
    }.sortedBy { it.start }

    var lostBookmarkCount = 0
    val remappedBookmarks = bookmarks.mapNotNull { bookmark ->
        val newOffset = remapPoint(hunks, bookmark.anchorStart, oldText.length)
        if (newOffset != null) {
            bookmark.copy(anchorStart = newOffset)
        } else {
            lostBookmarkCount++
            null
        }
    }.sortedBy { it.anchorStart }

    return RemapResult(
        highlights = remappedHighlights,
        bookmarks = remappedBookmarks,
        lostHighlightCount = lostHighlightCount,
        lostBookmarkCount = lostBookmarkCount
    )
}
