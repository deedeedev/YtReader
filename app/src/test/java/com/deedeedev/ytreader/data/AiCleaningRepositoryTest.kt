package com.deedeedev.ytreader.data

import android.content.Context
import android.content.res.Resources
import com.deedeedev.ytreader.R
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiCleaningRepositoryTest {

    private val repository = AiCleaningRepository(mockContext(), OkHttpClient())

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

    @Test
    fun buildEndpoint_appendsChatCompletionsForBaseUrl() {
        val endpoint = repository.buildEndpoint("https://api.example.com/v1")

        assertEquals("https://api.example.com/v1/chat/completions", endpoint)
    }

    @Test
    fun buildEndpoint_keepsFullChatCompletionsUrl() {
        val endpoint = repository.buildEndpoint("https://api.example.com/v1/chat/completions")

        assertEquals("https://api.example.com/v1/chat/completions", endpoint)
    }

    private fun mockContext(): Context {
        val context = mock<Context>()
        val resources = mock<Resources>()
        whenever(context.resources).thenReturn(resources)
        whenever(context.getString(eq(R.string.ai_cleaning_request_failed), any())).thenAnswer {
            "AI request failed (${it.arguments[1]})."
        }
        whenever(context.getString(R.string.ai_cleaning_missing_response_text))
            .thenReturn("AI response does not contain cleaned text.")
        whenever(resources.getString(R.string.ai_cleaning_timed_out))
            .thenReturn("AI cleaning timed out. Try shorter text or check endpoint/model.")
        whenever(resources.getString(R.string.ai_cleaning_failed)).thenReturn("AI cleaning failed.")
        whenever(resources.getString(R.string.ai_cleaning_no_additional_details))
            .thenReturn("No additional error details.")
        return context
    }
}
