package com.deedeedev.ytreader.ui.reader

internal data class TextRange(
    val start: Int,
    val end: Int
)

internal fun findTokenRangeAtOffset(text: String, offset: Int): TextRange? {
    if (text.isEmpty() || offset !in text.indices) {
        return null
    }
    if (text[offset].isWhitespace()) {
        return null
    }

    var start = offset
    while (start > 0 && !text[start - 1].isWhitespace()) {
        start--
    }

    var end = offset + 1
    while (end < text.length && !text[end].isWhitespace()) {
        end++
    }

    return TextRange(start = start, end = end)
}

internal fun mergeSelectionRange(anchor: TextRange, target: TextRange): TextRange {
    return TextRange(
        start = minOf(anchor.start, target.start),
        end = maxOf(anchor.end, target.end)
    )
}

internal enum class SelectionHandle {
    START,
    END
}

internal data class HandleSelectionUpdate(
    val range: TextRange,
    val activeHandle: SelectionHandle
)

internal fun updateSelectionForHandleDrag(
    current: TextRange,
    target: TextRange,
    handle: SelectionHandle
): HandleSelectionUpdate {
    return when (handle) {
        SelectionHandle.START -> {
            if (target.start < current.end) {
                HandleSelectionUpdate(
                    range = TextRange(start = target.start, end = current.end),
                    activeHandle = SelectionHandle.START
                )
            } else {
                HandleSelectionUpdate(
                    range = TextRange(start = current.end, end = target.end),
                    activeHandle = SelectionHandle.END
                )
            }
        }

        SelectionHandle.END -> {
            if (target.end > current.start) {
                HandleSelectionUpdate(
                    range = TextRange(start = current.start, end = target.end),
                    activeHandle = SelectionHandle.END
                )
            } else {
                HandleSelectionUpdate(
                    range = TextRange(start = target.start, end = current.start),
                    activeHandle = SelectionHandle.START
                )
            }
        }
    }
}
