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

    @Test
    fun computeReadTimeout_returnsBaseForShortText() {
        assertEquals(
            AiCleaningRepository.BASE_READ_TIMEOUT_SECONDS,
            AiCleaningRepository.computeReadTimeout(100)
        )
    }

    @Test
    fun computeReadTimeout_returnsBaseAtExactThreshold() {
        assertEquals(
            AiCleaningRepository.BASE_READ_TIMEOUT_SECONDS,
            AiCleaningRepository.computeReadTimeout(AiCleaningRepository.BASE_THRESHOLD_CHARS)
        )
    }

    @Test
    fun computeReadTimeout_addsOneChunkAboveThreshold() {
        val chars = AiCleaningRepository.BASE_THRESHOLD_CHARS + 1
        val expected = AiCleaningRepository.BASE_READ_TIMEOUT_SECONDS +
                AiCleaningRepository.EXTRA_TIMEOUT_SECONDS
        assertEquals(expected, AiCleaningRepository.computeReadTimeout(chars))
    }

    @Test
    fun computeReadTimeout_addsMultipleChunks() {
        val chars = AiCleaningRepository.BASE_THRESHOLD_CHARS +
                AiCleaningRepository.EXTRA_TIMEOUT_CHUNK_CHARS * 3
        val expected = AiCleaningRepository.BASE_READ_TIMEOUT_SECONDS +
                AiCleaningRepository.EXTRA_TIMEOUT_SECONDS * 3
        assertEquals(expected, AiCleaningRepository.computeReadTimeout(chars))
    }

    @Test
    fun computeReadTimeout_capsAtMaximum() {
        val veryLong = 1_000_000
        assertEquals(
            AiCleaningRepository.MAX_READ_TIMEOUT_SECONDS,
            AiCleaningRepository.computeReadTimeout(veryLong)
        )
    }

    @Test
    fun computeCallTimeout_includesReadPlusConnectPlusBuffer() {
        val textLength = 500
        val expected = AiCleaningRepository.computeReadTimeout(textLength) +
                AiCleaningRepository.CONNECT_TIMEOUT_SECONDS +
                AiCleaningRepository.CALL_BUFFER_SECONDS
        assertEquals(expected, AiCleaningRepository.computeCallTimeout(textLength))
    }

    @Test
    fun computeCallTimeout_scalesWithLongText() {
        val shortLength = 100
        val longLength = AiCleaningRepository.BASE_THRESHOLD_CHARS +
                AiCleaningRepository.EXTRA_TIMEOUT_CHUNK_CHARS * 2
        val shortCall = AiCleaningRepository.computeCallTimeout(shortLength)
        val longCall = AiCleaningRepository.computeCallTimeout(longLength)
        assertTrue(longCall > shortCall)
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
