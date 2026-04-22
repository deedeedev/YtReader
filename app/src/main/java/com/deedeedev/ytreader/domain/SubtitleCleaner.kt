package com.deedeedev.ytreader.domain

data class LocalCleaningOptions(
    val normalizeUnicodeWhitespace: Boolean = true,
    val removeHtmlTags: Boolean = true,
    val removeAsdCcArtifacts: Boolean = true,
    val normalizeQuotationMarks: Boolean = true,
    val normalizeEllipsis: Boolean = true,
    val removeDuplicateSpaces: Boolean = true,
    val removeSpacesBeforePunctuation: Boolean = true,
    val trimLines: Boolean = true,
    val removeBlankLines: Boolean = true,
    val capitalizeFirstLetter: Boolean = true,
    val addSpaceAfterPunctuation: Boolean = true,
    val capitalizeAfterSentenceEnd: Boolean = true,
    val mergeShortFragments: Boolean = true,
    val removeMidSentenceLineBreaks: Boolean = true,
    val replaceLineBreaksWithSpace: Boolean = false
)

object SubtitleCleaner {

    fun clean(text: String, options: LocalCleaningOptions): String {
        if (text.isBlank()) return text

        var result = text

        if (options.normalizeUnicodeWhitespace) {
            result = normalizeUnicodeWhitespace(result)
        }
        if (options.removeHtmlTags) {
            result = removeHtmlTags(result)
        }
        if (options.removeAsdCcArtifacts) {
            result = removeAsdCcArtifacts(result)
        }
        if (options.normalizeQuotationMarks) {
            result = normalizeQuotationMarks(result)
        }
        if (options.normalizeEllipsis) {
            result = normalizeEllipsis(result)
        }
        if (options.mergeShortFragments) {
            result = mergeShortFragments(result)
        }
        if (options.removeMidSentenceLineBreaks) {
            result = removeMidSentenceLineBreaks(result)
        }
        if (options.replaceLineBreaksWithSpace) {
            result = replaceLineBreaksWithSpace(result)
        }
        if (options.removeBlankLines) {
            result = removeBlankLines(result)
        }
        if (options.trimLines) {
            result = trimLines(result)
        }
        if (options.removeDuplicateSpaces) {
            result = removeDuplicateSpaces(result)
        }
        if (options.removeSpacesBeforePunctuation) {
            result = removeSpacesBeforePunctuation(result)
        }
        if (options.addSpaceAfterPunctuation) {
            result = addSpaceAfterPunctuation(result)
        }
        if (options.capitalizeAfterSentenceEnd) {
            result = capitalizeAfterSentenceEnd(result)
        }
        if (options.capitalizeFirstLetter) {
            result = capitalizeFirstLetter(result)
        }

        return result
    }

    private fun normalizeUnicodeWhitespace(input: String): String {
        return input
            .replace('\u00A0', ' ')
            .replace('\u2007', ' ')
            .replace('\u200F', ' ')
            .replace('\t', ' ')
            .replace(Regex("[\\u202F\\u2060-\\u2069]"), "")
    }

    private fun removeHtmlTags(input: String): String {
        return input.replace(Regex("<[^>]+>"), "")
    }

    private fun removeAsdCcArtifacts(input: String): String {
        return input
            .replace(Regex("^>>>\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^>>\\s*", RegexOption.MULTILINE), "")
    }

    private fun normalizeQuotationMarks(input: String): String {
        return input
            .replace(Regex("[\\u201C\\u201D]"), "\"")
            .replace(Regex("[\\u2018\\u2019]"), "'")
            .replace(Regex("[\\u2010-\\u2015]"), "-")
    }

    private fun normalizeEllipsis(input: String): String {
        return input
            .replace(Regex("\\.{3,}"), "...")
            .replace(Regex("[\\u2026\\u2027]+"), "...")
    }

    private fun removeDuplicateSpaces(input: String): String {
        return input.replace(Regex("\\s{2,}"), " ")
    }

    private fun removeSpacesBeforePunctuation(input: String): String {
        return input.replace(Regex("\\s+([.,!?;:])"), "$1")
    }

    private fun trimLines(input: String): String {
        return input.lines()
            .joinToString("\n") { it.trim() }
    }

    private fun removeBlankLines(input: String): String {
        return input
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun capitalizeFirstLetter(input: String): String {
        if (input.isEmpty()) return input
        val firstChar = input.first()
        return if (firstChar.isLowerCase()) {
            firstChar.uppercaseChar() + input.substring(1)
        } else {
            input
        }
    }

    private fun addSpaceAfterPunctuation(input: String): String {
        return input.replace(Regex("([.!?])([A-Za-z])"), "$1 $2")
    }

    private fun capitalizeAfterSentenceEnd(input: String): String {
        return input.replace(Regex("([.!?])\\s+([a-z])")) { match ->
            match.groupValues[1] + " " + match.groupValues[2].uppercase()
        }
    }

    private fun removeMidSentenceLineBreaks(input: String): String {
        val lines = input.lines()
        if (lines.size <= 1) return input

        val resultLines = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val currentLine = lines[i]
            val nextLine = if (i + 1 < lines.size) lines[i + 1] else null

            if (nextLine != null && !endsWithSentenceEndingPunctuation(currentLine)) {
                val nextFirstChar = nextLine.firstOrNull() ?: continue
                if (!nextFirstChar.isUpperCase() || currentLine.endsWith(" ") || currentLine.endsWith("-")) {
                    resultLines.add(currentLine + " " + nextLine)
                    i += 2
                    continue
                }
            }
            resultLines.add(currentLine)
            i++
        }
        return resultLines.joinToString("\n")
    }

    private fun replaceLineBreaksWithSpace(input: String): String {
        return input.replace(Regex("\\s+"), " ").trim()
    }

    private fun mergeShortFragments(input: String): String {
        val lines = input.lines()
        if (lines.size <= 1) return input

        val resultLines = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val currentLine = lines[i].trim()
            if (currentLine.isEmpty()) {
                i++
                continue
            }
            val nextLine = if (i + 1 < lines.size) lines[i + 1].trim() else null

            if (nextLine != null && !endsWithSentenceEndingPunctuation(currentLine) && !startsWithNewSentence(nextLine)) {
                resultLines.add(currentLine + " " + nextLine)
                i += 2
                continue
            }
            resultLines.add(currentLine)
            i++
        }
        return resultLines.joinToString("\n")
    }

    private fun endsWithSentenceEndingPunctuation(text: String): Boolean {
        return text.trimEnd().endsWithAny('.', '!', '?', ':', ';')
    }

    private fun startsWithNewSentence(text: String): Boolean {
        val firstChar = text.firstOrNull() ?: return false
        return firstChar.isUpperCase()
    }

    private fun String.endsWithAny(vararg chars: Char): Boolean {
        if (this.isEmpty()) return false
        return this.last() in chars
    }
}