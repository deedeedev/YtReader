package com.deedeedev.ytreader.ui.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.StaticLayout
import com.deedeedev.ytreader.data.local.BookmarkEntity

internal class StudyTextBookmarkRenderer {

    private val bookmarkPath = Path()
    private var cachedBookmarkBounds: List<BookmarkMarkerBounds>? = null
    private var cachedBookmarkBoundsKey: String = ""

    fun drawBookmarkIndicators(
        canvas: Canvas,
        textLayout: StaticLayout,
        content: String,
        bookmarks: List<BookmarkEntity>,
        bookmarkPaint: Paint,
        bookmarkStrokePaint: Paint,
        visTop: Int,
        visBottom: Int,
        dpToPx: (Float) -> Float
    ) {
        if (content.isEmpty() || bookmarks.isEmpty()) return
        val visLineStart = textLayout.getLineForVertical(visTop.coerceAtLeast(0))
        val visLineEnd = textLayout.getLineForVertical(visBottom.coerceAtLeast(0))
        bookmarkMarkerBounds(textLayout, content, bookmarks, dpToPx).forEach { marker ->
            val line = textLayout.getLineForOffset(marker.bookmark.anchorStart.coerceIn(0, content.lastIndex))
            if (line > visLineEnd || line < visLineStart) return@forEach
            val centerX = marker.left + (bookmarkMarkerWidthPx(dpToPx) / 2f)
            bookmarkPath.reset()
            bookmarkPath.moveTo(marker.left, marker.top)
            bookmarkPath.lineTo(marker.right, marker.top)
            bookmarkPath.lineTo(marker.right, marker.bottom)
            bookmarkPath.lineTo(centerX, marker.bottom - bookmarkMarkerNotchDepthPx(dpToPx))
            bookmarkPath.lineTo(marker.left, marker.bottom)
            bookmarkPath.close()
            canvas.drawPath(bookmarkPath, bookmarkPaint)
            canvas.drawPath(bookmarkPath, bookmarkStrokePaint)
        }
    }

    fun findBookmarkAt(
        localX: Float,
        localY: Float,
        textLayout: StaticLayout,
        content: String,
        bookmarks: List<BookmarkEntity>,
        dpToPx: (Float) -> Float
    ): BookmarkEntity? {
        if (content.isEmpty() || bookmarks.isEmpty()) return null
        return bookmarkMarkerBounds(textLayout, content, bookmarks, dpToPx)
            .lastOrNull { marker ->
                localX in marker.left..marker.right && localY in marker.top..marker.bottom
            }
            ?.bookmark
    }

    fun invalidateBookmarkBoundsCache() {
        cachedBookmarkBounds = null
        cachedBookmarkBoundsKey = ""
    }

    private fun bookmarkMarkerBounds(
        textLayout: StaticLayout,
        content: String,
        bookmarks: List<BookmarkEntity>,
        dpToPx: (Float) -> Float
    ): List<BookmarkMarkerBounds> {
        if (content.isEmpty() || bookmarks.isEmpty()) return emptyList()
        val key = "${bookmarks.size}_${bookmarks.hashCode()}_${textLayout.width}"
        if (cachedBookmarkBounds != null && cachedBookmarkBoundsKey == key) {
            return cachedBookmarkBounds!!
        }
        val markerWidth = bookmarkMarkerWidthPx(dpToPx)
        val markerHeight = bookmarkMarkerHeightPx(dpToPx)
        val topInset = bookmarkMarkerTopInsetPx(dpToPx)
        val endInset = bookmarkMarkerEndInsetPx(dpToPx)
        val result = bookmarks.map { bookmark ->
            val line = textLayout.getLineForOffset(bookmark.anchorStart.coerceIn(0, content.lastIndex))
            val top = (textLayout.getLineTop(line) + topInset)
                .coerceAtMost(textLayout.getLineBottom(line) - markerHeight)
            val left = (textLayout.width - markerWidth - endInset).coerceAtLeast(0f)
            BookmarkMarkerBounds(
                bookmark = bookmark,
                left = left,
                top = top,
                right = left + markerWidth,
                bottom = top + markerHeight
            )
        }
        cachedBookmarkBounds = result
        cachedBookmarkBoundsKey = key
        return result
    }

    private fun bookmarkMarkerWidthPx(dpToPx: (Float) -> Float): Float = dpToPx(10f)
    private fun bookmarkMarkerHeightPx(dpToPx: (Float) -> Float): Float = dpToPx(14f)
    private fun bookmarkMarkerTopInsetPx(dpToPx: (Float) -> Float): Float = dpToPx(2f)
    private fun bookmarkMarkerEndInsetPx(dpToPx: (Float) -> Float): Float = dpToPx(2f)
    private fun bookmarkMarkerNotchDepthPx(dpToPx: (Float) -> Float): Float = dpToPx(4f)

    internal data class BookmarkMarkerBounds(
        val bookmark: BookmarkEntity,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}
