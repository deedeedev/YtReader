package com.deedeedev.ytreader.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AiCleaningWorkerNotificationTest {

    @Test
    fun buildAiCleaningForegroundNotification_includesCancelAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val notification = buildAiCleaningForegroundNotification(
            context = context,
            subtitleId = 42L,
            workId = UUID.randomUUID(),
            title = "Subtitle",
            message = "Cleaning in progress"
        )

        val action = notification.actions.single()
        assertEquals("Cancel", action.title.toString())
        assertNotNull(action.actionIntent)
    }
}
