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
    originalFallbackText: String
): ReaderFindSearchOutcome {
    val regex = compileFindRegex(
        query = query,
        isCaseSensitive = isCaseSensitive
    ).getOrElse { error ->
        return ReaderFindSearchOutcome(errorMessage = error.message ?: "Invalid regex.")
    }

    if (!isOriginalMode) {
        return ReaderFindSearchOutcome(
            findResults = findRegexMatches(sourceText, regex)
        )
    }

    if (originalSegments.isEmpty()) {
        return ReaderFindSearchOutcome(
            findResults = findRegexMatches(originalFallbackText, regex)
        )
    }

    return ReaderFindSearchOutcome(
        originalSegmentFindResults = findRegexMatchesInSegments(originalSegments, regex)
    )
}
