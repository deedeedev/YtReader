package com.deedeedev.ytreader.ui.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.text.StaticLayout

internal class StudyTextSelectionController {

    var selectionRange: TextRange? = null
        private set
    var selectionAnchor: TextRange? = null
        private set
    var isSelecting: Boolean = false
        private set
    var activeHandle: SelectionHandle? = null
        internal set
    var hadActiveSelectionOnDown: Boolean = false

    fun beginSelection(
        content: String,
        offset: Int,
        onRequestDisallowIntercept: (Boolean) -> Unit,
        onSelectionChanged: (start: Int, end: Int) -> Unit
    ) {
        val anchorRange = findTokenRangeAtOffset(content, offset) ?: return
        isSelecting = true
        activeHandle = null
        selectionAnchor = anchorRange
        updateSelectionRange(anchorRange, content, onSelectionChanged)
        onRequestDisallowIntercept(true)
    }

    fun updateSelection(
        content: String,
        offset: Int,
        onRequestDisallowIntercept: (Boolean) -> Unit,
        onSelectionChanged: (start: Int, end: Int) -> Unit
    ) {
        val anchor = selectionAnchor ?: return
        val targetRange = findTokenRangeAtOffset(content, offset) ?: return
        updateSelectionRange(
            mergeSelectionRange(anchor, targetRange),
            content,
            onSelectionChanged
        )
    }

    fun updateSelectionFromHandle(
        content: String,
        offset: Int,
        onSelectionChanged: (start: Int, end: Int) -> Unit
    ) {
        val handle = activeHandle ?: return
        val currentRange = selectionRange ?: return
        val targetRange = findTokenRangeAtOffset(content, offset) ?: return
        val updated = updateSelectionForHandleDrag(currentRange, targetRange, handle)
        activeHandle = updated.activeHandle
        updateSelectionRange(updated.range, content, onSelectionChanged)
    }

    fun finishSelection(onRequestDisallowIntercept: (Boolean) -> Unit) {
        isSelecting = false
        selectionAnchor = null
        onRequestDisallowIntercept(false)
    }

    fun finishHandleDrag(onRequestDisallowIntercept: (Boolean) -> Unit) {
        activeHandle = null
        onRequestDisallowIntercept(false)
    }

    fun clearSelection(onSelectionChanged: (start: Int, end: Int) -> Unit) {
        selectionRange = null
        selectionAnchor = null
        activeHandle = null
        onSelectionChanged(0, 0)
    }

    fun setSelectionRange(start: Int, end: Int, content: String, onSelectionChanged: (start: Int, end: Int) -> Unit) {
        if (content.isEmpty()) {
            clearSelection(onSelectionChanged)
            return
        }
        val boundedStart = start.coerceIn(0, content.length)
        val boundedEnd = end.coerceIn(0, content.length)
        val normalizedStart = minOf(boundedStart, boundedEnd)
        val normalizedEnd = maxOf(boundedStart, boundedEnd)
        if (normalizedEnd <= normalizedStart) {
            clearSelection(onSelectionChanged)
            return
        }
        selectionAnchor = null
        activeHandle = null
        updateSelectionRange(TextRange(start = normalizedStart, end = normalizedEnd), content, onSelectionChanged)
    }

    fun hasActiveSelection(): Boolean {
        val range = selectionRange
        return range != null && range.end > range.start
    }

    fun selectedText(content: String): String? {
        val range = selectionRange ?: return null
        if (range.end <= range.start) return null
        return content.substring(range.start, range.end)
    }

    fun hitHandleAt(
        localX: Float,
        localY: Float,
        textLayout: StaticLayout,
        content: String,
        handleHitRadius: Float,
        handleStemHeight: Float,
        handleRadius: Float,
        dpToPx: (Float) -> Float
    ): SelectionHandle? {
        return selectionHandleVisuals(textLayout, content, handleStemHeight, handleRadius)
            .firstOrNull { handle ->
                val circleCenterY = handle.anchorY + handleStemHeight + handleRadius
                val dx = localX - handle.centerX
                val dy = localY - circleCenterY
                val stemHit = localX in (handle.centerX - handleHitRadius)..(handle.centerX + handleHitRadius) &&
                    localY in handle.anchorY..(circleCenterY + handleHitRadius)
                stemHit || (dx * dx + dy * dy) <= (handleHitRadius * handleHitRadius)
            }
            ?.handle
    }

    fun selectionHandleVisuals(
        textLayout: StaticLayout,
        content: String,
        handleStemHeight: Float,
        handleRadius: Float
    ): List<SelectionHandleVisual> {
        val range = selectionRange ?: return emptyList()
        if (content.isEmpty()) return emptyList()
        return listOfNotNull(
            handleVisualForOffset(range.start, SelectionHandle.START, isRangeEnd = false, textLayout = textLayout, content = content),
            handleVisualForOffset(range.end, SelectionHandle.END, isRangeEnd = true, textLayout = textLayout, content = content)
        )
    }

    fun extraHandleBottomInset(handleStemHeight: Float, handleRadius: Float): Float {
        return if (selectionRange == null) 0f else handleStemHeight + (handleRadius * 2f)
    }

    fun adjustForContentChange(content: String) {
        selectionRange = selectionRange?.let { range ->
            val start = range.start.coerceIn(0, content.length)
            val end = range.end.coerceIn(0, content.length)
            if (end > start) TextRange(start, end) else null
        }
    }

    private fun updateSelectionRange(
        range: TextRange,
        content: String,
        onSelectionChanged: (start: Int, end: Int) -> Unit
    ) {
        if (range.end <= range.start) {
            clearSelection(onSelectionChanged)
            return
        }
        selectionRange = range
        onSelectionChanged(range.start, range.end)
    }

    private fun handleVisualForOffset(
        offset: Int,
        handle: SelectionHandle,
        isRangeEnd: Boolean,
        textLayout: StaticLayout,
        content: String
    ): SelectionHandleVisual? {
        if (content.isEmpty()) return null
        val lineIndex = when {
            content.length == 1 -> 0
            isRangeEnd && offset >= content.length -> textLayout.getLineForOffset(content.length - 1)
            else -> textLayout.getLineForOffset(offset.coerceIn(0, content.length - 1))
        }
        val horizontal = textLayout.getPrimaryHorizontal(offset.coerceIn(0, content.length))
        return SelectionHandleVisual(
            handle = handle,
            centerX = horizontal,
            anchorY = textLayout.getLineBottom(lineIndex).toFloat()
        )
    }

    internal data class SelectionHandleVisual(
        val handle: SelectionHandle,
        val centerX: Float,
        val anchorY: Float
    )
}
