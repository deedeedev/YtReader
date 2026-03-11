package com.deedeedev.ytreader.ui.reader

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deedeedev.ytreader.data.AiCleaningRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.AppDatabase
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.ui.theme.YtReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var aiCleaningRepository: AiCleaningRepository
    private var subtitleId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        preferencesRepository = UserPreferencesRepository(context)
        aiCleaningRepository = AiCleaningRepository(OkHttpClient())

        db.subtitleDao().insert(
            SubtitleEntity(
                videoId = "video-1",
                title = "Reader Test Title",
                languageCode = "en",
                content = "First line\nSecond line"
            )
        )
        subtitleId = db.subtitleDao().getAll().first().first().id
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun editMode_positionsTextFieldBelowTopBar() {
        composeTestRule.setContent {
            YtReaderTheme(dynamicColor = false) {
                ReaderScreen(
                    subtitleId = subtitleId,
                    subtitleDao = db.subtitleDao(),
                    userPreferencesRepository = preferencesRepository,
                    aiCleaningRepository = aiCleaningRepository,
                    onChromeReady = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onRoot().performTouchInput {
            click(Offset(centerX, bottom - 20f))
        }

        composeTestRule.onNodeWithContentDescription("Edit").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        val topBarBounds = composeTestRule.onNodeWithTag("reader_top_bar").fetchSemanticsNode().boundsInRoot
        val textFieldBounds = composeTestRule.onNodeWithTag("reader_edit_text_field").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected edit field to start below top bar, but field top=${textFieldBounds.top} and top bar bottom=${topBarBounds.bottom}",
            textFieldBounds.top >= topBarBounds.bottom
        )
    }
}
