package com.deedeedev.ytreader.ui.reader

import kotlin.math.ceil

internal data class PageProgress(
    val currentPage: Int,
    val totalPages: Int
)

internal fun scrollPercent(
    value: Int,
    maxValue: Int,
    canScrollForward: Boolean,
    canScrollBackward: Boolean
): Int {
    if (maxValue <= 0 || (!canScrollForward && !canScrollBackward)) {
        return 100
    }
    if (!canScrollBackward || value <= 0) {
        return 0
    }
    if (!canScrollForward || value >= maxValue) {
        return 100
    }
    return ((value.toFloat() / maxValue.toFloat()) * 100f)
        .toInt()
        .coerceIn(0, 99)
}

internal fun lazyListScrollPercent(
    firstVisibleItemIndex: Int,
    totalItems: Int,
    canScrollForward: Boolean,
    canScrollBackward: Boolean
): Int {
    if (totalItems <= 0 || (!canScrollForward && !canScrollBackward)) {
        return 100
    }
    if (!canScrollBackward || firstVisibleItemIndex <= 0) {
        return 0
    }
    if (!canScrollForward) {
        return 100
    }
    val maxIndex = (totalItems - 1).coerceAtLeast(1)
    return ((firstVisibleItemIndex.toFloat() / maxIndex.toFloat()) * 100f)
        .toInt()
        .coerceIn(0, 99)
}

internal fun pagedScrollProgress(
    value: Int,
    maxValue: Int,
    viewportHeightPx: Int,
    canScrollForward: Boolean = true
): PageProgress {
    if (viewportHeightPx <= 0) {
        return PageProgress(currentPage = 1, totalPages = 1)
    }
    val contentHeightPx = (maxValue + viewportHeightPx).coerceAtLeast(viewportHeightPx)
    val totalPages = ceil(contentHeightPx.toFloat() / viewportHeightPx.toFloat())
        .toInt()
        .coerceAtLeast(1)
    val currentPage = if (!canScrollForward) {
        totalPages
    } else {
        ((value.coerceAtLeast(0)) / viewportHeightPx) + 1
    }
    return PageProgress(
        currentPage = currentPage.coerceIn(1, totalPages),
        totalPages = totalPages
    )
}

internal fun lazyListPageProgress(
    firstVisibleItemIndex: Int,
    totalItems: Int,
    visibleItemsCount: Int,
    canScrollForward: Boolean
): PageProgress {
    if (totalItems <= 0) {
        return PageProgress(currentPage = 1, totalPages = 1)
    }
    val itemsPerPage = visibleItemsCount.coerceAtLeast(1)
    val totalPages = ceil(totalItems.toFloat() / itemsPerPage.toFloat())
        .toInt()
        .coerceAtLeast(1)
    val estimatedCurrentPage = (firstVisibleItemIndex.coerceAtLeast(0) / itemsPerPage) + 1
    val currentPage = if (!canScrollForward) {
        totalPages
    } else {
        estimatedCurrentPage.coerceIn(1, totalPages)
    }
    return PageProgress(currentPage = currentPage, totalPages = totalPages)
}
