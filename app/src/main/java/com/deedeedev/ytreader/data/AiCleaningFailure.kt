package com.deedeedev.ytreader.data

import android.content.res.Resources
import com.deedeedev.ytreader.R
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
        resources: Resources,
        throwable: Throwable,
        endpoint: String? = null,
        model: String? = null
    ): AiCleaningFailure {
        return when (throwable) {
            is AiCleaningException -> throwable.failure
            is SocketTimeoutException -> buildFailure(
                resources = resources,
                summary = resources.getString(R.string.ai_cleaning_timed_out),
                lines = buildList {
                    add(resources.getString(R.string.ai_cleaning_log_error_type, throwable::class.java.name))
                    endpoint?.takeIf { it.isNotBlank() }
                        ?.let { add(resources.getString(R.string.ai_cleaning_log_endpoint, it)) }
                    model?.takeIf { it.isNotBlank() }
                        ?.let { add(resources.getString(R.string.ai_cleaning_log_model, it)) }
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?.let { add(resources.getString(R.string.ai_cleaning_log_message, it)) }
                    appendCauseChain(resources, this, throwable)
                }
            )
            else -> buildFailure(
                resources = resources,
                summary = throwable.message?.takeIf { it.isNotBlank() }
                    ?: resources.getString(R.string.ai_cleaning_failed),
                lines = buildList {
                    add(resources.getString(R.string.ai_cleaning_log_error_type, throwable::class.java.name))
                    endpoint?.takeIf { it.isNotBlank() }
                        ?.let { add(resources.getString(R.string.ai_cleaning_log_endpoint, it)) }
                    model?.takeIf { it.isNotBlank() }
                        ?.let { add(resources.getString(R.string.ai_cleaning_log_model, it)) }
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?.let { add(resources.getString(R.string.ai_cleaning_log_message, it)) }
                    appendCauseChain(resources, this, throwable)
                    add("")
                    add(resources.getString(R.string.ai_cleaning_log_stack_trace))
                    add(sanitizeLog(buildStackTrace(throwable)))
                }
            )
        }
    }

    fun buildFailure(resources: Resources, summary: String, lines: List<String>): AiCleaningFailure {
        val detail = sanitizeLog(lines.filter { it.isNotBlank() }.joinToString("\n"), resources)
        return AiCleaningFailure(
            summary = summary.ifBlank { resources.getString(R.string.ai_cleaning_failed) },
            detailedLog = if (detail.isBlank()) {
                resources.getString(R.string.ai_cleaning_no_additional_details)
            } else {
                detail
            }
        )
    }

    fun sanitizeLog(raw: String, resources: Resources? = null): String {
        if (raw.isBlank()) {
            return raw
        }
        val replacement = resources?.getString(R.string.ai_cleaning_log_redacted) ?: "<redacted>"
        val redacted = raw.replace(
            Regex("(?i)(authorization\\s*:\\s*bearer\\s+)(\\S+)"),
            "$1$replacement"
        )
        return if (redacted.length <= MAX_LOG_LENGTH) {
            redacted
        } else {
            redacted.take(MAX_LOG_LENGTH) + "\n" + (
                resources?.getString(R.string.ai_cleaning_log_truncated) ?: "... [truncated]"
            )
        }
    }

    private fun buildStackTrace(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun appendCauseChain(resources: Resources, lines: MutableList<String>, throwable: Throwable) {
        generateSequence(throwable.cause) { it.cause }
            .take(5)
            .forEachIndexed { index, cause ->
                val message = cause.message?.takeIf { it.isNotBlank() }
                    ?: resources.getString(R.string.ai_cleaning_log_no_message)
                lines += resources.getString(
                    R.string.ai_cleaning_log_cause,
                    index + 1,
                    cause::class.java.name,
                    message
                )
            }
    }
}
