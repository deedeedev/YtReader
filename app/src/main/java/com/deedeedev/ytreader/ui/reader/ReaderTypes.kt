package com.deedeedev.ytreader.ui.reader

internal enum class ReaderMode {
    ORIGINAL,
    STUDY
}

internal sealed interface PendingAction {
    data object ExitScreen : PendingAction
    data object ExitEditing : PendingAction
    data class SwitchMode(val targetMode: ReaderMode) : PendingAction
}

internal data class SelectionRange(
    val start: Int,
    val end: Int
)

internal data class ReaderTapPosition(
    val xFraction: Float,
    val yFraction: Float
)

internal sealed interface PendingFindSelection {
    data class Study(val start: Int, val end: Int) : PendingFindSelection
    data class OriginalSegment(val segmentIndex: Int, val start: Int, val end: Int) : PendingFindSelection
    data class OriginalFallback(val start: Int, val end: Int) : PendingFindSelection
}

internal sealed interface SearchResultsMode {
    val activeIndex: Int
    val totalResults: Int

    data class Study(
        val results: List<ReaderFindResult>,
        override val activeIndex: Int
    ) : SearchResultsMode {
        override val totalResults: Int = results.size
    }

    data class OriginalFallback(
        val results: List<ReaderFindResult>,
        override val activeIndex: Int
    ) : SearchResultsMode {
        override val totalResults: Int = results.size
    }

    data class OriginalSegment(
        val results: List<OriginalSegmentFindResult>,
        override val activeIndex: Int
    ) : SearchResultsMode {
        override val totalResults: Int = results.size
    }
}
