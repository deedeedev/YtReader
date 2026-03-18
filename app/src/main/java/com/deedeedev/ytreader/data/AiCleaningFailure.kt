package com.deedeedev.ytreader.data

import java.io.PrintWriter
import java.io.StringWriter
import java.net.SocketTimeoutException

data class AiCleaningFailure(
    val summary: String,
    val detailedLog: String
)

class AiCleaningException(
    val failure: AiCleaningFailure,
    cause: Throwable? = null
) : Exception(failure.summary, cause)

internal object AiCleaningFailureFactory {
    private const val MAX_LOG_LENGTH = 8_000

    fun fromThrowable(
        throwable: Throwable,
        endpoint: String? = null,
        model: String? = null
    ): AiCleaningFailure {
        return when (throwable) {
            is AiCleaningException -> throwable.failure
            is SocketTimeoutException -> buildFailure(
                summary = "AI cleaning timed out. Try shorter text or check endpoint/model.",
                lines = buildList {
                    add("Error type: ${throwable::class.java.name}")
                    endpoint?.takeIf { it.isNotBlank() }?.let { add("Endpoint: $it") }
                    model?.takeIf { it.isNotBlank() }?.let { add("Model: $it") }
                    throwable.message?.takeIf { it.isNotBlank() }?.let { add("Message: $it") }
                    appendCauseChain(this, throwable)
                }
            )
            else -> buildFailure(
                summary = throwable.message?.takeIf { it.isNotBlank() } ?: "AI cleaning failed.",
                lines = buildList {
                    add("Error type: ${throwable::class.java.name}")
                    endpoint?.takeIf { it.isNotBlank() }?.let { add("Endpoint: $it") }
                    model?.takeIf { it.isNotBlank() }?.let { add("Model: $it") }
                    throwable.message?.takeIf { it.isNotBlank() }?.let { add("Message: $it") }
                    appendCauseChain(this, throwable)
                    add("")
                    add("Stack trace:")
                    add(sanitizeLog(buildStackTrace(throwable)))
                }
            )
        }
    }

    fun buildFailure(summary: String, lines: List<String>): AiCleaningFailure {
        val detail = sanitizeLog(lines.filter { it.isNotBlank() }.joinToString("\n"))
        return AiCleaningFailure(
            summary = summary.ifBlank { "AI cleaning failed." },
            detailedLog = if (detail.isBlank()) "No additional error details." else detail
        )
    }

    fun sanitizeLog(raw: String): String {
        if (raw.isBlank()) {
            return raw
        }
        val redacted = raw.replace(Regex("(?i)(authorization\\s*:\\s*bearer\\s+)(\\S+)"), "$1<redacted>")
        return if (redacted.length <= MAX_LOG_LENGTH) {
            redacted
        } else {
            redacted.take(MAX_LOG_LENGTH) + "\n… [truncated]"
        }
    }

    private fun buildStackTrace(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun appendCauseChain(lines: MutableList<String>, throwable: Throwable) {
        generateSequence(throwable.cause) { it.cause }
            .take(5)
            .forEachIndexed { index, cause ->
                val message = cause.message?.takeIf { it.isNotBlank() } ?: "<no message>"
                lines += "Cause ${index + 1}: ${cause::class.java.name}: $message"
            }
    }
}
