package com.deedeedev.ytreader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationTest {
    @Test
    fun testApplicationInitialization() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertTrue(appContext is YtReaderApplication)
        
        val application = appContext as YtReaderApplication
        assertNotNull("AppContainer should be initialized", application.container)
    }
}
