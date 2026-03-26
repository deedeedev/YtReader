package com.deedeedev.ytreader.data

import android.content.res.Resources
import com.deedeedev.ytreader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import org.mockito.kotlin.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class AiCleaningFailureFactoryTest {
    private val resources = mockResources()

    @Test
    fun sanitizeLog_redactsBearerToken() {
        val sanitized = AiCleaningFailureFactory.sanitizeLog(
            "Authorization: Bearer secret-token-value"
        )

        assertTrue(sanitized.contains("Authorization: Bearer <redacted>"))
        assertFalse(sanitized.contains("secret-token-value"))
    }

    @Test
    fun fromThrowable_timeoutUsesFriendlySummary() {
        val failure = AiCleaningFailureFactory.fromThrowable(
            resources = resources,
            throwable = SocketTimeoutException("Read timed out"),
            endpoint = "https://api.example.com/v1/chat/completions",
            model = "gpt-test"
        )

        assertEquals(
            "AI cleaning timed out. Try shorter text or check endpoint/model.",
            failure.summary
        )
        assertTrue(failure.detailedLog.contains("Endpoint: https://api.example.com/v1/chat/completions"))
        assertTrue(failure.detailedLog.contains("Model: gpt-test"))
    }

    private fun mockResources(): Resources {
        val resources = mock<Resources>()
        whenever(resources.getString(R.string.ai_cleaning_timed_out))
            .thenReturn("AI cleaning timed out. Try shorter text or check endpoint/model.")
        whenever(resources.getString(R.string.ai_cleaning_failed)).thenReturn("AI cleaning failed.")
        whenever(resources.getString(R.string.ai_cleaning_no_additional_details))
            .thenReturn("No additional error details.")
        whenever(resources.getString(eq(R.string.ai_cleaning_log_error_type), any())).thenAnswer {
            "Error type: ${it.arguments[1]}"
        }
        whenever(resources.getString(eq(R.string.ai_cleaning_log_endpoint), any())).thenAnswer {
            "Endpoint: ${it.arguments[1]}"
        }
        whenever(resources.getString(eq(R.string.ai_cleaning_log_model), any())).thenAnswer {
            "Model: ${it.arguments[1]}"
        }
        whenever(resources.getString(eq(R.string.ai_cleaning_log_message), any())).thenAnswer {
            "Message: ${it.arguments[1]}"
        }
        whenever(resources.getString(R.string.ai_cleaning_log_no_message)).thenReturn("<no message>")
        whenever(resources.getString(eq(R.string.ai_cleaning_log_cause), any(), any(), any())).thenAnswer {
            "Cause ${it.arguments[1]}: ${it.arguments[2]}: ${it.arguments[3]}"
        }
        return resources
    }
}
