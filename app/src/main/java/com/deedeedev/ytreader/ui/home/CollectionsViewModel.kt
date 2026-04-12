package com.deedeedev.ytreader.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.CollectionRepository
import com.deedeedev.ytreader.data.NoteRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.PersistedCollectionFilters
import com.deedeedev.ytreader.data.VideoRepository
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.local.SubtitleEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.Flow
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer

data class CollectionsUiState(
    val collections: List<VideoCollection> = emptyList(),
    val collectionFilterStates: Map<String, CollectionFilterState> = emptyMap(),
    val savedSubtitles: List<SubtitleEntity> = emptyList(),
    val readingProgressByVideoId: Map<String, Int> = emptyMap(),
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
    private val subtitleRepository: SubtitleRepository,
    private val videoRepository: VideoRepository,
    private val noteRepository: NoteRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val ops = VideoOperationsHelper(
        appContext, youtubeRepository, subtitleRepository, videoRepository,
        noteRepository, collectionRepository
    )

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
            subtitleRepository.observeAll().collect { subtitles ->
                val videoIds = subtitles.map { it.videoId }.distinct()
                val progressMap = if (videoIds.isNotEmpty()) {
                    subtitleRepository.getMaxReadingProgressForVideos(videoIds)
                } else {
                    emptyMap()
                }
                _uiState.update {
                    it.copy(
                        savedSubtitles = subtitles,
                        readingProgressByVideoId = progressMap
                    )
                }
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
        if (!ops.createCollection(name, _uiState.value.collections)) {
            return false
        }
        viewModelScope.launch {
            collectionRepository.createCollection(name.trim())
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
        if (!ops.addVideoToCollection(collectionId, videoId, _uiState.value.collections)) {
            return false
        }
        val normalizedVideoId = YouTubeVideoIdNormalizer.extractVideoId(videoId.trim()) ?: videoId.trim()
        viewModelScope.launch {
            collectionRepository.addVideoToCollection(collectionId, normalizedVideoId)
        }
        return true
    }

    fun removeVideoFromCollection(collectionId: String, videoId: String) {
        viewModelScope.launch {
            collectionRepository.removeVideoFromCollection(collectionId, videoId)
            deleteVideoIfUnreferenced(subtitleRepository, videoRepository, collectionRepository, appContext, videoId)
        }
    }

    fun observeCollectionChannels(videoIds: List<String>): Flow<List<String>> {
        val normalizedIds = normalizeVideoIds(videoIds)
        if (normalizedIds.isEmpty()) {
            return flowOf(emptyList())
        }
        return subtitleRepository.observeCollectionChannels(normalizedIds)
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

        return subtitleRepository.observeCollectionVideoRows(
            videoIds = normalizedIds,
            channelName = channelName,
            sortOption = sortOption.name,
            isAscending = isAscending
        ).flatMapLatest { rows ->
            observeLibraryItemsForRows(subtitleRepository, collectionRepository, rows)
                .map { items -> items.filterByReadStatus(readStatusFilter) }
        }
    }

    fun observeCollectionVideoCount(videoIds: List<String>): Flow<Int> {
        val normalizedIds = normalizeVideoIds(videoIds)
        if (normalizedIds.isEmpty()) {
            return flowOf(0)
        }
        return subtitleRepository.observeCollectionVideoCount(normalizedIds)
    }

    fun downloadSubtitleAgain(subtitle: SubtitleEntity) {
        viewModelScope.launch {
            ops.downloadSubtitleAgain(
                subtitle = subtitle,
                onDownloadingChange = { id, adding ->
                    _uiState.update { state ->
                        state.copy(
                            downloadingSubtitleIds = if (adding) state.downloadingSubtitleIds + id
                            else state.downloadingSubtitleIds - id
                        )
                    }
                },
                onError = { error ->
                    _uiState.update { it.copy(error = error) }
                },
                onEvent = {}
            )
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
            ops.downloadThumbnailForVideo(
                videoId = videoId,
                videoUrl = videoUrl,
                title = title,
                channelName = channelName,
                uploadDate = uploadDate,
                onDownloadingChange = { id, adding ->
                    _uiState.update { state ->
                        state.copy(
                            downloadingThumbnailVideoIds = if (adding) state.downloadingThumbnailVideoIds + id
                            else state.downloadingThumbnailVideoIds - id
                        )
                    }
                },
                onError = { error ->
                    _uiState.update { it.copy(error = error) }
                },
                onEvent = { message ->
                    _events.tryEmit(CollectionsEvent.ShowMessage(message))
                }
            )
        }
    }

    fun markVideoAsRead(videoId: String) {
        viewModelScope.launch {
            ops.markVideoAsRead(videoId)
        }
    }

    fun resetVideoProgress(videoId: String) {
        viewModelScope.launch {
            ops.resetVideoProgress(videoId)
        }
    }

    fun deleteSubtitle(subtitle: SubtitleEntity) {
        viewModelScope.launch {
            ops.deleteSubtitle(subtitle)
        }
    }

    fun restoreLibraryItem(subtitles: List<SubtitleEntity>) {
        viewModelScope.launch {
            ops.restoreLibraryItem(subtitles)
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
            subtitleRepository: SubtitleRepository,
            videoRepository: VideoRepository,
            noteRepository: NoteRepository,
            userPreferencesRepository: UserPreferencesRepository,
            collectionRepository: CollectionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CollectionsViewModel(
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
