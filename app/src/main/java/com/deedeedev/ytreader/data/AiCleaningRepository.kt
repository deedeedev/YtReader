package com.deedeedev.ytreader.data

import android.content.Context
import com.deedeedev.ytreader.R
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AiCleaningRequest(
    val endpointBaseUrl: String,
    val apiKey: String,
    val model: String,
    val userInstructions: String,
    val subtitleText: String
)

class AiCleaningRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val gson: Gson = Gson()
) {

    suspend fun cleanText(request: AiCleaningRequest): String = withContext(Dispatchers.IO) {
        val endpoint = buildEndpoint(request.endpointBaseUrl)
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

        executeCancellable(httpRequest).use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AiCleaningException(
                    AiCleaningFailureFactory.buildFailure(
                        resources = context.resources,
                        summary = context.getString(R.string.ai_cleaning_request_failed, response.code),
                        lines = listOf(
                            context.getString(R.string.ai_cleaning_log_endpoint, endpoint),
                            context.getString(R.string.ai_cleaning_log_model, request.model),
                            context.getString(R.string.ai_cleaning_log_http_status, response.code),
                            context.getString(R.string.ai_cleaning_log_response_body),
                            AiCleaningFailureFactory.sanitizeLog(
                                body.ifBlank { context.getString(R.string.ai_cleaning_log_empty) },
                                context.resources
                            )
                        )
                    )
                )
            }

            val cleanedText = parseCleanedText(body)
            if (cleanedText.isNullOrBlank()) {
                throw AiCleaningException(
                    AiCleaningFailureFactory.buildFailure(
                        resources = context.resources,
                        summary = context.getString(R.string.ai_cleaning_missing_response_text),
                        lines = listOf(
                            context.getString(R.string.ai_cleaning_log_endpoint, endpoint),
                            context.getString(R.string.ai_cleaning_log_model, request.model),
                            context.getString(R.string.ai_cleaning_log_response_body),
                            AiCleaningFailureFactory.sanitizeLog(
                                body.ifBlank { context.getString(R.string.ai_cleaning_log_empty) },
                                context.resources
                            )
                        )
                    )
                )
            }
            cleanedText.trimEnd()
        }
    }

    fun toFailure(request: AiCleaningRequest, throwable: Throwable): AiCleaningFailure {
        if (throwable is SocketTimeoutException) {
            return AiCleaningFailureFactory.fromThrowable(
                resources = context.resources,
                throwable = throwable,
                endpoint = buildEndpoint(request.endpointBaseUrl),
                model = request.model
            )
        }
        return AiCleaningFailureFactory.fromThrowable(
            resources = context.resources,
            throwable = throwable,
            endpoint = runCatching { buildEndpoint(request.endpointBaseUrl) }.getOrNull(),
            model = request.model
        )
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

    internal fun buildEndpoint(baseOrFullUrl: String): String {
        val normalized = baseOrFullUrl.trim().trimEnd('/')
        return if (normalized.endsWith(CHAT_COMPLETIONS_PATH)) {
            normalized
        } else {
            normalized + CHAT_COMPLETIONS_PATH
        }
    }

    private suspend fun executeCancellable(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            })
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
