package com.deedeedev.ytreader.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.UUID

class AiCleaningCancelReceiverTest {

    @Test
    fun parseAiCleaningCancellationRequest_returnsRequestForValidValues() {
        val workId = UUID.randomUUID()

        val request = parseAiCleaningCancellationRequest(
            subtitleId = 42L,
            workSpecId = workId.toString()
        )

        assertNotNull(request)
        assertEquals(42L, request?.subtitleId)
        assertEquals(workId, request?.workId)
    }

    @Test
    fun parseAiCleaningCancellationRequest_returnsNullForMalformedWorkId() {
        val request = parseAiCleaningCancellationRequest(
            subtitleId = 42L,
            workSpecId = "not-a-uuid"
        )

        assertNull(request)
    }

    @Test
    fun cancelAiCleaningWorkAndState_callsCancelWorkDbAndNotification() = runBlocking {
        val appContext = mock(Context::class.java)
        val workId = UUID.randomUUID()
        val request = AiCleaningCancellationRequest(subtitleId = 77L, workId = workId)
        val calls = mutableListOf<String>()

        cancelAiCleaningWorkAndState(
            appContext = appContext,
            request = request,
            nowProvider = { 1234L },
            cancelWork = { _, canceledWorkId ->
                calls += "work"
                assertEquals(workId, canceledWorkId)
            },
            cancelSubtitleState = { _, subtitleId, updatedAt ->
                calls += "db"
                assertEquals(77L, subtitleId)
                assertEquals(1234L, updatedAt)
            },
            cancelNotification = { _, notificationId ->
                calls += "notification"
                assertEquals(4277, notificationId)
            }
        )

        assertEquals(listOf("work", "db", "notification"), calls)
    }
}
