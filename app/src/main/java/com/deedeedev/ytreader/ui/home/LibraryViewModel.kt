package com.deedeedev.ytreader.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.CollectionRepository
import com.deedeedev.ytreader.data.NoteRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.PersistedLibraryFilters
import com.deedeedev.ytreader.data.VideoRepository
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.local.AppDatabase
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class LibraryUiState(
    val selectedChannelFilter: String? = null,
    val libraryVisibilityFilter: LibraryVisibilityFilter = LibraryVisibilityFilter.ALL,
    val libraryReadStatusFilter: ReadStatusFilter = ReadStatusFilter.ALL,
    val sortOption: SortOption = SortOption.DOWNLOADED,
    val isAscending: Boolean = false,
    val downloadingSubtitleIds: Set<Long> = emptySet(),
    val downloadingThumbnailVideoIds: Set<String> = emptySet(),
    val collections: List<VideoCollection> = emptyList(),
    val error: String? = null
)

sealed interface LibraryEvent {
    data class ShowMessage(val message: String) : LibraryEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val appContext: Context,
    private val youtubeRepository: YoutubeRepository,
    private val subtitleRepository: SubtitleRepository,
    private val videoRepository: VideoRepository,
    private val noteRepository: NoteRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    val libraryChannels: StateFlow<List<String>> = subtitleRepository.observeLibraryChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryItems: StateFlow<List<LibraryItem>> = uiState
        .map { state ->
            LibraryQueryParams(
                channelName = state.selectedChannelFilter,
                visibilityFilter = state.libraryVisibilityFilter,
                readStatusFilter = state.libraryReadStatusFilter,
                sortOption = state.sortOption,
                isAscending = state.isAscending
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { params ->
            subtitleRepository.observeLibraryVideoRows(
                channelName = params.channelName,
                sortOption = params.sortOption.name,
                isAscending = params.isAscending
            ).flatMapLatest { rows ->
                observeLibraryItemsForRows(subtitleRepository, collectionRepository, rows)
                    .map { items ->
                        items.filterByVisibility(params.visibilityFilter)
                            .filterByReadStatus(params.readStatusFilter)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadCollections()
    }

    private fun loadCollections() {
        viewModelScope.launch {
            collectionRepository.collections.collect { collections ->
                _uiState.update { it.copy(collections = collections) }
            }
        }
    }

    fun setChannelFilter(channelName: String?) {
        _uiState.update {
            it.copy(selectedChannelFilter = channelName).also(::persistLibraryFilterState)
        }
    }

    fun setLibraryVisibilityFilter(visibilityFilter: LibraryVisibilityFilter) {
        _uiState.update {
            it.copy(libraryVisibilityFilter = visibilityFilter).also(::persistLibraryFilterState)
        }
    }

    fun setLibraryReadStatusFilter(readStatusFilter: ReadStatusFilter) {
        _uiState.update {
            it.copy(libraryReadStatusFilter = readStatusFilter).also(::persistLibraryFilterState)
        }
    }

    fun setSortOption(sortOption: SortOption) {
        _uiState.update {
            it.copy(sortOption = sortOption).also(::persistLibraryFilterState)
        }
    }

    fun toggleSortOrder() {
        _uiState.update {
            it.copy(isAscending = !it.isAscending).also(::persistLibraryFilterState)
        }
    }

    suspend fun getPreferredSubtitleIdForVideo(videoId: String): Long? {
        return subtitleRepository.getPreferredSubtitleForVideo(videoId)?.id
    }

    fun downloadSubtitleAgain(subtitle: SubtitleEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            _uiState.update { state ->
                state.copy(downloadingSubtitleIds = state.downloadingSubtitleIds + subtitle.id)
            }
            try {
                val info = youtubeRepository.getStreamInfo(resolveVideoLookupUrl(subtitle))
                val matchingSubtitle = SubtitleIdentityMatcher.findMatchingStream(
                    savedSubtitle = subtitle,
                    streams = info.subtitles
                )
                    ?: throw IllegalStateException(
                        appContext.getString(R.string.matching_subtitle_not_found)
                    )

                val subtitleContent = matchingSubtitle.content
                val rawContent = if (matchingSubtitle.isUrl) {
                    youtubeRepository.downloadSubtitle(subtitleContent)
                } else {
                    subtitleContent
                }

                subtitleRepository.replaceContentForRedownload(
                    id = subtitle.id,
                    content = rawContent,
                    createdAt = System.currentTimeMillis()
                )
                noteRepository.deleteHighlightsBySubtitleId(subtitle.id)
                noteRepository.deleteBookmarksBySubtitleId(subtitle.id)
                upsertVideoMetadata(
                    videoRepository = videoRepository,
                    youtubeRepository = youtubeRepository,
                    appContext = appContext,
                    videoId = subtitle.videoId,
                    fallbackVideoUrl = displayUrlFor(subtitle.videoId, subtitle.videoUrl),
                    fallbackTitle = info.name,
                    fallbackChannelName = info.uploaderName ?: appContext.getString(R.string.channel_unknown),
                    fallbackUploadDate = info.uploadDate?.instant?.toEpochMilli() ?: 0L,
                    info = info
                )
                _uiState.update { it.copy(error = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: appContext.getString(R.string.download_failed)
                    )
                }
            } finally {
                _uiState.update { state ->
                    state.copy(downloadingSubtitleIds = state.downloadingSubtitleIds - subtitle.id)
                }
            }
        }
    }

    fun downloadThumbnailForVideo(
        videoId: String,
        videoUrl: String,
        title: String,
        channelName: String,
        uploadDate: Long
    ) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(downloadingThumbnailVideoIds = state.downloadingThumbnailVideoIds + videoId)
            }
            try {
                val info = youtubeRepository.getStreamInfo(displayUrlFor(videoId, videoUrl))
                upsertVideoMetadata(
                    videoRepository = videoRepository,
                    youtubeRepository = youtubeRepository,
                    appContext = appContext,
                    videoId = videoId,
                    fallbackVideoUrl = displayUrlFor(videoId, videoUrl),
                    fallbackTitle = info.name.ifBlank { title },
                    fallbackChannelName = (info.uploaderName ?: channelName).ifBlank { channelName },
                    fallbackUploadDate = info.uploadDate?.instant?.toEpochMilli() ?: uploadDate,
                    info = info
                )
                _uiState.update { it.copy(error = null) }
                _events.tryEmit(LibraryEvent.ShowMessage(appContext.getString(R.string.library_thumbnail_downloaded)))
            } catch (e: Exception) {
                val errorMessage = e.message ?: appContext.getString(R.string.library_thumbnail_download_failed)
                _uiState.update {
                    it.copy(error = errorMessage)
                }
                _events.tryEmit(LibraryEvent.ShowMessage(errorMessage))
            } finally {
                _uiState.update { state ->
                    state.copy(downloadingThumbnailVideoIds = state.downloadingThumbnailVideoIds - videoId)
                }
            }
        }
    }

    fun removeLibraryItem(subtitles: List<SubtitleEntity>) {
        viewModelScope.launch {
            val videoId = subtitles.firstOrNull()?.videoId ?: return@launch
            subtitleRepository.updateLibraryVisibility(videoId, false)
            deleteVideoIfUnreferenced(subtitleRepository, videoRepository, collectionRepository, appContext, videoId)
        }
    }

    fun restoreLibraryItem(subtitles: List<SubtitleEntity>) {
        viewModelScope.launch {
            val videoId = subtitles.firstOrNull()?.videoId ?: return@launch
            subtitles.forEach { subtitle ->
                subtitleRepository.upsertByIdentity(subtitle.copy(isInLibrary = true))
            }
            subtitleRepository.updateLibraryVisibility(videoId, true)
        }
    }

    fun resetVideoProgress(videoId: String) {
        viewModelScope.launch {
            subtitleRepository.resetReadingProgressForVideo(videoId)
        }
    }

    fun markVideoAsRead(videoId: String) {
        viewModelScope.launch {
            subtitleRepository.markVideoAsRead(videoId)
        }
    }

    fun deleteVideoPermanently(videoId: String) {
        viewModelScope.launch {
            collectionRepository.removeVideoFromAllCollections(videoId)
            subtitleRepository.deleteByVideoId(videoId)
        }
    }

    fun deleteSubtitle(subtitle: SubtitleEntity) {
        viewModelScope.launch {
            val subtitleCountForVideo = subtitleRepository.countByVideoId(subtitle.videoId)
            if (subtitleCountForVideo <= 1) {
                collectionRepository.removeVideoFromAllCollections(subtitle.videoId)
            }
            subtitleRepository.delete(subtitle)
        }
    }

    fun createCollection(name: String): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return false
        }
        if (_uiState.value.collections.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            return false
        }
        viewModelScope.launch {
            collectionRepository.createCollection(trimmedName)
        }
        return true
    }

    fun addVideoToCollection(collectionId: String, videoId: String): Boolean {
        val collection = _uiState.value.collections.firstOrNull { c -> c.id == collectionId } ?: return false
        val normalizedVideoId = YouTubeVideoIdNormalizer.extractVideoId(videoId.trim()) ?: videoId.trim()
        if (normalizedVideoId.isBlank()) {
            return false
        }
        if (normalizedVideoId in collection.videoIds) {
            return true
        }
        viewModelScope.launch {
            collectionRepository.addVideoToCollection(collectionId, normalizedVideoId)
        }
        return true
    }

    suspend fun exportEpub(
        videoIds: List<String>,
        mode: com.deedeedev.ytreader.data.EpubExportMode,
        bookTitle: String
    ): File {
        return com.deedeedev.ytreader.data.exportEpub(
            context = appContext,
            subtitleRepository = subtitleRepository,
            videoRepository = videoRepository,
            noteRepository = noteRepository,
            videoIds = videoIds,
            mode = mode,
            bookTitle = bookTitle
        )
    }

    suspend fun getAllLibraryVideoIds(): List<String> {
        return subtitleRepository.getLibraryVideoIds()
    }

    private fun createInitialUiState(): LibraryUiState {
        val libraryFilters = userPreferencesRepository.getLibraryFilterState()
        return LibraryUiState(
            selectedChannelFilter = libraryFilters.selectedChannelFilter,
            libraryVisibilityFilter = libraryFilters.visibilityFilter.toLibraryVisibilityFilter(),
            libraryReadStatusFilter = libraryFilters.readStatusFilter.toReadStatusFilter(),
            sortOption = libraryFilters.sortOption.toSortOption(),
            isAscending = libraryFilters.isAscending
        )
    }

    private fun persistLibraryFilterState(state: LibraryUiState) {
        userPreferencesRepository.saveLibraryFilterState(
            PersistedLibraryFilters(
                selectedChannelFilter = state.selectedChannelFilter,
                visibilityFilter = state.libraryVisibilityFilter.name,
                readStatusFilter = state.libraryReadStatusFilter.name,
                sortOption = state.sortOption.name,
                isAscending = state.isAscending
            )
        )
    }

    private data class LibraryQueryParams(
        val channelName: String?,
        val visibilityFilter: LibraryVisibilityFilter,
        val readStatusFilter: ReadStatusFilter,
        val sortOption: SortOption,
        val isAscending: Boolean
    )

    companion object {
        fun provideFactory(
            appContext: Context,
            youtubeRepository: YoutubeRepository,
            subtitleRepository: SubtitleRepository,
            videoRepository: VideoRepository,
            noteRepository: NoteRepository,
            userPreferencesRepository: UserPreferencesRepository,
            collectionRepository: CollectionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(
                    appContext,
                    youtubeRepository,
                    subtitleRepository,
                    videoRepository,
                    noteRepository,
                    userPreferencesRepository,
                    collectionRepository
                ) as T
            }
        }
    }
}
