package com.deedeedev.ytreader.ui.reader

enum class HighlightColor {
    RED,
    BLUE,
    GREEN
}

data class TextHighlight(
    val start: Int,
    val end: Int,
    val color: HighlightColor
)

private const val HIGHLIGHT_ENTRY_SEPARATOR = "|"
private const val HIGHLIGHT_FIELD_SEPARATOR = ","

fun serializeHighlights(highlights: List<TextHighlight>): String {
    if (highlights.isEmpty()) return ""
    return highlights.joinToString(HIGHLIGHT_ENTRY_SEPARATOR) {
        "${it.start}$HIGHLIGHT_FIELD_SEPARATOR${it.end}$HIGHLIGHT_FIELD_SEPARATOR${it.color.name}"
    }
}

fun parseHighlights(serialized: String): List<TextHighlight> {
    if (serialized.isBlank()) return emptyList()
    return serialized.split(HIGHLIGHT_ENTRY_SEPARATOR)
        .mapNotNull { entry ->
            val parts = entry.split(HIGHLIGHT_FIELD_SEPARATOR)
            if (parts.size != 3) return@mapNotNull null
            val start = parts[0].toIntOrNull() ?: return@mapNotNull null
            val end = parts[1].toIntOrNull() ?: return@mapNotNull null
            val color = runCatching { HighlightColor.valueOf(parts[2]) }.getOrNull() ?: return@mapNotNull null
            if (start < 0 || end <= start) return@mapNotNull null
            TextHighlight(start = start, end = end, color = color)
        }
        .sortedBy { it.start }
}

fun mergeHighlight(
    current: List<TextHighlight>,
    start: Int,
    end: Int,
    color: HighlightColor
): List<TextHighlight> {
    if (start >= end || start < 0) return current.sortedBy { it.start }

    var mergedStart = start
    var mergedEnd = end
    val kept = mutableListOf<TextHighlight>()

    current.forEach { highlight ->
        val isOverlapping = highlight.start < mergedEnd && mergedStart < highlight.end
        if (isOverlapping) {
            mergedStart = minOf(mergedStart, highlight.start)
            mergedEnd = maxOf(mergedEnd, highlight.end)
        } else {
            kept += highlight
        }
    }

    kept += TextHighlight(start = mergedStart, end = mergedEnd, color = color)
    return kept.sortedBy { it.start }
}
