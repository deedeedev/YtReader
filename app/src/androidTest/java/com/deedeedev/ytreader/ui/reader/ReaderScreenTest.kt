package com.deedeedev.ytreader.ui.reader

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {

    companion object {
        private const val CHROME_VISIBILITY_TIMEOUT_MS = 5_000L
        private const val CHROME_SHOW_RETRY_COUNT = 3
        private const val READER_TOP_BAR_TAG = "reader_top_bar"
        private const val READER_EDIT_TEXT_FIELD_TAG = "reader_edit_text_field"
        private const val READER_SELECTION_TOOLBAR_TAG = "reader_selection_toolbar"
        private const val READER_FIND_DIALOG_TAG = "reader_find_dialog"
        private const val READER_FIND_INPUT_TAG = "reader_find_input"
        private const val READER_FIND_RESULTS_TAG = "reader_find_results"
        private const val READER_FIND_REPLACE_INPUT_TAG = "reader_find_replace_input"
        private const val READER_FIND_REPLACE_REPLACEMENT_TAG = "reader_find_replace_replacement"
        private const val READER_SEARCH_RESULTS_BAR_TAG = "reader_search_results_bar"
        private const val READER_SEARCH_RESULTS_COUNT_TAG = "reader_search_results_count"
        private const val READER_BRIGHTNESS_GESTURE_TAG = "reader_brightness_gesture"
        private const val READER_BRIGHTNESS_INDICATOR_TAG = "reader_brightness_indicator"
        private const val READER_PAGE_PROGRESS_TAG = "reader_page_progress"
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var preferencesRepository: UserPreferencesRepository
    private var readerTestHooks: ReaderTestHooks? = null
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

        showChrome()
        composeTestRule.onNodeWithTag(READER_TOP_BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun tappingHighlightInTopZone_showsHighlightToolbarInsteadOfPaging() = runBlocking {
        db.subtitleDao().updateHighlights(
            subtitleId,
            serializeHighlights(listOf(TextHighlight(start = 0, end = 5, color = HighlightColor.YELLOW)))
        )

        setReaderContent()

        composeTestRule.runOnUiThread {
            checkNotNull(readerTestHooks).activateFirstHighlight()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(READER_SELECTION_TOOLBAR_TAG).assertIsDisplayed()
        assertTagMissing(READER_TOP_BAR_TAG)
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
    fun overflowMenu_keepsReplaceClipboardAiAndExternalAiAsLastThreeInStudyMode() {
        setReaderContent()

        showChrome()
        openOverflowMenu()

        val findAndReplaceTop = composeTestRule.onNodeWithText("Find and replace").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.top
        val replaceWithClipboardTop = composeTestRule.onNodeWithText("Replace with Clipboard").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.top
        val aiCleaningTop = composeTestRule.onNodeWithText("AI Cleaning").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.top
        val externalAiCleaningTop = composeTestRule.onNodeWithText("AI Cleaning (ext)").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(findAndReplaceTop < replaceWithClipboardTop)
        assertTrue(replaceWithClipboardTop < aiCleaningTop)
        assertTrue(aiCleaningTop < externalAiCleaningTop)
    }

    @Test
    fun originalMode_overflowMenuShowsFind() {
        setReaderContent()

        switchToOriginalModeIfNeeded()
        openOverflowMenu()

        composeTestRule.onNodeWithText("Find").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Replace with Clipboard").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("AI Cleaning (ext)").assertCountEquals(0)
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
        composeTestRule.onNodeWithText("Replace with Clipboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Find and replace").assertIsDisplayed()
    }

    @Test
    fun replaceWithClipboard_replacesStudyText() {
        setClipboardText("Clipboard replacement")
        setReaderContent()

        openOverflowMenu()
        composeTestRule.onNodeWithText("Replace with Clipboard").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        val updatedContent = runBlocking {
            db.subtitleDao().getById(subtitleId)?.studyContent
        }
        assertEquals("Clipboard replacement", updatedContent)
    }

    @Test
    fun replaceWithClipboard_withEmptyClipboardShowsSnackbarAndDoesNotReplace() {
        setClipboardText("")
        setReaderContent()

        val beforeContent = runBlocking {
            db.subtitleDao().getById(subtitleId)?.studyContent
        }

        openOverflowMenu()
        composeTestRule.onNodeWithText("Replace with Clipboard").assertIsDisplayed().performClick()

        composeTestRule.onNodeWithText("Clipboard is empty.").assertIsDisplayed()

        val afterContent = runBlocking {
            db.subtitleDao().getById(subtitleId)?.studyContent
        }
        assertEquals(beforeContent, afterContent)
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
    fun studyMode_findResultClosesDialogAndShowsSearchToolbar() {
        setReaderContent()

        openFindDialog()
        composeTestRule.onNodeWithTag(READER_FIND_INPUT_TAG).performTextInput("line")
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithText("1.").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTagMissing(READER_FIND_DIALOG_TAG)

        composeTestRule.onNodeWithTag(READER_SEARCH_RESULTS_BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun studyMode_findResultEntersSearchResultsModeWithoutTextSelection() {
        setReaderContent()

        openFindDialog()
        composeTestRule.onNodeWithTag(READER_FIND_INPUT_TAG).performTextInput("line")
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithText("1.").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTagMissing(READER_FIND_DIALOG_TAG)
        composeTestRule.onNodeWithTag(READER_SEARCH_RESULTS_BAR_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(READER_SEARCH_RESULTS_COUNT_TAG).assertTextEquals("1/2")
        composeTestRule.onNodeWithContentDescription("Previous search result").assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Next search result").assertIsEnabled()

        val studyTextView = waitForStudyTextView()
        var selectedText: String? = null
        composeTestRule.runOnUiThread {
            selectedText = studyTextView.selectedText()
        }
        assertEquals(null, selectedText)

        composeTestRule.onNodeWithContentDescription("Next search result").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(READER_SEARCH_RESULTS_COUNT_TAG).assertTextEquals("2/2")
        composeTestRule.onNodeWithContentDescription("Previous search result").assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Next search result").assertIsNotEnabled()

        composeTestRule.onNodeWithContentDescription("Close search results").performClick()
        composeTestRule.waitForIdle()
        assertTagMissing(READER_SEARCH_RESULTS_BAR_TAG)
    }

    @Test
    fun originalMode_findResultClosesDialogAndShowsSearchToolbar() {
        setReaderContent()

        switchToOriginalModeIfNeeded()
        openFindDialog()
        composeTestRule.onNodeWithTag(READER_FIND_INPUT_TAG).performTextInput("Second")
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithText("1.").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTagMissing(READER_FIND_DIALOG_TAG)

        composeTestRule.onNodeWithTag(READER_SEARCH_RESULTS_BAR_TAG).assertIsDisplayed()
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
    fun leftEdgeSwipeUp_reachesMaximumBrightness() {
        preferencesRepository.setAppBrightness(MIN_READER_BRIGHTNESS)
        setReaderContent()
        setWindowBrightness(MIN_READER_BRIGHTNESS)

        repeat(3) {
            swipeBrightnessGestureUp()
        }

        composeTestRule.onNodeWithTag(READER_BRIGHTNESS_INDICATOR_TAG).assertIsDisplayed()
        assertEquals(1f, currentWindowBrightness(), 0.001f)
    }

    @Test
    fun leftEdgeSwipeDown_reachesMinimumBrightness() {
        preferencesRepository.setAppBrightness(1f)
        setReaderContent()
        setWindowBrightness(1f)

        repeat(3) {
            swipeBrightnessGestureDown()
        }

        composeTestRule.onNodeWithTag(READER_BRIGHTNESS_INDICATOR_TAG).assertIsDisplayed()
        assertEquals(MIN_READER_BRIGHTNESS, currentWindowBrightness(), 0.001f)
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
    fun fullscreenTapGestures_navigatePagesInAllDirections() {
        runBlocking {
            db.subtitleDao().updateStudyContent(subtitleId, longStudyContent())
        }

        setReaderContent()

        val initialPage = currentTinyIndicatorPage()
        tapRootFraction(xFraction = 0.9f, yFraction = 0.5f)
        val pageAfterRightTap = currentTinyIndicatorPage()

        assertTrue(
            "Expected right-side tap to move forward, but page stayed at $initialPage",
            pageAfterRightTap > initialPage
        )

        tapRootFraction(xFraction = 0.1f, yFraction = 0.5f)
        val pageAfterLeftTap = currentTinyIndicatorPage()

        assertEquals(initialPage, pageAfterLeftTap)

        tapRootFraction(xFraction = 0.5f, yFraction = 0.9f)
        val pageAfterBottomTap = currentTinyIndicatorPage()

        assertTrue(
            "Expected bottom-side tap to move forward, but page was $pageAfterBottomTap",
            pageAfterBottomTap > pageAfterLeftTap
        )

        tapRootFraction(xFraction = 0.5f, yFraction = 0.1f)
        val pageAfterTopTap = currentTinyIndicatorPage()

        assertEquals(pageAfterLeftTap, pageAfterTopTap)
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
        readerTestHooks = null
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
                        highlightNoteDao = db.highlightNoteDao(),
                        bookmarkDao = db.bookmarkDao(),
                        userPreferencesRepository = preferencesRepository,
                        onOpenVideoNotes = {},
                        onChromeReady = {},
                        onTestHooksReady = { hooks -> readerTestHooks = hooks },
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

    private fun setClipboardText(text: String) {
        val clipboardManager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("reader-test", text))
    }

    private fun showChrome() {
        readerTestHooks?.showChrome?.let { showChrome ->
            composeTestRule.runOnUiThread { showChrome() }
            composeTestRule.waitUntil(timeoutMillis = CHROME_VISIBILITY_TIMEOUT_MS) {
                isChromeVisible()
            }
            return
        }
        val initialDiagnostic = readerTapDiagnosticState()
        repeat(CHROME_SHOW_RETRY_COUNT) {
            if (isChromeVisible()) return
            tapVisibleReaderContentCenter()
            composeTestRule.waitForIdle()
            if (isChromeVisible()) return
        }
        val finalDiagnostic = readerTapDiagnosticState()
        if (!isChromeVisible()) {
            fail(
                buildString {
                    append("Center tap did not show reader chrome. ")
                    append("Before: ")
                    append(initialDiagnostic)
                    append(". After: ")
                    append(finalDiagnostic)
                    append(". Center tap may be interpreted as paging instead of TOGGLE_UI.")
                }
            )
        }
    }

    private fun hideChrome() {
        if (!isChromeVisible()) {
            return
        }
        readerTestHooks?.hideChrome?.let { hideChrome ->
            composeTestRule.runOnUiThread { hideChrome() }
            composeTestRule.waitUntil(timeoutMillis = CHROME_VISIBILITY_TIMEOUT_MS) {
                !isChromeVisible()
            }
            return
        }
        tapVisibleReaderContentCenter()
        composeTestRule.waitUntil(timeoutMillis = CHROME_VISIBILITY_TIMEOUT_MS) {
            !isChromeVisible()
        }
    }

    private fun ensureChromeVisible() {
        if (!isChromeVisible()) {
            showChrome()
        }
        composeTestRule.onNodeWithTag(READER_TOP_BAR_TAG).assertIsDisplayed()
    }

    private fun openOverflowMenu() {
        ensureChromeVisible()
        composeTestRule.waitUntil(timeoutMillis = CHROME_VISIBILITY_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithContentDescription("More options").fetchSemanticsNodes().isNotEmpty()
        }
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
        composeTestRule.waitUntil(timeoutMillis = CHROME_VISIBILITY_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Edit").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    private fun switchToOriginalModeIfNeeded() {
        ensureChromeVisible()
        composeTestRule.waitUntil(timeoutMillis = CHROME_VISIBILITY_TIMEOUT_MS) {
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

    private fun isChromeVisible(): Boolean {
        return composeTestRule.onAllNodesWithTag(READER_TOP_BAR_TAG).fetchSemanticsNodes().isNotEmpty()
    }

    private fun readerTapDiagnosticState(): String {
        val topBarVisible = isChromeVisible()
        val pageIndicatorText = composeTestRule.onAllNodesWithTag(READER_PAGE_PROGRESS_TAG)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.let { node ->
                val textList = if (SemanticsProperties.Text in node.config) {
                    node.config[SemanticsProperties.Text]
                } else {
                    emptyList()
                }
                textList.joinToString(separator = "") { it.text }.ifBlank { "<blank>" }
            } ?: "<missing>"
        return "topBarVisible=$topBarVisible, pageIndicator=$pageIndicatorText"
    }

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

    private fun tapReaderTextCenterViaRoot(textView: SelectableHighlightTextView) {
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

    private fun tapVisibleReaderContentCenter() {
        val studyTextView = findStudyTextView()
        if (studyTextView != null) {
            tapStudyTextCenterViaRoot(studyTextView)
            return
        }

        val readerTextView = findReaderTextViews().firstOrNull()
        if (readerTextView != null) {
            tapReaderTextCenterViaRoot(readerTextView)
            return
        }

        fail("Could not find a visible reader content view to tap.")
    }

    private fun tapStudyTextOffsetViaRoot(
        textView: JustifiedStudyTextView,
        xInsetPx: Float,
        yInsetPx: Float
    ) {
        var rootOffset = Offset.Zero
        composeTestRule.runOnUiThread {
            val locationInWindow = IntArray(2)
            val rootLocationInWindow = IntArray(2)
            textView.getLocationInWindow(locationInWindow)
            composeTestRule.activity.window.decorView.rootView.getLocationInWindow(rootLocationInWindow)
            rootOffset = Offset(
                x = locationInWindow[0] - rootLocationInWindow[0] + xInsetPx,
                y = locationInWindow[1] - rootLocationInWindow[1] + yInsetPx
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

    private fun tapRootFraction(xFraction: Float, yFraction: Float) {
        composeTestRule.onRoot().performTouchInput {
            click(
                Offset(
                    x = width * xFraction.coerceIn(0f, 1f),
                    y = height * yFraction.coerceIn(0f, 1f)
                )
            )
        }
        composeTestRule.waitForIdle()
    }

    private fun swipeBrightnessGestureUp() {
        composeTestRule.onNodeWithTag(READER_BRIGHTNESS_GESTURE_TAG).performTouchInput {
            val start = Offset(x = centerX, y = height * 0.9f)
            val end = Offset(x = centerX, y = height * 0.1f)
            down(start)
            moveTo(end)
            up()
        }
        composeTestRule.waitForIdle()
    }

    private fun swipeBrightnessGestureDown() {
        composeTestRule.onNodeWithTag(READER_BRIGHTNESS_GESTURE_TAG).performTouchInput {
            val start = Offset(x = centerX, y = height * 0.1f)
            val end = Offset(x = centerX, y = height * 0.9f)
            down(start)
            moveTo(end)
            up()
        }
        composeTestRule.waitForIdle()
    }

    private fun currentTinyIndicatorPage(): Int {
        composeTestRule.onNodeWithTag(READER_PAGE_PROGRESS_TAG).assertIsDisplayed()
        val node = composeTestRule.onNodeWithTag(READER_PAGE_PROGRESS_TAG).fetchSemanticsNode()
        val textList = if (SemanticsProperties.Text in node.config) {
            node.config[SemanticsProperties.Text]
        } else {
            emptyList()
        }
        val text = textList.joinToString(separator = "") { it.text }
        val pagePart = text.substringAfterLast(' ').substringBefore('/')
        return pagePart.toInt()
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
