package com.deedeedev.ytreader.ui.reader

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.AppDatabase
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.ui.theme.AppTheme
import com.deedeedev.ytreader.ui.theme.YtReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
        private const val READER_FIND_DIALOG_TAG = "reader_find_dialog"
        private const val READER_FIND_INPUT_TAG = "reader_find_input"
        private const val READER_FIND_RESULTS_TAG = "reader_find_results"
        private const val READER_FIND_REPLACE_INPUT_TAG = "reader_find_replace_input"
        private const val READER_FIND_REPLACE_REPLACEMENT_TAG = "reader_find_replace_replacement"
        private const val READER_BRIGHTNESS_GESTURE_TAG = "reader_brightness_gesture"
        private const val READER_BRIGHTNESS_INDICATOR_TAG = "reader_brightness_indicator"
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var preferencesRepository: UserPreferencesRepository
    private val showReaderContent = mutableStateOf(true)
    private var subtitleId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        preferencesRepository = UserPreferencesRepository(context)
        preferencesRepository.setAppBrightness(UserPreferencesRepository.BRIGHTNESS_FOLLOW_SYSTEM)
        db.subtitleDao().upsertByIdentity(
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

        val textView = waitForStudyTextView()
        composeTestRule.runOnUiThread {
            textView.setSelectionRange(0, 5)
        }
        composeTestRule.waitForIdle()

        assertTrue(hasActiveSelection(textView))
        assertTagMissing(READER_TOP_BAR_TAG)

        tapStudyTextCenterViaRoot(textView)
        composeTestRule.waitForIdle()

        assertFalse(hasActiveSelection(textView))
        assertTagMissing(READER_SELECTION_TOOLBAR_TAG)
        assertTagMissing(READER_TOP_BAR_TAG)

        tapStudyTextCenterViaRoot(textView)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(READER_TOP_BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun fullscreenMode_hidesAndShowsSystemBarsWithReaderChrome() {
        setReaderContent()

        assertSystemBarsVisible(visible = false)

        showChrome()
        assertSystemBarsVisible(visible = true)

        composeTestRule.onNodeWithContentDescription("Edit").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertSystemBarsVisible(visible = true)
    }

    @Test
    fun overflowMenu_showsFindAboveFindAndReplace_inStudyMode() {
        setReaderContent()

        showChrome()
        openOverflowMenu()

        val findTop = composeTestRule.onNodeWithText("Find").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.top
        val findAndReplaceTop = composeTestRule.onNodeWithText("Find and replace").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(findTop < findAndReplaceTop)
    }

    @Test
    fun originalMode_overflowMenuShowsFind() {
        setReaderContent()

        switchToOriginalModeIfNeeded()
        openOverflowMenu()

        composeTestRule.onNodeWithText("Find").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Find and replace").assertCountEquals(0)
    }

    @Test
    fun studyEditMode_overflowMenuHidesFind() {
        setReaderContent()

        showChrome()
        composeTestRule.onNodeWithContentDescription("Edit").performClick()
        composeTestRule.waitForIdle()
        openOverflowMenu()

        composeTestRule.onAllNodesWithText("Find").assertCountEquals(0)
        composeTestRule.onNodeWithText("Find and replace").assertIsDisplayed()
    }

    @Test
    fun findDialog_invalidRegexShowsError() {
        setReaderContent()

        openFindDialog()
        composeTestRule.onNodeWithTag(READER_FIND_INPUT_TAG).performTextInput("(")
        composeTestRule.onNodeWithContentDescription("Search").performClick()

        composeTestRule.onNodeWithText("Invalid regex.").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(READER_FIND_RESULTS_TAG).assertCountEquals(0)
    }

    @Test
    fun findDialog_showsCaseSensitiveOption() {
        setReaderContent()

        openFindDialog()

        composeTestRule.onNodeWithText("Case sensitive").assertIsDisplayed()
    }

    @Test
    fun studyMode_findResultClosesDialogAndSelectsMatch() {
        setReaderContent()

        openFindDialog()
        composeTestRule.onNodeWithTag(READER_FIND_INPUT_TAG).performTextInput("line")
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithText("1.").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTagMissing(READER_FIND_DIALOG_TAG)

        val studyTextView = waitForStudyTextView()
        var selectedText: String? = null
        composeTestRule.runOnUiThread {
            selectedText = studyTextView.selectedText()
        }
        assertEquals("line", selectedText)
    }

    @Test
    fun originalMode_findResultClosesDialogAndSelectsMatch() {
        setReaderContent()

        switchToOriginalModeIfNeeded()
        openFindDialog()
        composeTestRule.onNodeWithTag(READER_FIND_INPUT_TAG).performTextInput("Second")
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithText("1.").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTagMissing(READER_FIND_DIALOG_TAG)

        val textViews = waitForReaderTextViews(count = 2)
        var selectedText: String? = null
        composeTestRule.runOnUiThread {
            selectedText = textViews.firstNotNullOfOrNull { it.selectedText() }
        }
        assertEquals("Second", selectedText)
    }

    @Test
    fun findAndReplaceDialog_invalidRegexShowsErrorAndDoesNotChangeContent() {
        setReaderContent()

        enterEditMode()
        openFindAndReplaceDialog()
        regexField().performTextInput("(")
        replaceField().performTextInput("updated")
        composeTestRule.onNodeWithText("Replace").performClick()

        composeTestRule.onNodeWithText("Invalid regex.").assertIsDisplayed()
    }

    @Test
    fun disposingReader_restoresSystemBarsVisible() {
        setReaderContent()

        assertSystemBarsVisible(visible = false)

        composeTestRule.runOnUiThread {
            showReaderContent.value = false
        }
        composeTestRule.waitForIdle()

        assertSystemBarsVisible(visible = true)
    }

    @Test
    fun backgroundingApp_savesStudyScrollWithoutDisposingReader() {
        runBlocking {
            db.subtitleDao().updateStudyContent(subtitleId, longStudyContent())
        }

        setReaderContent()

        val initialScroll = runBlocking {
            db.subtitleDao().getById(subtitleId)?.lastStudyScroll ?: 0
        }
        assertEquals(0, initialScroll)

        composeTestRule.onRoot().performTouchInput {
            swipeUp()
        }
        composeTestRule.waitForIdle()

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                (db.subtitleDao().getById(subtitleId)?.lastStudyScroll ?: 0) > 0
            }
        }

        val savedScroll = runBlocking {
            db.subtitleDao().getById(subtitleId)?.lastStudyScroll ?: 0
        }
        assertTrue("Expected study scroll to be saved on background, but was $savedScroll", savedScroll > 0)

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @Test
    fun leftEdgeSwipe_changesBrightnessAndShowsIndicator() {
        preferencesRepository.setAppBrightness(0.35f)
        setReaderContent()
        setWindowBrightness(0.35f)

        val before = currentWindowBrightness()

        composeTestRule.onNodeWithTag(READER_BRIGHTNESS_GESTURE_TAG).performTouchInput {
            swipeUp()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(READER_BRIGHTNESS_INDICATOR_TAG).assertIsDisplayed()
        val after = currentWindowBrightness()
        assertTrue("Expected brightness to increase from $before, but was $after", after > before)
    }

    @Test
    fun editMode_leftEdgeSwipeDoesNotChangeBrightness() {
        preferencesRepository.setAppBrightness(0.55f)
        setReaderContent()
        setWindowBrightness(0.55f)

        enterEditMode()

        composeTestRule.onNodeWithTag(READER_BRIGHTNESS_GESTURE_TAG).performTouchInput {
            swipeUp()
        }
        composeTestRule.waitForIdle()

        assertEquals(0.55f, currentWindowBrightness(), 0.001f)
    }

    @Test
    fun backPress_hidesChromeWhenVisible_thenCallsOnBackInFullscreen() {
        var backInvocations = 0
        setReaderContent(onBack = { backInvocations++ })

        showChrome()
        composeTestRule.onNodeWithTag(READER_TOP_BAR_TAG).assertIsDisplayed()

        pressSystemBack()
        composeTestRule.waitForIdle()
        assertTagMissing(READER_TOP_BAR_TAG)
        assertEquals(0, backInvocations)

        pressSystemBack()
        composeTestRule.waitForIdle()
        assertEquals(1, backInvocations)
    }

    @Test
    fun backPress_hidesChromeWhenVisible_duringAiCleaning_thenCallsOnBackInFullscreen() {
        runBlocking {
            db.subtitleDao().markAiCleaningQueued(
                id = subtitleId,
                sourceText = "queued text",
                updatedAt = System.currentTimeMillis()
            )
        }

        var backInvocations = 0
        setReaderContent(onBack = { backInvocations++ })

        showChrome()
        composeTestRule.onNodeWithTag(READER_TOP_BAR_TAG).assertIsDisplayed()

        pressSystemBack()
        composeTestRule.waitForIdle()
        assertTagMissing(READER_TOP_BAR_TAG)
        assertEquals(0, backInvocations)

        pressSystemBack()
        composeTestRule.waitForIdle()
        assertEquals(1, backInvocations)
    }

    private fun setReaderContent(onBack: () -> Unit = {}) {
        showReaderContent.value = true
        composeTestRule.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(composeTestRule.activity.window, false)
        }
        composeTestRule.setContent {
            YtReaderTheme(appTheme = AppTheme.LIGHT) {
                if (showReaderContent.value) {
                    ReaderScreen(
                        subtitleId = subtitleId,
                        subtitleDao = db.subtitleDao(),
                        userPreferencesRepository = preferencesRepository,
                        onChromeReady = {},
                        onBack = onBack
                    )
                } else {
                    Box {}
                }
            }
        }

        composeTestRule.waitForIdle()
    }

    private fun pressSystemBack() {
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
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

    private fun ensureChromeVisible() {
        if (composeTestRule.onAllNodesWithTag(READER_TOP_BAR_TAG).fetchSemanticsNodes().isEmpty()) {
            showChrome()
        }
    }

    private fun openOverflowMenu() {
        ensureChromeVisible()
        composeTestRule.onNodeWithContentDescription("More options").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    private fun openFindDialog() {
        ensureChromeVisible()
        openOverflowMenu()
        composeTestRule.onNodeWithText("Find").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag(READER_FIND_DIALOG_TAG).assertIsDisplayed()
    }

    private fun enterEditMode() {
        ensureChromeVisible()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Edit").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    private fun switchToOriginalModeIfNeeded() {
        ensureChromeVisible()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription("Switch to original mode").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithContentDescription("Switch to study mode").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeTestRule.onAllNodesWithContentDescription("Switch to original mode").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithContentDescription("Switch to original mode").assertIsDisplayed().performClick()
            composeTestRule.waitForIdle()
        }
    }

    private fun openFindAndReplaceDialog() {
        ensureChromeVisible()
        openOverflowMenu()
        composeTestRule.onNodeWithText("Find and replace").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Replace with").assertIsDisplayed()
    }

    private fun regexField() = composeTestRule.onNodeWithTag(READER_FIND_REPLACE_INPUT_TAG)

    private fun replaceField() = composeTestRule.onNodeWithTag(READER_FIND_REPLACE_REPLACEMENT_TAG)

    private fun waitForReaderTextViews(count: Int): List<SelectableHighlightTextView> {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            findReaderTextViews().size >= count
        }
        return findReaderTextViews().take(count)
    }

    private fun waitForStudyTextView(): JustifiedStudyTextView {
        lateinit var textView: JustifiedStudyTextView
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            findStudyTextView()?.also { textView = it } != null
        }
        return textView
    }

    private fun findReaderTextViews(): List<SelectableHighlightTextView> {
        val rootView = composeTestRule.activity.window.decorView.rootView
        return buildList {
            findReaderTextViews(rootView, this)
        }
    }

    private fun findStudyTextView(): JustifiedStudyTextView? {
        val rootView = composeTestRule.activity.window.decorView.rootView
        return findStudyTextView(rootView)
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

    private fun findStudyTextView(view: View): JustifiedStudyTextView? {
        if (view is JustifiedStudyTextView && view.isShown) {
            return view
        }
        if (view !is ViewGroup) {
            return null
        }
        for (index in 0 until view.childCount) {
            val found = findStudyTextView(view.getChildAt(index))
            if (found != null) {
                return found
            }
        }
        return null
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

    private fun tapStudyTextCenterViaRoot(textView: JustifiedStudyTextView) {
        var rootOffset = Offset.Zero
        composeTestRule.runOnUiThread {
            val locationInWindow = IntArray(2)
            val rootLocationInWindow = IntArray(2)
            textView.getLocationInWindow(locationInWindow)
            composeTestRule.activity.window.decorView.rootView.getLocationInWindow(rootLocationInWindow)
            rootOffset = Offset(
                x = locationInWindow[0] - rootLocationInWindow[0] + (textView.width / 2f),
                y = locationInWindow[1] - rootLocationInWindow[1] + (textView.height / 2f)
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

    private fun hasActiveSelection(textView: JustifiedStudyTextView): Boolean {
        var hasSelection = false
        composeTestRule.runOnUiThread {
            hasSelection = textView.hasActiveSelection()
        }
        return hasSelection
    }

    private fun assertTagMissing(tag: String) {
        composeTestRule.onAllNodesWithTag(tag).assertCountEquals(0)
    }

    private fun assertSystemBarsVisible(visible: Boolean) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            currentSystemBarsVisibility() == visible
        }
        assertTrue(
            "Expected system bars visible=$visible but was ${currentSystemBarsVisibility()}",
            currentSystemBarsVisibility() == visible
        )
    }

    private fun currentSystemBarsVisibility(): Boolean {
        var visible = true
        composeTestRule.runOnUiThread {
            val systemUiVisibility = composeTestRule.activity.window.decorView.systemUiVisibility
            val statusBarHidden = systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
            val navigationBarHidden = systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0
            visible = !statusBarHidden && !navigationBarHidden
        }
        return visible
    }

    private fun setWindowBrightness(brightness: Float) {
        composeTestRule.runOnUiThread {
            val params = composeTestRule.activity.window.attributes
            params.screenBrightness = brightness
            composeTestRule.activity.window.attributes = params
        }
        composeTestRule.waitForIdle()
    }

    private fun currentWindowBrightness(): Float {
        var brightness = -1f
        composeTestRule.runOnUiThread {
            brightness = composeTestRule.activity.window.attributes.screenBrightness
        }
        return brightness
    }

    private fun longStudyContent(): String = buildString {
        repeat(200) { index ->
            append("Scrollable study line ")
            append(index + 1)
            append('\n')
        }
    }
}
