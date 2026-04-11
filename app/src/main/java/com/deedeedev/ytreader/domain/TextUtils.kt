package com.deedeedev.ytreader.domain

fun lineTextAtOffset(text: String, offset: Int): String {
    if (text.isEmpty()) return ""
    val safeOffset = offset.coerceIn(0, text.lastIndex)
    val lineStart = text.lastIndexOf('\n', startIndex = safeOffset)
        .let { if (it == -1) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', startIndex = safeOffset)
        .let { if (it == -1) text.length else it }
    return text.substring(lineStart, lineEnd)
        .replace(Regex("\\s+"), " ")
        .trim()
}
