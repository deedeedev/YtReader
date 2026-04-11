package com.deedeedev.ytreader.ui.reader

import android.content.Context
import android.content.SharedPreferences
import com.deedeedev.ytreader.data.NoteRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.data.local.HighlightNoteEntity
import com.deedeedev.ytreader.data.local.SubtitleEntity
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
    private lateinit var subtitleFlow: MutableStateFlow<SubtitleEntity?>
    private lateinit var noteFlow: MutableStateFlow<List<HighlightNoteEntity>>
    private lateinit var bookmarkFlow: MutableStateFlow<List<BookmarkEntity>>

    @Before
    fun setUp() {
        subtitleRepository = mock()
        noteRepository = mock()
        subtitleFlow = MutableStateFlow(baseSubtitle(highlights = emptyList()))
        noteFlow = MutableStateFlow(emptyList())
        bookmarkFlow = MutableStateFlow(emptyList())

        whenever(subtitleRepository.observeById(SUBTITLE_ID)).thenReturn(subtitleFlow)
        whenever(noteRepository.observeHighlightsBySubtitleId(SUBTITLE_ID)).thenReturn(noteFlow)
        whenever(noteRepository.observeBookmarksBySubtitleId(SUBTITLE_ID)).thenReturn(bookmarkFlow)
    }

    @Test
    fun init_combinesPersistedHighlightNotesIntoUiState() = runTest {
        val highlights = listOf(
            TextHighlight(start = 0, end = 5, color = HighlightColor.YELLOW),
            TextHighlight(start = 8, end = 12, color = HighlightColor.BLUE)
        )
        subtitleFlow.value = baseSubtitle(highlights = highlights)
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
        subtitleFlow.value = baseSubtitle(highlights = listOf(existingHighlight))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveHighlightNote(existingHighlight, "  updated note  ")
        advanceUntilIdle()

        assertEquals("updated note", viewModel.uiState.value.highlights.single().note)
        verify(noteRepository).upsertHighlight(
            HighlightNoteEntity(
                subtitleId = SUBTITLE_ID,
                highlightStart = 0,
                highlightEnd = 5,
                noteText = "updated note"
            )
        )
    }

    @Test
    fun applyHighlight_mergesRangesAndConcatenatesExistingNotes() = runTest {
        val highlights = listOf(
            TextHighlight(start = 0, end = 4, color = HighlightColor.RED),
            TextHighlight(start = 6, end = 10, color = HighlightColor.GREEN)
        )
        subtitleFlow.value = baseSubtitle(highlights = highlights)
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
        subtitleFlow.value = baseSubtitle(highlights = listOf(highlight))
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
        subtitleFlow.value = baseSubtitle(highlights = emptyList())
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

    private fun createViewModel(): ReaderViewModel {
        return ReaderViewModel(
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

    private fun baseSubtitle(highlights: List<TextHighlight>): SubtitleEntity {
        return SubtitleEntity(
            id = SUBTITLE_ID,
            videoId = "video-id",
            title = "Title",
            languageCode = "en",
            content = "Hello world more text",
            highlights = serializeHighlights(highlights)
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
