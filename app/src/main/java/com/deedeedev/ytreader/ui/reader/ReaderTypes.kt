package com.deedeedev.ytreader.ui.reader

internal enum class ReaderMode {
    ORIGINAL,
    STUDY
}

internal sealed interface PendingAction {
    data object ExitScreen : PendingAction
    data object ExitEditing : PendingAction
    data class SwitchMode(val targetMode: ReaderMode) : PendingAction
    data object ShowWebViewEditUnavailable : PendingAction
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

internal sealed interface ReaderAnchor {
    data class Study(val anchorStart: Int) : ReaderAnchor
    data class OriginalSegment(val segmentIndex: Int) : ReaderAnchor
    data class OriginalFallback(val textOffset: Int) : ReaderAnchor
}

internal data class ReaderLocation(
    val subtitleId: Long,
    val anchor: ReaderAnchor
)

internal enum class ReaderJumpReason {
    SEARCH,
    ANNOTATION,
    INITIAL_TARGET,
    TIME_JUMP
}

internal data class JumpBackState(
    val origin: ReaderLocation,
    val reason: ReaderJumpReason,
    val label: String? = null
)

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

internal data class InteractiveReplaceState(
    val findText: String,
    val replaceText: String,
    val isCaseSensitive: Boolean,
    val totalReplacements: Int = 0
)

internal data class SearchInOriginalResult(
    val segmentIndex: Int,
    val startTime: Long,
    val excerpt: String,
    val matchStart: Int,
    val matchEnd: Int
)
