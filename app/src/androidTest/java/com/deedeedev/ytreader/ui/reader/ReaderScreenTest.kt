package com.deedeedev.ytreader.ui.reader

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.deedeedev.ytreader.ui.theme.AppTheme
import com.deedeedev.ytreader.ui.theme.YtReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {

    companion object {
        private const val READER_TOP_BAR_TAG = "reader_top_bar"
        private const val READER_EDIT_TEXT_FIELD_TAG = "reader_edit_text_field"
        private const val READER_SELECTION_TOOLBAR_TAG = "reader_selection_toolbar"
    }

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
                content = """
                    1
                    00:00:00.000 --> 00:00:01.000
                    First line

                    2
                    00:00:01.000 --> 00:00:02.000
                    Second line
                """.trimIndent()
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
        setReaderContent()

        showChrome()

        composeTestRule.onNodeWithContentDescription("Edit").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        val topBarBounds = composeTestRule.onNodeWithTag(READER_TOP_BAR_TAG).fetchSemanticsNode().boundsInRoot
        val textFieldBounds = composeTestRule.onNodeWithTag(READER_EDIT_TEXT_FIELD_TAG).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected edit field to start below top bar, but field top=${textFieldBounds.top} and top bar bottom=${topBarBounds.bottom}",
            textFieldBounds.top >= topBarBounds.bottom
        )
    }

    @Test
    fun studyMode_deselectTapDoesNotToggleChrome() {
        setReaderContent()

        val textView = waitForReaderTextViews(count = 1).first()
        composeTestRule.runOnUiThread {
            textView.setSelectionRange(0, 5)
        }
        composeTestRule.waitForIdle()

        assertTrue(hasActiveSelection(textView))
        assertTagMissing(READER_TOP_BAR_TAG)

        tapTextAtOffsetViaRoot(textView, offset = 8)
        composeTestRule.waitForIdle()

        assertFalse(hasActiveSelection(textView))
        assertTagMissing(READER_SELECTION_TOOLBAR_TAG)
        assertTagMissing(READER_TOP_BAR_TAG)

        tapTextAtOffsetViaRoot(textView, offset = 8)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(READER_TOP_BAR_TAG).assertIsDisplayed()
    }

    private fun setReaderContent() {
        composeTestRule.setContent {
            YtReaderTheme(appTheme = AppTheme.LIGHT) {
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
    }

    private fun showChrome() {
        composeTestRule.onRoot().performTouchInput {
            click(Offset(centerX, centerY))
        }
        composeTestRule.waitForIdle()
    }

    private fun hideChrome() {
        showChrome()
        assertTagMissing(READER_TOP_BAR_TAG)
    }

    private fun waitForReaderTextViews(count: Int): List<SelectableHighlightTextView> {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            findReaderTextViews().size >= count
        }
        return findReaderTextViews().take(count)
    }

    private fun findReaderTextViews(): List<SelectableHighlightTextView> {
        val rootView = composeTestRule.activity.window.decorView.rootView
        return buildList {
            findReaderTextViews(rootView, this)
        }
    }

    private fun findReaderTextViews(view: View, results: MutableList<SelectableHighlightTextView>) {
        if (view is SelectableHighlightTextView && view.isShown) {
            results += view
            return
        }
        if (view !is ViewGroup) {
            return
        }
        for (index in 0 until view.childCount) {
            findReaderTextViews(view.getChildAt(index), results)
        }
    }

    private fun tapTextAtOffsetViaRoot(textView: SelectableHighlightTextView, offset: Int) {
        var rootOffset = Offset.Zero
        composeTestRule.runOnUiThread {
            val locationInWindow = IntArray(2)
            val rootLocationInWindow = IntArray(2)
            val layout = checkNotNull(textView.layout) { "Text layout was not ready" }
            val safeOffset = offset.coerceIn(0, (textView.text.length - 1).coerceAtLeast(0))
            val line = layout.getLineForOffset(safeOffset)
            val localX = layout.getPrimaryHorizontal(safeOffset) + textView.totalPaddingLeft
            val localY = ((layout.getLineTop(line) + layout.getLineBottom(line)) / 2f) + textView.totalPaddingTop
            textView.getLocationInWindow(locationInWindow)
            composeTestRule.activity.window.decorView.rootView.getLocationInWindow(rootLocationInWindow)
            rootOffset = Offset(
                x = locationInWindow[0] - rootLocationInWindow[0] + localX,
                y = locationInWindow[1] - rootLocationInWindow[1] + localY
            )
        }
        composeTestRule.onRoot().performTouchInput {
            click(rootOffset)
        }
    }

    private fun hasActiveSelection(textView: SelectableHighlightTextView): Boolean {
        var hasSelection = false
        composeTestRule.runOnUiThread {
            hasSelection = textView.hasActiveSelection()
        }
        return hasSelection
    }

    private fun assertTagMissing(tag: String) {
        composeTestRule.onAllNodesWithTag(tag).assertCountEquals(0)
    }
}
