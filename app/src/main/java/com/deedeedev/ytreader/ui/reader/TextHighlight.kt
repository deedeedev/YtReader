package com.deedeedev.ytreader.ui.reader

enum class HighlightColor {
    RED,
    BLUE,
    GREEN,
    YELLOW
}

data class TextHighlight(
    val start: Int,
    val end: Int,
    val color: HighlightColor,
    val note: String? = null
)

data class HighlightMergeResult(
    val highlights: List<TextHighlight>,
    val mergedHighlight: TextHighlight,
    val replacedHighlights: List<TextHighlight>
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
    color: HighlightColor,
    note: String? = null
): HighlightMergeResult? {
    if (start >= end || start < 0) return null

    var mergedStart = start
    var mergedEnd = end
    val kept = mutableListOf<TextHighlight>()
    val replaced = mutableListOf<TextHighlight>()

    current.forEach { highlight ->
        val isOverlapping = highlight.start < mergedEnd && mergedStart < highlight.end
        if (isOverlapping) {
            mergedStart = minOf(mergedStart, highlight.start)
            mergedEnd = maxOf(mergedEnd, highlight.end)
            replaced += highlight
        } else {
            kept += highlight
        }
    }

    val mergedHighlight = TextHighlight(
        start = mergedStart,
        end = mergedEnd,
        color = color,
        note = mergeHighlightNotes(
            replaced + TextHighlight(start = start, end = end, color = color, note = note)
        )
    )
    kept += mergedHighlight

    return HighlightMergeResult(
        highlights = kept.sortedBy { it.start },
        mergedHighlight = mergedHighlight,
        replacedHighlights = replaced.sortedBy { it.start }
    )
}

fun findHighlightAtOffset(
    highlights: List<TextHighlight>,
    offset: Int
): TextHighlight? {
    if (offset < 0) return null
    return highlights.firstOrNull { offset in it.start until it.end }
}

fun recolorHighlight(
    highlights: List<TextHighlight>,
    target: TextHighlight,
    newColor: HighlightColor
): List<TextHighlight> {
    return highlights.map { highlight ->
        if (highlight == target) {
            highlight.copy(color = newColor)
        } else {
            highlight
        }
    }
}

fun deleteHighlightFromList(
    highlights: List<TextHighlight>,
    target: TextHighlight
): List<TextHighlight> {
    return highlights.filterNot { it == target }
}

fun normalizeHighlightNote(note: String?): String? {
    return note?.trim()?.takeIf { it.isNotEmpty() }
}

private fun mergeHighlightNotes(highlights: List<TextHighlight>): String? {
    return highlights
        .sortedWith(compareBy<TextHighlight> { it.start }.thenBy { it.end })
        .mapNotNull { normalizeHighlightNote(it.note) }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n\n")
}
