package com.deedeedev.ytreader.ui.reader

import com.deedeedev.ytreader.domain.SubtitleSegment

internal data class ReaderFindSearchOutcome(
    val findResults: List<ReaderFindResult> = emptyList(),
    val originalSegmentFindResults: List<OriginalSegmentFindResult> = emptyList(),
    val errorMessage: String? = null
)

internal fun executeReaderFindSearch(
    query: String,
    isCaseSensitive: Boolean,
    isOriginalMode: Boolean,
    sourceText: String,
    originalSegments: List<SubtitleSegment>,
    originalFallbackText: String,
    emptyQueryMessage: String,
    invalidRegexMessage: String,
    excerptEllipsis: String
): ReaderFindSearchOutcome {
    val regex = compileFindRegex(
        query = query,
        isCaseSensitive = isCaseSensitive,
        emptyQueryMessage = emptyQueryMessage,
        invalidRegexMessage = invalidRegexMessage
    ).getOrElse { error ->
        return ReaderFindSearchOutcome(errorMessage = error.message ?: invalidRegexMessage)
    }

    if (!isOriginalMode) {
        return ReaderFindSearchOutcome(
            findResults = findRegexMatches(sourceText, regex, excerptEllipsis)
        )
    }

    if (originalSegments.isEmpty()) {
        return ReaderFindSearchOutcome(
            findResults = findRegexMatches(originalFallbackText, regex, excerptEllipsis)
        )
    }

    return ReaderFindSearchOutcome(
        originalSegmentFindResults = findRegexMatchesInSegments(originalSegments, regex, excerptEllipsis)
    )
}

internal fun canNavigateToPreviousSearchResult(searchResultsMode: SearchResultsMode?): Boolean {
    return searchResultsMode != null && searchResultsMode.activeIndex > 0
}

internal fun canNavigateToNextSearchResult(searchResultsMode: SearchResultsMode?): Boolean {
    return searchResultsMode != null && searchResultsMode.activeIndex < searchResultsMode.totalResults - 1
}

internal fun moveToPreviousSearchResult(searchResultsMode: SearchResultsMode): SearchResultsMode {
    return withUpdatedSearchResultIndex(searchResultsMode, searchResultsMode.activeIndex - 1)
}

internal fun moveToNextSearchResult(searchResultsMode: SearchResultsMode): SearchResultsMode {
    return withUpdatedSearchResultIndex(searchResultsMode, searchResultsMode.activeIndex + 1)
}

internal fun activePendingFindSelection(searchResultsMode: SearchResultsMode?): PendingFindSelection? {
    return when (searchResultsMode) {
        is SearchResultsMode.Study -> {
            val result = searchResultsMode.results.getOrNull(searchResultsMode.activeIndex) ?: return null
            PendingFindSelection.Study(start = result.start, end = result.end)
        }

        is SearchResultsMode.OriginalFallback -> {
            val result = searchResultsMode.results.getOrNull(searchResultsMode.activeIndex) ?: return null
            PendingFindSelection.OriginalFallback(start = result.start, end = result.end)
        }

        is SearchResultsMode.OriginalSegment -> {
            val result = searchResultsMode.results.getOrNull(searchResultsMode.activeIndex) ?: return null
            PendingFindSelection.OriginalSegment(
                segmentIndex = result.segmentIndex,
                start = result.start,
                end = result.end
            )
        }

        null -> null
    }
}

private fun withUpdatedSearchResultIndex(
    searchResultsMode: SearchResultsMode,
    requestedIndex: Int
): SearchResultsMode {
    val safeIndex = requestedIndex.coerceIn(0, (searchResultsMode.totalResults - 1).coerceAtLeast(0))
    return when (searchResultsMode) {
        is SearchResultsMode.Study -> searchResultsMode.copy(activeIndex = safeIndex)
        is SearchResultsMode.OriginalFallback -> searchResultsMode.copy(activeIndex = safeIndex)
        is SearchResultsMode.OriginalSegment -> searchResultsMode.copy(activeIndex = safeIndex)
    }
}
