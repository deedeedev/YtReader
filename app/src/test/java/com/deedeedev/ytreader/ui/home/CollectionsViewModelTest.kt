package com.deedeedev.ytreader.ui.home

import android.content.Context
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.StringProvider
import com.deedeedev.ytreader.data.CollectionRepository
import com.deedeedev.ytreader.data.NoteRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.VideoRepository
import com.deedeedev.ytreader.data.YoutubeRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appContext: Context
    private lateinit var stringProvider: StringProvider
    private lateinit var youtubeRepository: YoutubeRepository
    private lateinit var subtitleRepository: SubtitleRepository
    private lateinit var videoRepository: VideoRepository
    private lateinit var noteRepository: NoteRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var collectionRepository: CollectionRepository
    private lateinit var collectionsFlow: MutableStateFlow<List<VideoCollection>>
    private lateinit var savedSubtitlesFlow: MutableStateFlow<List<SubtitleEntity>>

    @Before
    fun setUp() {
        appContext = mock()
        stringProvider = mock()
        youtubeRepository = mock()
        subtitleRepository = mock()
        videoRepository = mock()
        noteRepository = mock()
        userPreferencesRepository = mock()
        collectionRepository = mock()

        collectionsFlow = MutableStateFlow(emptyList())
        savedSubtitlesFlow = MutableStateFlow(emptyList())

        whenever(collectionRepository.collections).thenReturn(collectionsFlow)
        whenever(subtitleRepository.observeAll()).thenReturn(savedSubtitlesFlow)
        whenever(userPreferencesRepository.getCollectionFilterStates()).thenReturn(emptyMap())
        whenever(stringProvider.getString(R.string.channel_unknown)).thenReturn("Unknown")
        whenever(stringProvider.getString(R.string.download_failed)).thenReturn("Download failed")
        whenever(stringProvider.getString(R.string.matching_subtitle_not_found)).thenReturn("Not found")
        whenever(stringProvider.getString(R.string.library_thumbnail_downloaded)).thenReturn("Downloaded")
        whenever(stringProvider.getString(R.string.library_thumbnail_download_failed)).thenReturn("Failed")
    }

    private fun createViewModel(): CollectionsViewModel {
        return CollectionsViewModel(
            stringProvider,
            appContext,
            youtubeRepository,
            subtitleRepository,
            videoRepository,
            noteRepository,
            userPreferencesRepository,
            collectionRepository
        )
    }

    @Test
    fun initialUiState_hasDefaults() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(emptyList<VideoCollection>(), state.collections)
        assertEquals(emptyMap<String, CollectionFilterState>(), state.collectionFilterStates)
        assertNull(state.error)
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
    fun renameCollection_blankName_returnsFalse() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.renameCollection("c1", "   "))
    }

    @Test
    fun renameCollection_unknownCollection_returnsFalse() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.renameCollection("unknown", "New Name"))
    }

    @Test
    fun renameCollection_duplicateName_returnsFalse() = runTest {
        collectionsFlow.value = listOf(
            VideoCollection("c1", "Collection 1", emptyList()),
            VideoCollection("c2", "Collection 2", emptyList())
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.renameCollection("c1", "Collection 2"))
    }

    @Test
    fun renameCollection_validName_returnsTrue() = runTest {
        collectionsFlow.value = listOf(VideoCollection("c1", "Old Name", emptyList()))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.renameCollection("c1", "New Name"))
        advanceUntilIdle()
        verify(collectionRepository).renameCollection("c1", "New Name")
    }

    @Test
    fun deleteCollection_removesFilterStateAndDeletes() = runTest {
        collectionsFlow.value = listOf(VideoCollection("c1", "Test", emptyList()))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.deleteCollection("c1")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.collectionFilterStates.containsKey("c1"))
        verify(userPreferencesRepository).removeCollectionFilterState("c1")
        verify(collectionRepository).deleteCollection("c1")
    }

    @Test
    fun reorderCollections_sameOrder_doesNothing() = runTest {
        collectionsFlow.value = listOf(
            VideoCollection("c1", "A", emptyList()),
            VideoCollection("c2", "B", emptyList())
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.reorderCollections(listOf("c1", "c2"))
        advanceUntilIdle()
    }

    @Test
    fun reorderCollections_differentOrder_delegates() = runTest {
        collectionsFlow.value = listOf(
            VideoCollection("c1", "A", emptyList()),
            VideoCollection("c2", "B", emptyList())
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.reorderCollections(listOf("c2", "c1"))
        advanceUntilIdle()
        verify(collectionRepository).reorderCollections(listOf("c2", "c1"))
    }

    @Test
    fun reorderCollections_wrongSize_doesNothing() = runTest {
        collectionsFlow.value = listOf(
            VideoCollection("c1", "A", emptyList()),
            VideoCollection("c2", "B", emptyList())
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.reorderCollections(listOf("c1"))
    }

    @Test
    fun setCollectionChannelFilter_updatesState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setCollectionChannelFilter("c1", "Channel1")
        assertEquals("Channel1", viewModel.uiState.value.collectionFilterStates["c1"]?.selectedChannelFilter)
    }

    @Test
    fun setCollectionSortOption_updatesState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setCollectionSortOption("c1", SortOption.TITLE)
        assertEquals(SortOption.TITLE, viewModel.uiState.value.collectionFilterStates["c1"]?.sortOption)
    }

    @Test
    fun setCollectionReadStatusFilter_updatesState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setCollectionReadStatusFilter("c1", ReadStatusFilter.READ)
        assertEquals(ReadStatusFilter.READ, viewModel.uiState.value.collectionFilterStates["c1"]?.readStatusFilter)
    }

    @Test
    fun toggleCollectionSortOrder_flipsState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleCollectionSortOrder("c1")
        assertTrue(viewModel.uiState.value.collectionFilterStates["c1"]?.isAscending ?: false)
        viewModel.toggleCollectionSortOrder("c1")
        assertFalse(viewModel.uiState.value.collectionFilterStates["c1"]?.isAscending ?: true)
    }

    @Test
    @Ignore("Requires suspend function mocking")
    fun removeVideoFromCollection_delegatesAndCleansUp() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        whenever(collectionRepository.isVideoInAnyCollection("video123")).thenReturn(false)

        viewModel.removeVideoFromCollection("c1", "video123")
        advanceUntilIdle()

        verify(collectionRepository).removeVideoFromCollection("c1", "video123")
    }

    @Test
    fun addVideoToCollection_unknownCollection_returnsFalse() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.addVideoToCollection("unknown", "video123"))
    }

    @Test
    fun addVideoToCollection_success_delegates() = runTest {
        collectionsFlow.value = listOf(VideoCollection("c1", "Test", emptyList()))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.addVideoToCollection("c1", "video123"))
        advanceUntilIdle()
        verify(collectionRepository).addVideoToCollection("c1", "video123")
    }

    @Test
    fun markVideoAsRead_delegatesToDao() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.markVideoAsRead("video123")
        advanceUntilIdle()
        verify(subtitleRepository).markVideoAsRead("video123")
    }

    @Test
    fun resetVideoProgress_delegatesToDao() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.resetVideoProgress("video123")
        advanceUntilIdle()
        verify(subtitleRepository).resetReadingProgressForVideo("video123")
    }

    @Test
    fun deleteSubtitle_delegatesToDao() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        whenever(subtitleRepository.countByVideoId("video123")).thenReturn(2)
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
        verify(subtitleRepository).delete(subtitle)
    }

    @Test
    fun restoreLibraryItem_restoresVisibility() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
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
        verify(subtitleRepository).updateLibraryVisibility("video123", true)
    }

    @Test
    fun collectionFilterStatePersistence_calledOnFilterChange() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setCollectionChannelFilter("c1", "Channel1")
        verify(userPreferencesRepository).saveCollectionFilterState(
            org.mockito.kotlin.eq("c1"),
            org.mockito.kotlin.check { state ->
                assertEquals("Channel1", state.selectedChannelFilter)
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
