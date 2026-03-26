package com.deedeedev.ytreader

import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AppContainerTest {

    @Test
    fun testDefaultAppContainerInitialization() {
        // Given
        val mockContext = mock(Context::class.java)
        `when`(mockContext.applicationContext).thenReturn(mockContext)

        // When
        val container = DefaultAppContainer(mockContext)

        // Then
        assertNotNull(container)
        // Check if other dependencies (when added) are initialized. 
        // Currently AppContainer is empty so checking not null is enough.
    }
}
