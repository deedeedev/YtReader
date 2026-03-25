package com.deedeedev.ytreader

import android.content.Intent
import com.deedeedev.ytreader.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MainLaunchRouteResolverTest {

    @Test
    fun requestedHomeRouteForIntent_returnsNull_forLauncherIntent() {
        val intent = mock<Intent>().apply {
            whenever(action).thenReturn(Intent.ACTION_MAIN)
        }

        assertNull(requestedHomeRouteForIntent(intent))
    }

    @Test
    fun requestedHomeRouteForIntent_returnsSearch_forSharedPlainTextLink() {
        val intent = mock<Intent>().apply {
            whenever(action).thenReturn(Intent.ACTION_SEND)
            whenever(type).thenReturn("text/plain")
            whenever(getStringExtra(Intent.EXTRA_TEXT)).thenReturn(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            )
        }

        assertEquals(Screen.Search.route, requestedHomeRouteForIntent(intent))
    }

    @Test
    fun requestedHomeRouteForIntent_returnsNull_forSharedTextWithoutPayload() {
        val intent = mock<Intent>().apply {
            whenever(action).thenReturn(Intent.ACTION_SEND)
            whenever(type).thenReturn("text/plain")
            whenever(getStringExtra(Intent.EXTRA_TEXT)).thenReturn(null)
        }

        assertNull(requestedHomeRouteForIntent(intent))
    }
}
