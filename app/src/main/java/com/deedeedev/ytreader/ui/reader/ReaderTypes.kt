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

internal sealed interface PendingFindSelection {
    data class Study(val start: Int, val end: Int) : PendingFindSelection
    data class OriginalSegment(val segmentIndex: Int, val start: Int, val end: Int) : PendingFindSelection
    data class OriginalFallback(val start: Int, val end: Int) : PendingFindSelection
}
