package com.deedeedev.ytreader.domain

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.util.Locale

data class SubtitleSegment(
    val startTime: Long,
    val endTime: Long,
    val text: String
)

object SubtitleParser {

    fun toSrt(content: String): String {
        return when {
            content.trim().startsWith("<?xml") || content.contains("<tt") -> convertTtmlToSrt(content)
            content.contains("WEBVTT") -> convertWebVttToSrt(content)
            else -> content // Assume it's already SRT or plain text if unknown
        }
    }

    /**
     * Parses subtitle content into segments with timestamps.
     */
    fun parseToSegments(content: String): List<SubtitleSegment> {
        return when {
            content.contains("-->") -> parseSrtToSegments(content)
            content.trim().startsWith("<?xml") || content.contains("<tt") -> parseTtmlToSegments(content)
            else -> emptyList() // Or some default parsing
        }
    }

    private fun parseSrtToSegments(content: String): List<SubtitleSegment> {
        val segments = mutableListOf<SubtitleSegment>()
        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.matches(Regex("^\\d+$"))) {
                i++
                if (i < lines.size && lines[i].contains("-->")) {
                    val timeLine = lines[i]
                    val times = timeLine.split(" --> ")
                    if (times.size >= 2) {
                        val start = parseTime(times[0])
                        val end = parseTime(times[1])
                        i++
                        val textBuilder = StringBuilder()
                        while (i < lines.size && lines[i].isNotBlank()) {
                            textBuilder.append(lines[i]).append("\n")
                            i++
                        }
                        segments.add(SubtitleSegment(start, end, textBuilder.toString().trim()))
                    }
                }
            }
            i++
        }
        return segments
    }

    private fun parseTtmlToSegments(content: String): List<SubtitleSegment> {
        val doc: Document = Jsoup.parse(content, "", Parser.xmlParser())
        val paragraphs = doc.select("p")
        val segments = mutableListOf<SubtitleSegment>()

        for (p in paragraphs) {
            val begin = p.attr("begin").takeIf { it.isNotEmpty() } ?: p.attr("t")
            val end = p.attr("end").takeIf { it.isNotEmpty() }
            val duration = p.attr("d").takeIf { it.isNotEmpty() }
            val text = p.text()

            if (begin.isNotEmpty() && text.isNotEmpty()) {
                val startTime = parseTime(begin)
                val endTime = if (!end.isNullOrEmpty()) {
                    parseTime(end)
                } else if (!duration.isNullOrEmpty()) {
                    startTime + parseDuration(duration)
                } else {
                    startTime + 2000
                }
                segments.add(SubtitleSegment(startTime, endTime, text))
            }
        }
        return segments
    }

    /**
     * Extracts plain text from subtitle content (SRT, TTML, WebVTT).
     */
    fun parse(content: String): String {
        return when {
            content.contains("-->") -> {
                // Handle SRT / WebVTT (strip timestamps and indices)
                content.lines()
                    .filter { line ->
                        !line.contains("-->") && 
                        line.isNotBlank() && 
                        !line.trim().matches(Regex("^\\d+$")) &&
                        !line.startsWith("WEBVTT")
                    }
                    .joinToString("\n")
            }
            content.trim().startsWith("<?xml") || content.contains("<tt") -> {
                // Handle TTML (Timed Text Markup Language)
                val doc = Jsoup.parse(content, "", Parser.xmlParser())
                doc.select("p").eachText().joinToString("\n")
            }
            else -> {
                // Fallback: strip HTML tags
                Jsoup.parse(content).text()
            }
        }
    }

    private fun convertTtmlToSrt(ttmlContent: String): String {
        val doc: Document = Jsoup.parse(ttmlContent, "", Parser.xmlParser())
        val paragraphs = doc.select("p")
        val srtBuilder = StringBuilder()
        var index = 1

        for (p in paragraphs) {
            val begin = p.attr("begin").takeIf { it.isNotEmpty() } ?: p.attr("t")
            val end = p.attr("end").takeIf { it.isNotEmpty() }
            val duration = p.attr("d").takeIf { it.isNotEmpty() }
            val text = p.text()

            if (begin.isNotEmpty() && text.isNotEmpty()) {
                val startTime = parseTime(begin)
                val endTime = if (!end.isNullOrEmpty()) {
                    parseTime(end)
                } else if (!duration.isNullOrEmpty()) {
                    startTime + parseDuration(duration)
                } else {
                    startTime + 2000 // Default 2s duration if missing
                }

                srtBuilder.append(index).append("\n")
                srtBuilder.append(formatTime(startTime)).append(" --> ").append(formatTime(endTime)).append("\n")
                srtBuilder.append(text).append("\n\n")
                index++
            }
        }
        return srtBuilder.toString().trim()
    }

    private fun convertWebVttToSrt(vttContent: String): String {
        val lines = vttContent.lines()
        val srtBuilder = StringBuilder()
        var index = 1
        var isHeader = true

        for (line in lines) {
            if (isHeader) {
                if (line.trim().isEmpty()) isHeader = false
                continue
            }
            if (line.contains("-->")) {
                srtBuilder.append(index).append("\n")
                srtBuilder.append(line.replace(".", ",")).append("\n") // WebVTT uses . for ms, SRT uses ,
                index++
            } else if (line.isNotBlank()) {
                srtBuilder.append(line).append("\n")
            } else {
                srtBuilder.append("\n")
            }
        }
        return srtBuilder.toString().trim()
    }

    private fun parseTime(timeStr: String): Long {
        return try {
            if (timeStr.contains(":")) {
                val parts = timeStr.replace(",", ".").split(":", ".")
                val h = parts.getOrElse(0) { "0" }.toLong()
                val m = parts.getOrElse(1) { "0" }.toLong()
                val s = parts.getOrElse(2) { "0" }.toLong()
                val ms = parts.getOrElse(3) { "0" }.padEnd(3, '0').take(3).toLong()
                (h * 3600000) + (m * 60000) + (s * 1000) + ms
            } else {
                // Assume milliseconds if no colons
                timeStr.replace("ms", "").toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseDuration(durationStr: String): Long {
         return try {
            if (durationStr.contains(":")) {
                 parseTime(durationStr)
            } else {
                durationStr.replace("ms", "").toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun formatTime(millis: Long): String {
        val h = millis / 3600000
        val m = (millis % 3600000) / 60000
        val s = (millis % 60000) / 1000
        val ms = millis % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms)
    }
}
