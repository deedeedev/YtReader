package com.deedeedev.ytreader.ui.home

import android.content.Context
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.SearchHistoryRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.VideoRepository
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.local.SearchHistoryEntity
import com.deedeedev.ytreader.data.local.SubtitleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appContext: Context
    private lateinit var youtubeRepository: YoutubeRepository
    private lateinit var subtitleRepository: SubtitleRepository
    private lateinit var videoRepository: VideoRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var searchHistoryRepository: SearchHistoryRepository
    private lateinit var favoriteLanguagesFlow: MutableStateFlow<Set<String>>
    private lateinit var searchHistoryFlow: MutableStateFlow<List<SearchHistoryEntity>>
    private lateinit var savedSubtitlesFlow: MutableStateFlow<List<SubtitleEntity>>

    @Before
    fun setUp() {
        appContext = mock()
        youtubeRepository = mock()
        subtitleRepository = mock()
        videoRepository = mock()
        userPreferencesRepository = mock()
        searchHistoryRepository = mock()

        favoriteLanguagesFlow = MutableStateFlow(emptySet())
        searchHistoryFlow = MutableStateFlow(emptyList())
        savedSubtitlesFlow = MutableStateFlow(emptyList())

        whenever(userPreferencesRepository.favoriteLanguages).thenReturn(favoriteLanguagesFlow)
        whenever(subtitleRepository.observeAll()).thenReturn(savedSubtitlesFlow)
        whenever(searchHistoryRepository.observeAll()).thenReturn(searchHistoryFlow)
        whenever(userPreferencesRepository.defaultFontSize).thenReturn(MutableStateFlow(16f))
        whenever(userPreferencesRepository.fontFamily).thenReturn(MutableStateFlow("Default"))
        whenever(appContext.getString(R.string.channel_unknown)).thenReturn("Unknown")
        whenever(appContext.getString(R.string.unknown_error)).thenReturn("Unknown error")
        whenever(appContext.getString(R.string.download_failed)).thenReturn("Download failed")
        whenever(appContext.getString(R.string.library_unknown_code)).thenReturn("und")
        whenever(appContext.getString(R.string.matching_subtitle_not_found)).thenReturn("Not found")
        whenever(appContext.getString(R.string.library_thumbnail_downloaded)).thenReturn("Downloaded")
        whenever(appContext.getString(R.string.library_thumbnail_download_failed)).thenReturn("Failed")
    }

    private fun createStreamInfo(
        name: String = "Test Video",
        uploaderName: String? = "Test Channel",
        url: String = "https://youtube.com/watch?v=test"
    ): org.schabi.newpipe.extractor.stream.StreamInfo {
        val info = mock<org.schabi.newpipe.extractor.stream.StreamInfo>()
        whenever(info.name).thenReturn(name)
        whenever(info.uploaderName).thenReturn(uploaderName)
        whenever(info.url).thenReturn(url)
        whenever(info.subtitles).thenReturn(emptyList())
        whenever(info.thumbnails).thenReturn(emptyList())
        whenever(info.uploadDate).thenReturn(null)
        return info
    }

    private fun createViewModel(): SearchViewModel {
        return SearchViewModel(
            appContext,
            youtubeRepository,
            subtitleRepository,
            videoRepository,
            userPreferencesRepository,
            searchHistoryRepository
        )
    }

    @Test
    fun initialUiState_hasDefaults() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertEquals("", state.url)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNull(state.streamInfo)
        assertFalse(state.showHistory)
    }

    @Test
    fun onUrlChange_updatesUrl_clearsError() = runTest {
        val viewModel = createViewModel()
        viewModel.onUrlChange("https://youtube.com/watch?v=test")
        assertEquals("https://youtube.com/watch?v=test", viewModel.uiState.value.url)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun onUrlChange_nullConvertsToEmpty() = runTest {
        val viewModel = createViewModel()
        viewModel.onUrlChange(null)
        assertEquals("", viewModel.uiState.value.url)
    }

    @Test
    fun onUrlChange_emptyString_clearsStreamInfoAndHistory() = runTest {
        val viewModel = createViewModel()
        viewModel.onUrlChange("https://youtube.com/watch?v=test")
        viewModel.onUrlChange("")
        assertNull(viewModel.uiState.value.streamInfo)
        assertFalse(viewModel.uiState.value.showHistory)
    }

    @Test
    fun searchVideo_blankUrl_doesNothing() = runTest {
        val viewModel = createViewModel()
        viewModel.searchVideo()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun searchVideo_success_setsStreamInfo() = runTest {
        val viewModel = createViewModel()
        val streamInfo = createStreamInfo()
        whenever(youtubeRepository.getStreamInfo("https://youtube.com/watch?v=test")).thenReturn(streamInfo)
        whenever(searchHistoryRepository.getCount()).thenReturn(0)

        viewModel.onUrlChange("https://youtube.com/watch?v=test")
        viewModel.searchVideo()
        advanceUntilIdle()

        assertEquals(streamInfo, viewModel.uiState.value.streamInfo)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun searchVideo_error_setsErrorMessage() = runTest {
        val viewModel = createViewModel()
        whenever(youtubeRepository.getStreamInfo("https://youtube.com/watch?v=bad"))
            .thenThrow(RuntimeException("Network error"))

        viewModel.onUrlChange("https://youtube.com/watch?v=bad")
        viewModel.searchVideo()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Network error", viewModel.uiState.value.error)
    }

    @Test
    fun toggleHistory_togglesState() = runTest {
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.showHistory)
        viewModel.toggleHistory()
        assertTrue(viewModel.uiState.value.showHistory)
        viewModel.toggleHistory()
        assertFalse(viewModel.uiState.value.showHistory)
    }

    @Test
    fun deleteHistoryEntry_callsDaoDelete() = runTest {
        val viewModel = createViewModel()
        viewModel.deleteHistoryEntry(42L)
        advanceUntilIdle()
        verify(searchHistoryRepository).delete(42L)
    }

    @Test
    fun searchFromHistory_setsUrlAndSearches() = runTest {
        val viewModel = createViewModel()
        val streamInfo = createStreamInfo()
        whenever(youtubeRepository.getStreamInfo("https://youtube.com/watch?v=test")).thenReturn(streamInfo)
        whenever(searchHistoryRepository.getCount()).thenReturn(0)

        viewModel.searchFromHistory("https://youtube.com/watch?v=test")
        advanceUntilIdle()

        assertEquals(streamInfo, viewModel.uiState.value.streamInfo)
    }

    @Test
    fun saveToSearchHistory_capsAt100() = runTest {
        val viewModel = createViewModel()
        whenever(searchHistoryRepository.getCount()).thenReturn(105)

        val streamInfo = createStreamInfo()
        whenever(youtubeRepository.getStreamInfo(any())).thenReturn(streamInfo)

        viewModel.onUrlChange("https://youtube.com/watch?v=test")
        viewModel.searchVideo()
        advanceUntilIdle()

        verify(searchHistoryRepository).deleteOldest(5)
    }

    @Test
    fun toggleFavoriteLanguage_delegatesToRepository() = runTest {
        val viewModel = createViewModel()
        viewModel.toggleFavoriteLanguage("en")
        verify(userPreferencesRepository).toggleFavoriteLanguage("en")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    class MainDispatcherRule : TestWatcher() {
        private val testDispatcher: TestDispatcher = StandardTestDispatcher()

        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
