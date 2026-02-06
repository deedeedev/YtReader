package com.deedeedev.ytreader

import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock

class AppContainerTest {

    @Test
    fun testDefaultAppContainerInitialization() {
        // Given
        val mockContext = mock(Context::class.java)

        // When
        val container = DefaultAppContainer(mockContext)

        // Then
        assertNotNull(container)
        // Check if other dependencies (when added) are initialized. 
        // Currently AppContainer is empty so checking not null is enough.
    }
}
