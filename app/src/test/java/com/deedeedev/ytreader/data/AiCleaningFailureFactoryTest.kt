package com.deedeedev.ytreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class AiCleaningFailureFactoryTest {

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
}
