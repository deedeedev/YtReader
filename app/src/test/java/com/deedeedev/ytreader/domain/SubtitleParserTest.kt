package com.deedeedev.ytreader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleParserTest {

    @Test
    fun `parse TTML returns clean text`() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt>
                <body>
                    <p begin="00:00:00.000" end="00:00:01.000">Line 1</p>
                    <p begin="00:00:01.000" end="00:00:02.000">Line 2</p>
                </body>
            </tt>
        """.trimIndent()

        val result = SubtitleParser.parse(ttml)
        val expected = "Line 1\n\nLine 2"
        assertEquals(expected, result)
    }

    @Test
    fun `parse VTT returns clean text`() {
        val vtt = """
            WEBVTT

            00:00:00.000 --> 00:00:01.000
            Line 1

            00:00:01.000 --> 00:00:02.000
            Line 2
        """.trimIndent()

        val result = SubtitleParser.parse(vtt)
        val expected = "Line 1\n\nLine 2"
        assertEquals(expected, result)
    }

    @Test
    fun `parse simple text returns as is`() {
        val text = "Just some text"
        val result = SubtitleParser.parse(text)
        assertEquals(text, result)
    }
}
