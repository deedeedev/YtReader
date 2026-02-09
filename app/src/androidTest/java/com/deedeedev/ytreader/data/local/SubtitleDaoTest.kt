package com.deedeedev.ytreader.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SubtitleDaoTest {
    private lateinit var subtitleDao: SubtitleDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        subtitleDao = db.subtitleDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetSubtitle() = runBlocking {
        val subtitle = SubtitleEntity(
            videoId = "123",
            title = "Test Video",
            languageCode = "en",
            content = "Hello World"
        )
        subtitleDao.insert(subtitle)
        
        val allSubtitles = subtitleDao.getAll().first()
        assertEquals(1, allSubtitles.size)
        assertEquals("Test Video", allSubtitles[0].title)
        
        // Check getById
        val insertedId = allSubtitles[0].id
        val fetched = subtitleDao.getById(insertedId)
        assertEquals("Hello World", fetched?.content)
    }
}
