package com.deedeedev.ytreader.data

import androidx.room.Room
import com.deedeedev.ytreader.data.local.AppDatabase
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.data.local.SubtitleReadingStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SubtitleReadingProgressTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: SubtitleRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), AppDatabase::class.java
        ).build()
        repository = SubtitleRepository(
            db.subtitleDao(), db.subtitleReadingStateDao(), db.aiCleaningStateDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun progressAfterDeletion_doesNotLoadLargeSubtitleText() = runBlocking {
        val deletedId = insertSubtitle("deleted", "en", 20)
        insertSubtitle("remaining", "en", 30)
        insertSubtitle("remaining", "it", 80, "x".repeat(3 * 1024 * 1024))
        insertSubtitle("unread", "en", null)
        insertSubtitle("unrelated", "en", 100)

        db.subtitleDao().updateLibraryVisibility("deleted", false)
        db.subtitleDao().deleteByVideoId("deleted")
        assertEquals(null, db.subtitleReadingStateDao().getBySubtitleId(deletedId))

        assertEquals(
            mapOf("remaining" to 80, "unread" to 0),
            repository.getMaxReadingProgressForVideos(listOf("remaining", "unread", "missing"))
        )
        assertEquals(emptyMap<String, Int>(), repository.getMaxReadingProgressForVideos(emptyList()))
    }

    private suspend fun insertSubtitle(
        videoId: String,
        language: String,
        progress: Int?,
        content: String = "content"
    ): Long {
        val id = db.subtitleDao().insertIgnore(
            SubtitleEntity(videoId = videoId, title = videoId, languageCode = language, content = content)
        )
        if (progress != null) {
            db.subtitleReadingStateDao().insert(
                SubtitleReadingStateEntity(subtitleId = id, readingProgressPercent = progress)
            )
        }
        return id
    }
}
