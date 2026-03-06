package com.deedeedev.ytreader.data

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class AiCleaningRequest(
    val endpointBaseUrl: String,
    val apiKey: String,
    val model: String,
    val userInstructions: String,
    val subtitleText: String
)

class AiCleaningRepository(
    private val client: OkHttpClient,
    private val gson: Gson = Gson()
) {

    suspend fun cleanText(request: AiCleaningRequest): String = withContext(Dispatchers.IO) {
        val endpoint = request.endpointBaseUrl.trim().trimEnd('/') + CHAT_COMPLETIONS_PATH
        val effectiveInstructions = request.userInstructions.ifBlank { DEFAULT_AI_CLEANING_PROMPT }

        val payload = ChatCompletionsRequest(
            model = request.model,
            messages = listOf(
                ChatMessage(role = "system", content = SYSTEM_PROMPT),
                ChatMessage(
                    role = "user",
                    content = buildUserPrompt(
                        userInstructions = effectiveInstructions,
                        subtitleText = request.subtitleText
                    )
                )
            ),
            temperature = 0.1
        )

        val httpRequest = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${request.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val details = body.take(240)
                throw IOException("AI request failed (${response.code}): $details")
            }

            val cleanedText = parseCleanedText(body)
            if (cleanedText.isNullOrBlank()) {
                throw IOException("AI response does not contain cleaned text")
            }
            cleanedText.trimEnd()
        }
    }

    internal fun buildUserPrompt(userInstructions: String, subtitleText: String): String {
        return """
        Additional cleaning instructions from user:
        $userInstructions

        Clean the subtitle text below.
        Return only cleaned text with no markdown, no code fences, no explanations.

        <<SUBTITLE_TEXT>>
        $subtitleText
        <</SUBTITLE_TEXT>>
        """.trimIndent()
    }

    internal fun parseCleanedText(responseBody: String): String? {
        return try {
            val response = gson.fromJson(responseBody, ChatCompletionsResponse::class.java)
            response.choices
                .firstOrNull()
                ?.message
                ?.content
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private data class ChatCompletionsRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double
    )

    private data class ChatMessage(
        val role: String,
        val content: String
    )

    private data class ChatCompletionsResponse(
        val choices: List<ChatChoice> = emptyList()
    )

    private data class ChatChoice(
        val message: ChatMessageContent? = null
    )

    private data class ChatMessageContent(
        val content: String? = null
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val CHAT_COMPLETIONS_PATH = "/chat/completions"

        private const val SYSTEM_PROMPT = """
            You are a subtitle cleaning assistant.
            Improve subtitle readability with minimal edits only.

            Allowed changes:
            - Merge subtitle fragments into complete sentences.
            - Add missing punctuation.
            - Correct obvious transcription mistakes.

            Hard constraints:
            - Keep original meaning and sentence order.
            - Stay as close as possible to original words.
            - Do not summarize, rewrite style, translate, or add content.
            - Output plain cleaned text only.
        """
    }
}
