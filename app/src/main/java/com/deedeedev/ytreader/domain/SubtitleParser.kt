package com.deedeedev.ytreader.domain

import org.jsoup.Jsoup

object SubtitleParser {
    fun parse(content: String): String {
        if (content.trim().startsWith("<?xml") || content.contains("<tt")) {
            // Handle TTML (Timed Text Markup Language)
            val doc = Jsoup.parse(content)
            return doc.select("p").eachText().joinToString("\n")
        } else if (content.contains("WEBVTT")) {
            // Handle WebVTT
            return content.lines()
                .filter { !it.contains("-->") && it.isNotBlank() && !it.startsWith("WEBVTT") && !it.trim().matches(Regex("^\\d+$")) }
                .joinToString("\n")
        } else {
            // Fallback: strip HTML tags
            return Jsoup.parse(content).text()
        }
    }
}
