package com.deedeedev.ytreader.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.CollectionRepository
import com.deedeedev.ytreader.data.PersistedCollectionFilters
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.local.AppDatabase
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.data.local.VideoDao
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionsUiState(
    val collections: List<VideoCollection> = emptyList(),
    val collectionFilterStates: Map<String, CollectionFilterState> = emptyMap(),
    val savedSubtitles: List<SubtitleEntity> = emptyList(),
    val downloadingSubtitleIds: Set<Long> = emptySet(),
    val downloadingThumbnailVideoIds: Set<String> = emptySet(),
    val error: String? = null
)

sealed interface CollectionsEvent {
    data class ShowMessage(val message: String) : CollectionsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModel(
    private val appContext: Context,
    private val youtubeRepository: YoutubeRepository,
    private val database: AppDatabase,
    private val subtitleDao: SubtitleDao,
    private val videoDao: VideoDao,
    private val highlightNoteDao: HighlightNoteDao,
    private val bookmarkDao: BookmarkDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<CollectionsEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        loadCollections()
        loadSavedSubtitles()
    }

    private fun loadCollections() {
        viewModelScope.launch {
            collectionRepository.collections.collect { collections ->
                _uiState.update { it.copy(collections = collections) }
            }
        }
    }

    private fun loadSavedSubtitles() {
        viewModelScope.launch {
            subtitleDao.getAll().collect { subtitles ->
                _uiState.update { it.copy(savedSubtitles = subtitles) }
            }
        }
    }

    fun getCollectionFilterState(collectionId: String): CollectionFilterState {
        return _uiState.value.collectionFilterStates[collectionId] ?: CollectionFilterState()
    }

    fun setCollectionChannelFilter(collectionId: String, channelName: String?) {
        updateCollectionFilterState(collectionId) { it.copy(selectedChannelFilter = channelName) }
    }

    fun setCollectionSortOption(collectionId: String, sortOption: SortOption) {
        updateCollectionFilterState(collectionId) { it.copy(sortOption = sortOption) }
    }

    fun setCollectionReadStatusFilter(collectionId: String, readStatusFilter: ReadStatusFilter) {
        updateCollectionFilterState(collectionId) { it.copy(readStatusFilter = readStatusFilter) }
    }

    fun toggleCollectionSortOrder(collectionId: String) {
        updateCollectionFilterState(collectionId) { it.copy(isAscending = !it.isAscending) }
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

    fun renameCollection(collectionId: String, newName: String): Boolean {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            return false
        }
        if (_uiState.value.collections.none { it.id == collectionId }) {
            return false
        }
        if (_uiState.value.collections.any { it.id != collectionId && it.name.equals(trimmedName, ignoreCase = true) }) {
            return false
        }
        viewModelScope.launch {
            collectionRepository.renameCollection(collectionId, trimmedName)
        }
        return true
    }

    fun deleteCollection(collectionId: String) {
        _uiState.update {
            it.copy(collectionFilterStates = it.collectionFilterStates - collectionId)
        }
        userPreferencesRepository.removeCollectionFilterState(collectionId)
        viewModelScope.launch {
            collectionRepository.deleteCollection(collectionId)
        }
    }

    fun reorderCollections(collectionIds: List<String>) {
        val currentIds = _uiState.value.collections.map { it.id }
        if (collectionIds == currentIds || collectionIds.size != currentIds.size) {
            return
        }
        if (collectionIds.toSet() != currentIds.toSet()) {
            return
        }
        viewModelScope.launch {
            collectionRepository.reorderCollections(collectionIds)
        }
    }

    fun addVideoToCollection(collectionId: String, videoId: String): Boolean {
        val collection = _uiState.value.collections.firstOrNull { it.id == collectionId } ?: return false
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

    fun removeVideoFromCollection(collectionId: String, videoId: String) {
        viewModelScope.launch {
            collectionRepository.removeVideoFromCollection(collectionId, videoId)
            deleteVideoIfUnreferenced(subtitleDao, videoDao, collectionRepository, appContext, videoId)
        }
    }

    fun observeCollectionChannels(videoIds: List<String>): Flow<List<String>> {
        val normalizedIds = normalizeVideoIds(videoIds)
        if (normalizedIds.isEmpty()) {
            return flowOf(emptyList())
        }
        return subtitleDao.observeCollectionChannels(normalizedIds)
    }

    fun observeCollectionItems(
        videoIds: List<String>,
        channelName: String?,
        readStatusFilter: ReadStatusFilter = ReadStatusFilter.ALL,
        sortOption: SortOption,
        isAscending: Boolean
    ): Flow<List<LibraryItem>> {
        val normalizedIds = normalizeVideoIds(videoIds)
        if (normalizedIds.isEmpty()) {
            return flowOf(emptyList())
        }

        return subtitleDao.observeCollectionVideoRows(
            videoIds = normalizedIds,
            channelName = channelName,
            sortOption = sortOption.name,
            isAscending = isAscending
        ).flatMapLatest { rows ->
            observeLibraryItemsForRows(subtitleDao, collectionRepository, rows)
                .map { items -> items.filterByReadStatus(readStatusFilter) }
        }
    }

    fun observeCollectionVideoCount(videoIds: List<String>): Flow<Int> {
        val normalizedIds = normalizeVideoIds(videoIds)
        if (normalizedIds.isEmpty()) {
            return flowOf(0)
        }
        return subtitleDao.observeCollectionVideoCount(normalizedIds)
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

                database.withTransaction {
                    subtitleDao.replaceContentForRedownload(
                        id = subtitle.id,
                        content = rawContent,
                        createdAt = System.currentTimeMillis()
                    )
                    highlightNoteDao.deleteBySubtitleId(subtitle.id)
                    bookmarkDao.deleteBySubtitleId(subtitle.id)
                }
                upsertVideoMetadata(
                    videoDao = videoDao,
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
                    videoDao = videoDao,
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
                _events.tryEmit(CollectionsEvent.ShowMessage(appContext.getString(R.string.library_thumbnail_downloaded)))
            } catch (e: Exception) {
                val errorMessage = e.message ?: appContext.getString(R.string.library_thumbnail_download_failed)
                _uiState.update {
                    it.copy(error = errorMessage)
                }
                _events.tryEmit(CollectionsEvent.ShowMessage(errorMessage))
            } finally {
                _uiState.update { state ->
                    state.copy(downloadingThumbnailVideoIds = state.downloadingThumbnailVideoIds - videoId)
                }
            }
        }
    }

    fun markVideoAsRead(videoId: String) {
        viewModelScope.launch {
            subtitleDao.markVideoAsRead(videoId)
        }
    }

    fun resetVideoProgress(videoId: String) {
        viewModelScope.launch {
            subtitleDao.resetReadingProgressForVideo(videoId)
        }
    }

    fun deleteSubtitle(subtitle: SubtitleEntity) {
        viewModelScope.launch {
            val subtitleCountForVideo = subtitleDao.countByVideoId(subtitle.videoId)
            if (subtitleCountForVideo <= 1) {
                collectionRepository.removeVideoFromAllCollections(subtitle.videoId)
            }
            subtitleDao.delete(subtitle)
        }
    }

    fun restoreLibraryItem(subtitles: List<SubtitleEntity>) {
        viewModelScope.launch {
            val videoId = subtitles.firstOrNull()?.videoId ?: return@launch
            subtitles.forEach { subtitle ->
                subtitleDao.upsertByIdentity(subtitle.copy(isInLibrary = true))
            }
            subtitleDao.updateLibraryVisibility(videoId, true)
        }
    }

    private fun createInitialUiState(): CollectionsUiState {
        val collectionFilters = userPreferencesRepository.getCollectionFilterStates()
            .mapValues { (_, state) ->
                CollectionFilterState(
                    selectedChannelFilter = state.selectedChannelFilter,
                    readStatusFilter = state.readStatusFilter.toReadStatusFilter(),
                    sortOption = state.sortOption.toSortOption(),
                    isAscending = state.isAscending
                )
            }
        return CollectionsUiState(
            collectionFilterStates = collectionFilters
        )
    }

    private fun updateCollectionFilterState(
        collectionId: String,
        transform: (CollectionFilterState) -> CollectionFilterState
    ) {
        var updatedState: CollectionFilterState? = null
        _uiState.update { state ->
            val current = state.collectionFilterStates[collectionId] ?: CollectionFilterState()
            val updated = transform(current)
            updatedState = updated
            state.copy(
                collectionFilterStates = state.collectionFilterStates + (collectionId to updated)
            )
        }
        updatedState?.let { state ->
            userPreferencesRepository.saveCollectionFilterState(
                collectionId = collectionId,
                state = PersistedCollectionFilters(
                    selectedChannelFilter = state.selectedChannelFilter,
                    readStatusFilter = state.readStatusFilter.name,
                    sortOption = state.sortOption.name,
                    isAscending = state.isAscending
                )
            )
        }
    }

    companion object {
        fun provideFactory(
            appContext: Context,
            youtubeRepository: YoutubeRepository,
            database: AppDatabase,
            subtitleDao: SubtitleDao,
            videoDao: VideoDao,
            highlightNoteDao: HighlightNoteDao,
            bookmarkDao: BookmarkDao,
            userPreferencesRepository: UserPreferencesRepository,
            collectionRepository: CollectionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CollectionsViewModel(
                    appContext,
                    youtubeRepository,
                    database,
                    subtitleDao,
                    videoDao,
                    highlightNoteDao,
                    bookmarkDao,
                    userPreferencesRepository,
                    collectionRepository
                ) as T
            }
        }
    }
}
