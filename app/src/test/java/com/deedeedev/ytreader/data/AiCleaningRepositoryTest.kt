package com.deedeedev.ytreader.data

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCleaningRepositoryTest {

    private val repository = AiCleaningRepository(OkHttpClient())

    @Test
    fun buildUserPrompt_includesInstructionsAndSubtitleText() {
        val prompt = repository.buildUserPrompt(
            userInstructions = "Preserve hesitations.",
            subtitleText = "this is a line\\nnext line"
        )

        assertTrue(prompt.contains("Preserve hesitations."))
        assertTrue(prompt.contains("<<SUBTITLE_TEXT>>"))
        assertTrue(prompt.contains("this is a line\\nnext line"))
        assertTrue(prompt.contains("Return only cleaned text"))
    }

    @Test
    fun parseCleanedText_returnsFirstChoiceMessageContent() {
        val response = """
            {
              "choices": [
                {
                  "message": {
                    "content": "Hello, world."
                  }
                }
              ]
            }
        """.trimIndent()

        val cleaned = repository.parseCleanedText(response)

        assertEquals("Hello, world.", cleaned)
    }

    @Test
    fun parseCleanedText_returnsNullForInvalidJson() {
        val cleaned = repository.parseCleanedText("not-json")
        assertNull(cleaned)
    }
}
