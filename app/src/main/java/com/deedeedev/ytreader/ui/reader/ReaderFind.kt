package com.deedeedev.ytreader.ui.reader

import com.deedeedev.ytreader.domain.SubtitleSegment

internal data class ReaderFindResult(
    val start: Int,
    val end: Int,
    val number: Int,
    val excerpt: String,
    val progressPercent: Int
)

internal data class OriginalSegmentFindResult(
    val segmentIndex: Int,
    val start: Int,
    val end: Int,
    val number: Int,
    val excerpt: String,
    val progressPercent: Int
)

private const val FIND_EXCERPT_RADIUS = 24

internal fun compileFindRegex(
    query: String,
    isCaseSensitive: Boolean = false
): Result<Regex> {
    return compileReaderRegex(query = query, isCaseSensitive = isCaseSensitive)
}

internal fun compileReaderRegex(
    query: String,
    isCaseSensitive: Boolean
): Result<Regex> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) {
        return Result.failure(IllegalArgumentException("Enter a regex to search."))
    }

    return try {
        val options = if (isCaseSensitive) {
            emptySet()
        } else {
            setOf(RegexOption.IGNORE_CASE)
        }
        Result.success(Regex(trimmedQuery, options))
    } catch (_: IllegalArgumentException) {
        Result.failure(IllegalArgumentException("Invalid regex."))
    }
}

internal fun replaceRegexMatches(
    text: String,
    query: String,
    replacement: String,
    isCaseSensitive: Boolean
): Result<String> {
    val regex = compileReaderRegex(query = query, isCaseSensitive = isCaseSensitive)
        .getOrElse { return Result.failure(it) }
    return Result.success(regex.replace(text, replacement))
}

internal fun findRegexMatches(
    text: String,
    regex: Regex
): List<ReaderFindResult> {
    if (text.isEmpty()) return emptyList()

    return regex.findAll(text)
        .filter { it.range.last >= it.range.first }
        .mapIndexed { index, match ->
            val start = match.range.first
            val end = match.range.last + 1
            ReaderFindResult(
                start = start,
                end = end,
                number = index + 1,
                excerpt = buildFindExcerpt(text, start, end),
                progressPercent = calculateFindProgressPercent(
                    matchStart = start,
                    textLength = text.length
                )
            )
        }
        .toList()
}

internal fun findRegexMatchesInSegments(
    segments: List<SubtitleSegment>,
    regex: Regex
): List<OriginalSegmentFindResult> {
    if (segments.isEmpty()) return emptyList()

    val totalLength = segments.sumOf { it.text.length }
    if (totalLength <= 0) return emptyList()

    var consumedLength = 0
    var resultNumber = 1
    val results = mutableListOf<OriginalSegmentFindResult>()

    segments.forEachIndexed { segmentIndex, segment ->
        regex.findAll(segment.text)
            .filter { it.range.last >= it.range.first }
            .forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                results += OriginalSegmentFindResult(
                    segmentIndex = segmentIndex,
                    start = start,
                    end = end,
                    number = resultNumber,
                    excerpt = buildFindExcerpt(segment.text, start, end),
                    progressPercent = calculateFindProgressPercent(
                        matchStart = consumedLength + start,
                        textLength = totalLength
                    )
                )
                resultNumber++
            }
        consumedLength += segment.text.length
    }

    return results
}

private fun buildFindExcerpt(
    text: String,
    start: Int,
    end: Int,
    radius: Int = FIND_EXCERPT_RADIUS
): String {
    if (text.isEmpty()) return ""

    val excerptStart = (start - radius).coerceAtLeast(0)
    val excerptEnd = (end + radius).coerceAtMost(text.length)
    val excerpt = text.substring(excerptStart, excerptEnd)
        .replace(Regex("\\s+"), " ")
        .trim()

    val prefix = if (excerptStart > 0) "..." else ""
    val suffix = if (excerptEnd < text.length) "..." else ""
    return prefix + excerpt + suffix
}

private fun calculateFindProgressPercent(
    matchStart: Int,
    textLength: Int
): Int {
    if (textLength <= 0) return 100
    if (matchStart <= 0) return 0
    if (matchStart >= textLength) return 100
    return ((matchStart.toFloat() / textLength.toFloat()) * 100f)
        .toInt()
        .coerceIn(0, 100)
}
