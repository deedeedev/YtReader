package com.deedeedev.ytreader.ui.reader

import android.content.Context
import android.content.SharedPreferences
import com.deedeedev.ytreader.StringProvider
import com.deedeedev.ytreader.data.NoteRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.data.local.HighlightNoteEntity
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.data.SubtitleWithStates
import com.deedeedev.ytreader.data.local.AiCleaningStateEntity
import com.deedeedev.ytreader.data.local.SubtitleReadingStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var subtitleRepository: SubtitleRepository
    private lateinit var noteRepository: NoteRepository
    private lateinit var subtitleWithStatesFlow: MutableStateFlow<SubtitleWithStates?>
    private lateinit var noteFlow: MutableStateFlow<List<HighlightNoteEntity>>
    private lateinit var bookmarkFlow: MutableStateFlow<List<BookmarkEntity>>
    private lateinit var stringProvider: StringProvider

    @Before
    fun setUp() {
        stringProvider = mock()
        subtitleRepository = mock()
        noteRepository = mock()
        subtitleWithStatesFlow = MutableStateFlow(
            SubtitleWithStates(
                subtitle = baseSubtitleEntity(highlights = emptyList()),
                readingState = null,
                aiCleaningState = null
            )
        )
        noteFlow = MutableStateFlow(emptyList())
        bookmarkFlow = MutableStateFlow(emptyList())

        whenever(subtitleRepository.observeSubtitleWithStates(SUBTITLE_ID)).thenReturn(subtitleWithStatesFlow)
        whenever(noteRepository.observeHighlightsBySubtitleId(SUBTITLE_ID)).thenReturn(noteFlow)
        whenever(noteRepository.observeBookmarksBySubtitleId(SUBTITLE_ID)).thenReturn(bookmarkFlow)
        whenever(stringProvider.getString(com.deedeedev.ytreader.R.string.ai_cleaning_missing_settings)).thenReturn("Missing settings")
        whenever(stringProvider.getString(com.deedeedev.ytreader.R.string.ai_cleaning_already_running)).thenReturn("Already running")
        whenever(stringProvider.getString(com.deedeedev.ytreader.R.string.ai_cleaning_start_failed)).thenReturn("Start failed")
    }

    @Test
    fun init_combinesPersistedHighlightNotesIntoUiState() = runTest {
        val highlights = listOf(
            TextHighlight(start = 0, end = 5, color = HighlightColor.YELLOW),
            TextHighlight(start = 8, end = 12, color = HighlightColor.BLUE)
        )
        subtitleWithStatesFlow.value = baseSubtitleWithStates(highlights = highlights)
        noteFlow.value = listOf(
            HighlightNoteEntity(
                subtitleId = SUBTITLE_ID,
                highlightStart = 0,
                highlightEnd = 5,
                noteText = "saved note"
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf(
                TextHighlight(start = 0, end = 5, color = HighlightColor.YELLOW, note = "saved note"),
                TextHighlight(start = 8, end = 12, color = HighlightColor.BLUE, note = null)
            ),
            viewModel.uiState.value.highlights
        )
    }

    @Test
    fun saveHighlightNote_trimsAndPersistsUpdatedNote() = runTest {
        val existingHighlight = TextHighlight(start = 0, end = 5, color = HighlightColor.YELLOW)
        subtitleWithStatesFlow.value = baseSubtitleWithStates(highlights = listOf(existingHighlight))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveHighlightNote(existingHighlight, "  updated note  ")
        advanceUntilIdle()

        assertEquals("updated note", viewModel.uiState.value.highlights.single().note)
        verify(noteRepository).upsertHighlight(
            argThat { entity ->
                entity.subtitleId == SUBTITLE_ID &&
                    entity.highlightStart == 0 &&
                    entity.highlightEnd == 5 &&
                    entity.noteText == "updated note" &&
                    entity.createdAt > 0 &&
                    entity.updatedAt > 0
            }
        )
    }

    @Test
    fun applyHighlight_mergesRangesAndConcatenatesExistingNotes() = runTest {
        val highlights = listOf(
            TextHighlight(start = 0, end = 4, color = HighlightColor.RED),
            TextHighlight(start = 6, end = 10, color = HighlightColor.GREEN)
        )
        subtitleWithStatesFlow.value = baseSubtitleWithStates(highlights = highlights)
        noteFlow.value = listOf(
            HighlightNoteEntity(
                subtitleId = SUBTITLE_ID,
                highlightStart = 0,
                highlightEnd = 4,
                noteText = "First note"
            ),
            HighlightNoteEntity(
                subtitleId = SUBTITLE_ID,
                highlightStart = 6,
                highlightEnd = 10,
                noteText = "Second note"
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val merged = viewModel.applyHighlight(start = 3, end = 8, color = HighlightColor.BLUE)
        advanceUntilIdle()

        assertEquals(
            TextHighlight(
                start = 0,
                end = 10,
                color = HighlightColor.BLUE,
                note = "First note\n\nSecond note"
            ),
            merged
        )
        assertEquals(listOf(merged), viewModel.uiState.value.highlights)
    }

    @Test
    fun deleteHighlightNote_clearsUiStateAndDeletesPersistedNote() = runTest {
        val highlight = TextHighlight(start = 2, end = 7, color = HighlightColor.GREEN)
        subtitleWithStatesFlow.value = baseSubtitleWithStates(highlights = listOf(highlight))
        noteFlow.value = listOf(
            HighlightNoteEntity(
                subtitleId = SUBTITLE_ID,
                highlightStart = 2,
                highlightEnd = 7,
                noteText = "temporary"
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val loadedHighlight = viewModel.uiState.value.highlights.single()
        viewModel.deleteHighlightNote(loadedHighlight)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.highlights.single().note)
        verify(noteRepository).deleteHighlightByRange(SUBTITLE_ID, 2, 7)
    }

    @Test
    fun updateContent_clearsHighlightsAndDeletesAllNotes() = runTest {
        subtitleWithStatesFlow.value = baseSubtitleWithStates(highlights = emptyList())
        noteFlow.value = listOf(
            HighlightNoteEntity(
                subtitleId = SUBTITLE_ID,
                highlightStart = 0,
                highlightEnd = 4,
                noteText = "old note"
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateContent("Updated content")
        advanceUntilIdle()

        assertEquals(emptyList<TextHighlight>(), viewModel.uiState.value.highlights)
        verify(subtitleRepository).updateStudyContent(SUBTITLE_ID, "Updated content")
        verify(subtitleRepository).updateHighlights(SUBTITLE_ID, "")
        verify(noteRepository).deleteHighlightsBySubtitleId(SUBTITLE_ID)
    }

    @Test
    fun init_exposesLastStudyScrollFromReadingState() = runTest {
        subtitleWithStatesFlow.value = baseSubtitleWithStates(
            highlights = emptyList(),
            lastStudyScroll = 42
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(42, viewModel.uiState.value.lastStudyScroll)
    }

    @Test
    fun init_exposesZeroLastStudyScrollWhenNoReadingState() = runTest {
        subtitleWithStatesFlow.value = baseSubtitleWithStates(
            highlights = emptyList()
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.lastStudyScroll)
    }

    @Test
    fun updateLastStudyScroll_persistsScrollPercentToRepository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateLastStudyScroll(37)
        advanceUntilIdle()

        verify(subtitleRepository).updateLastStudyScroll(SUBTITLE_ID, 37)
    }

    @Test
    fun updateLastStudyScroll_coercesNegativeValuesToZero() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateLastStudyScroll(-5)
        advanceUntilIdle()

        verify(subtitleRepository).updateLastStudyScroll(SUBTITLE_ID, 0)
    }

    @Test
    fun updateReadingProgress_persistsPercentAndPageProgress() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateReadingProgress(percent = 15, currentPage = 5, totalPages = 15)
        advanceUntilIdle()

        verify(subtitleRepository).updateReadingProgress(
            eq(SUBTITLE_ID),
            eq(15),
            eq(5),
            eq(15)
        )
    }

    private fun createViewModel(): ReaderViewModel {
        return ReaderViewModel(
            stringProvider = stringProvider,
            appContext = mock(),
            subtitleRepository = subtitleRepository,
            noteRepository = noteRepository,
            userPreferencesRepository = createUserPreferencesRepository(),
            subtitleId = SUBTITLE_ID,
            widgetUpdater = { _ -> }
        )
    }

    private fun createUserPreferencesRepository(): UserPreferencesRepository {
        val context = mock<Context>()
        val preferences = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()

        whenever(context.getSharedPreferences(any(), eq(Context.MODE_PRIVATE))).thenReturn(preferences)
        whenever(preferences.getStringSet(any(), any())).thenReturn(emptySet())
        whenever(preferences.getFloat(any(), any())).thenAnswer { it.arguments[1] as Float }
        whenever(preferences.getString(any(), any())).thenAnswer { it.arguments[1] as String? }
        whenever(preferences.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.putStringSet(any(), any())).thenReturn(editor)
        whenever(editor.putFloat(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)

        return UserPreferencesRepository(context)
    }

    private fun baseSubtitleEntity(highlights: List<TextHighlight>): SubtitleEntity {
        return SubtitleEntity(
            id = SUBTITLE_ID,
            videoId = "video-id",
            title = "Title",
            languageCode = "en",
            content = "Hello world more text",
            highlights = serializeHighlights(highlights)
        )
    }

    private fun baseSubtitleWithStates(
        highlights: List<TextHighlight>,
        lastStudyScroll: Int = 0
    ): SubtitleWithStates {
        return SubtitleWithStates(
            subtitle = baseSubtitleEntity(highlights),
            readingState = if (lastStudyScroll > 0) {
                SubtitleReadingStateEntity(subtitleId = SUBTITLE_ID, lastStudyScroll = lastStudyScroll)
            } else null,
            aiCleaningState = null
        )
    }

    companion object {
        private const val SUBTITLE_ID = 42L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
