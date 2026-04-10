package com.deedeedev.ytreader.ui.home

import android.content.Context
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.CollectionRepository
import com.deedeedev.ytreader.data.PersistedLibraryFilters
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.AppDatabase
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.data.local.VideoDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appContext: Context
    private lateinit var youtubeRepository: YoutubeRepository
    private lateinit var database: AppDatabase
    private lateinit var subtitleDao: SubtitleDao
    private lateinit var videoDao: VideoDao
    private lateinit var highlightNoteDao: HighlightNoteDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var collectionRepository: CollectionRepository
    private lateinit var collectionsFlow: MutableStateFlow<List<VideoCollection>>

    @Before
    fun setUp() {
        appContext = mock()
        youtubeRepository = mock()
        database = mock()
        subtitleDao = mock()
        videoDao = mock()
        highlightNoteDao = mock()
        bookmarkDao = mock()
        userPreferencesRepository = mock()
        collectionRepository = mock()

        collectionsFlow = MutableStateFlow(emptyList())
        whenever(collectionRepository.collections).thenReturn(collectionsFlow)
        whenever(userPreferencesRepository.getLibraryFilterState()).thenReturn(
            PersistedLibraryFilters()
        )
        whenever(appContext.getString(R.string.library_thumbnail_downloaded)).thenReturn("Downloaded")
        whenever(appContext.getString(R.string.library_thumbnail_download_failed)).thenReturn("Failed")
        whenever(appContext.getString(R.string.download_failed)).thenReturn("Download failed")
        whenever(appContext.getString(R.string.matching_subtitle_not_found)).thenReturn("Not found")
        whenever(appContext.getString(R.string.channel_unknown)).thenReturn("Unknown")
    }

    private fun createViewModel(): LibraryViewModel {
        return LibraryViewModel(
            appContext,
            youtubeRepository,
            database,
            subtitleDao,
            videoDao,
            highlightNoteDao,
            bookmarkDao,
            userPreferencesRepository,
            collectionRepository
        )
    }

    @Test
    fun initialUiState_hasDefaultFilters() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertNull(state.selectedChannelFilter)
        assertEquals(LibraryVisibilityFilter.ALL, state.libraryVisibilityFilter)
        assertEquals(ReadStatusFilter.ALL, state.libraryReadStatusFilter)
        assertEquals(SortOption.DOWNLOADED, state.sortOption)
        assertFalse(state.isAscending)
    }

    @Test
    fun setChannelFilter_updatesState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setChannelFilter("TestChannel")
        assertEquals("TestChannel", viewModel.uiState.value.selectedChannelFilter)
    }

    @Test
    fun setChannelFilter_null_clearsFilter() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setChannelFilter("TestChannel")
        viewModel.setChannelFilter(null)
        assertNull(viewModel.uiState.value.selectedChannelFilter)
    }

    @Test
    fun setLibraryVisibilityFilter_updatesState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setLibraryVisibilityFilter(LibraryVisibilityFilter.IN_COLLECTIONS)
        assertEquals(LibraryVisibilityFilter.IN_COLLECTIONS, viewModel.uiState.value.libraryVisibilityFilter)
    }

    @Test
    fun setLibraryReadStatusFilter_updatesState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setLibraryReadStatusFilter(ReadStatusFilter.READ)
        assertEquals(ReadStatusFilter.READ, viewModel.uiState.value.libraryReadStatusFilter)
    }

    @Test
    fun setSortOption_updatesState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setSortOption(SortOption.TITLE)
        assertEquals(SortOption.TITLE, viewModel.uiState.value.sortOption)
    }

    @Test
    fun toggleSortOrder_flipsState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAscending)
        viewModel.toggleSortOrder()
        assertTrue(viewModel.uiState.value.isAscending)
        viewModel.toggleSortOrder()
        assertFalse(viewModel.uiState.value.isAscending)
    }

    @Test
    fun resetVideoProgress_delegatesToDao() = runTest {
        val viewModel = createViewModel()
        viewModel.resetVideoProgress("video123")
        advanceUntilIdle()
        verify(subtitleDao).resetReadingProgressForVideo("video123")
    }

    @Test
    fun markVideoAsRead_delegatesToDao() = runTest {
        val viewModel = createViewModel()
        viewModel.markVideoAsRead("video123")
        advanceUntilIdle()
        verify(subtitleDao).markVideoAsRead("video123")
    }

    @Test
    fun deleteVideoPermanently_removesFromCollectionsAndDeletesSubtitles() = runTest {
        val viewModel = createViewModel()
        viewModel.deleteVideoPermanently("video123")
        advanceUntilIdle()
        verify(collectionRepository).removeVideoFromAllCollections("video123")
        verify(subtitleDao).deleteByVideoId("video123")
    }

    @Test
    fun deleteSubtitle_deletesSubtitle() = runTest {
        val viewModel = createViewModel()
        whenever(subtitleDao.countByVideoId("video123")).thenReturn(2)
        val subtitle = SubtitleEntity(
            id = 1L, videoId = "video123", videoUrl = "",
            title = "Test", channelName = "Channel",
            languageCode = "en", subtitleTrackId = "t1",
            trackIdentity = "t1", isAutoGenerated = false,
            content = "", fontSize = 16f, fontFamily = "Default",
            uploadDate = 0L
        )
        viewModel.deleteSubtitle(subtitle)
        advanceUntilIdle()
        verify(subtitleDao).delete(subtitle)
    }

    @Test
    fun deleteSubtitle_lastSubtitleForVideo_removesFromCollections() = runTest {
        val viewModel = createViewModel()
        whenever(subtitleDao.countByVideoId("video123")).thenReturn(1)
        val subtitle = SubtitleEntity(
            id = 1L, videoId = "video123", videoUrl = "",
            title = "Test", channelName = "Channel",
            languageCode = "en", subtitleTrackId = "t1",
            trackIdentity = "t1", isAutoGenerated = false,
            content = "", fontSize = 16f, fontFamily = "Default",
            uploadDate = 0L
        )
        viewModel.deleteSubtitle(subtitle)
        advanceUntilIdle()
        verify(collectionRepository).removeVideoFromAllCollections("video123")
        verify(subtitleDao).delete(subtitle)
    }

    @Test
    fun removeLibraryItem_updatesVisibility() = runTest {
        val viewModel = createViewModel()
        val subtitle = SubtitleEntity(
            id = 1L, videoId = "video123", videoUrl = "",
            title = "Test", channelName = "Channel",
            languageCode = "en", subtitleTrackId = "t1",
            trackIdentity = "t1", isAutoGenerated = false,
            content = "", fontSize = 16f, fontFamily = "Default",
            uploadDate = 0L
        )
        whenever(subtitleDao.countLibraryEntriesByVideoId("video123")).thenReturn(0)
        whenever(collectionRepository.isVideoInAnyCollection("video123")).thenReturn(false)
        whenever(videoDao.getByVideoId("video123")).thenReturn(null)

        viewModel.removeLibraryItem(listOf(subtitle))
        advanceUntilIdle()

        verify(subtitleDao).updateLibraryVisibility("video123", false)
    }

    @Test
    fun restoreLibraryItem_restoresVisibility() = runTest {
        val viewModel = createViewModel()
        val subtitle = SubtitleEntity(
            id = 1L, videoId = "video123", videoUrl = "",
            title = "Test", channelName = "Channel",
            languageCode = "en", subtitleTrackId = "t1",
            trackIdentity = "t1", isAutoGenerated = false,
            content = "", fontSize = 16f, fontFamily = "Default",
            uploadDate = 0L
        )

        viewModel.restoreLibraryItem(listOf(subtitle))
        advanceUntilIdle()

        verify(subtitleDao).updateLibraryVisibility("video123", true)
    }

    @Test
    fun createCollection_blankName_returnsFalse() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.createCollection("   "))
    }

    @Test
    fun createCollection_duplicateName_returnsFalse() = runTest {
        collectionsFlow.value = listOf(VideoCollection("c1", "Existing", emptyList()))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.createCollection("Existing"))
    }

    @Test
    fun createCollection_validName_returnsTrue() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.createCollection("New Collection"))
        advanceUntilIdle()
        verify(collectionRepository).createCollection("New Collection")
    }

    @Test
    fun addVideoToCollection_unknownCollection_returnsFalse() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.addVideoToCollection("unknown", "video123"))
    }

    @Test
    fun addVideoToCollection_alreadyInCollection_returnsTrue() = runTest {
        collectionsFlow.value = listOf(VideoCollection("c1", "Test", listOf("video123")))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.addVideoToCollection("c1", "video123"))
    }

    @Test
    fun addVideoToCollection_success_delegatesToRepository() = runTest {
        collectionsFlow.value = listOf(VideoCollection("c1", "Test", emptyList()))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.addVideoToCollection("c1", "video123"))
        advanceUntilIdle()
        verify(collectionRepository).addVideoToCollection("c1", "video123")
    }

    @Test
    fun getPreferredSubtitleIdForVideo_returnsSubtitleId() = runTest {
        val subtitle = SubtitleEntity(
            id = 42L, videoId = "video123", videoUrl = "",
            title = "Test", channelName = "Channel",
            languageCode = "en", subtitleTrackId = "t1",
            trackIdentity = "t1", isAutoGenerated = false,
            content = "", fontSize = 16f, fontFamily = "Default",
            uploadDate = 0L
        )
        whenever(subtitleDao.getPreferredSubtitleForVideo("video123")).thenReturn(subtitle)

        val viewModel = createViewModel()
        advanceUntilIdle()
        val result = viewModel.getPreferredSubtitleIdForVideo("video123")

        assertEquals(42L, result)
    }

    @Test
    fun filterStatePersistence_calledOnFilterChange() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setChannelFilter("Channel")
        verify(userPreferencesRepository).saveLibraryFilterState(
            org.mockito.kotlin.check { filters ->
                assertEquals("Channel", filters.selectedChannelFilter)
            }
        )
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
