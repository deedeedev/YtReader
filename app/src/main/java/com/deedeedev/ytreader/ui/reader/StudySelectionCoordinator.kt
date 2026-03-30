package com.deedeedev.ytreader.ui.reader

internal class StudySelectionCoordinator {
    val textViews = mutableMapOf<Int, JustifiedStudyTextView>()
    var activeChunkIndex: Int? = null
    var onSelectionChangedListener: ((start: Int, end: Int) -> Unit)? = null
    var onHighlightTappedListener: ((TextHighlight?) -> Unit)? = null
    var onBookmarkTappedListener: ((com.deedeedev.ytreader.data.local.BookmarkEntity) -> Unit)? = null

    fun clearAllSelections() {
        textViews.values.forEach { it.clearSelection() }
        activeChunkIndex = null
    }

    fun registerTextView(chunkIndex: Int, textView: JustifiedStudyTextView) {
        textViews[chunkIndex] = textView
    }

    fun unregisterTextView(chunkIndex: Int) {
        textViews.remove(chunkIndex)
        if (activeChunkIndex == chunkIndex) {
            activeChunkIndex = null
        }
    }

    fun setSelectionForChunk(chunkIndex: Int, start: Int, end: Int, globalTextOffset: Int) {
        activeChunkIndex = chunkIndex
        val textView = textViews[chunkIndex] ?: return
        val localStart = (start - globalTextOffset).coerceIn(0, textView.contentLength)
        val localEnd = (end - globalTextOffset).coerceIn(0, textView.contentLength)
        textView.setSelectionRange(localStart, localEnd)
    }

    fun clearSelectionForChunk(chunkIndex: Int) {
        val textView = textViews[chunkIndex]
        textView?.clearSelection()
        if (activeChunkIndex == chunkIndex) {
            activeChunkIndex = null
        }
    }

    fun getTextViewForChunk(chunkIndex: Int): JustifiedStudyTextView? {
        return textViews[chunkIndex]
    }

    fun handleSelectionChanged(chunkIndex: Int, localStart: Int, localEnd: Int, globalTextOffset: Int) {
        activeChunkIndex = chunkIndex
        val globalStart = localStart + globalTextOffset
        val globalEnd = localEnd + globalTextOffset
        onSelectionChangedListener?.invoke(globalStart, globalEnd)
    }

    fun handleHighlightTapped(chunkIndex: Int, localOffset: Int, globalTextOffset: Int, highlights: List<TextHighlight>): TextHighlight? {
        val globalOffset = localOffset + globalTextOffset
        val tappedHighlight = highlights.find { globalOffset in it.start until it.end }
        onHighlightTappedListener?.invoke(tappedHighlight)
        return tappedHighlight
    }

    fun handleBookmarkTapped(chunkIndex: Int, bookmarks: List<com.deedeedev.ytreader.data.local.BookmarkEntity>): com.deedeedev.ytreader.data.local.BookmarkEntity? {
        val bookmark = bookmarks.firstOrNull()
        bookmark?.let { onBookmarkTappedListener?.invoke(it) }
        return bookmark
    }

    fun setupListeners(
        onSelectionChanged: (start: Int, end: Int) -> Unit,
        onHighlightTapped: (TextHighlight?) -> Unit,
        onBookmarkTapped: (com.deedeedev.ytreader.data.local.BookmarkEntity) -> Unit
    ) {
        onSelectionChangedListener = onSelectionChanged
        onHighlightTappedListener = onHighlightTapped
        onBookmarkTappedListener = onBookmarkTapped
    }

    fun clearListeners() {
        onSelectionChangedListener = null
        onHighlightTappedListener = null
        onBookmarkTappedListener = null
    }
}
