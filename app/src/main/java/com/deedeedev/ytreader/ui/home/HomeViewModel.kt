package com.deedeedev.ytreader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.local.LibraryVideoRow
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleIdentity
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream

enum class SortOption {
    TITLE, CHANNEL_NAME, DATE_PUBLISHED, DOWNLOADED, LAST_OPENED
}

data class HomeUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val streamInfo: StreamInfo? = null,
    val savedSubtitles: List<SubtitleEntity> = emptyList(),
    val selectedSubtitle: SubtitleEntity? = null,
    val favoriteLanguages: Set<String> = emptySet(),
    val selectedChannelFilter: String? = null,
    val sortOption: SortOption = SortOption.DOWNLOADED,
    val isAscending: Boolean = false,
    val downloadingSubtitleIds: Set<Long> = emptySet(),
    val collections: List<VideoCollection> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val youtubeRepository: YoutubeRepository,
    private val subtitleDao: SubtitleDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val libraryChannels: StateFlow<List<String>> = subtitleDao.observeLibraryChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryItems: StateFlow<List<LibraryItem>> = uiState
        .map { state ->
            LibraryQueryParams(
                channelName = state.selectedChannelFilter,
                sortOption = state.sortOption,
                isAscending = state.isAscending
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { params ->
            subtitleDao.observeLibraryVideoRows(
                channelName = params.channelName,
                sortOption = params.sortOption.name,
                isAscending = params.isAscending
            ).flatMapLatest { rows ->
                observeLibraryItemsForRows(rows)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        userPreferencesRepository.normalizeCollectionVideoIds()
        loadSavedSubtitles()
        loadFavoriteLanguages()
        loadCollections()
    }

    private fun loadCollections() {
        viewModelScope.launch {
            userPreferencesRepository.videoCollections.collect { collections ->
                _uiState.update { it.copy(collections = collections) }
            }
        }
    }

    private fun loadFavoriteLanguages() {
        viewModelScope.launch {
            userPreferencesRepository.favoriteLanguages.collect { favorites ->
                _uiState.update { it.copy(favoriteLanguages = favorites) }
            }
        }
    }

    fun toggleFavoriteLanguage(languageCode: String) {
        userPreferencesRepository.toggleFavoriteLanguage(languageCode)
    }

    private fun loadSavedSubtitles() {
        viewModelScope.launch {
            subtitleDao.getAll().collect { subtitles ->
                _uiState.update { it.copy(savedSubtitles = subtitles) }
            }
        }
    }

    fun setChannelFilter(channelName: String?) {
        _uiState.update { it.copy(selectedChannelFilter = channelName) }
    }

    fun setSortOption(sortOption: SortOption) {
        _uiState.update { it.copy(sortOption = sortOption) }
    }

    fun toggleSortOrder() {
        _uiState.update { it.copy(isAscending = !it.isAscending) }
    }

    fun onUrlChange(newUrl: String?) {
        _uiState.update { it.copy(url = newUrl ?: "", error = null) }
    }

    fun selectSubtitle(id: Long) {
        viewModelScope.launch {
            val subtitle = subtitleDao.getById(id)
            _uiState.update { it.copy(selectedSubtitle = subtitle) }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedSubtitle = null) }
    }

    fun searchVideo() {
        val url = _uiState.value.url
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, streamInfo = null) }
            try {
                val info = youtubeRepository.getStreamInfo(url)
                _uiState.update { it.copy(isLoading = false, streamInfo = info) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun downloadSubtitle(subtitle: SubtitlesStream) {
        val info = _uiState.value.streamInfo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val subtitleContent = subtitle.content
                val rawContent = if (subtitle.isUrl) {
                    youtubeRepository.downloadSubtitle(subtitleContent)
                } else {
                    subtitleContent
                }
                val canonicalVideoRef = YouTubeVideoIdNormalizer.canonicalize(info.url)
                
                val entity = SubtitleEntity(
                    videoId = canonicalVideoRef.videoId,
                    videoUrl = canonicalVideoRef.videoUrl,
                    title = info.name,
                    channelName = info.uploaderName ?: "Unknown Channel",
                    languageCode = subtitle.languageTag ?: "unknown",
                    subtitleTrackId = subtitle.id,
                    trackIdentity = SubtitleIdentity.fromTrack(
                        subtitleTrackId = subtitle.id,
                        languageCode = subtitle.languageTag,
                        isAutoGenerated = subtitle.isAutoGenerated
                    ),
                    isAutoGenerated = subtitle.isAutoGenerated,
                    content = rawContent,
                    fontSize = userPreferencesRepository.defaultFontSize.value,
                    fontFamily = userPreferencesRepository.fontFamily.value,
                    uploadDate = info.uploadDate?.instant?.toEpochMilli() ?: 0L
                )
                subtitleDao.upsertByIdentity(entity)
                
                _uiState.update { it.copy(isLoading = false, error = null) } // Success
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Download failed") }
            }
        }
    }

    fun downloadSubtitleAgain(subtitle: SubtitleEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            _uiState.update { state ->
                state.copy(downloadingSubtitleIds = state.downloadingSubtitleIds + subtitle.id)
            }
            try {
                val info = youtubeRepository.getStreamInfo(resolveVideoLookupUrl(subtitle))
                val matchingSubtitle = SubtitleIdentityMatcher.findMatchingStream(
                    savedSubtitle = subtitle,
                    streams = info.subtitles
                )
                    ?: throw IllegalStateException("Matching subtitle not found")

                val subtitleContent = matchingSubtitle.content
                val rawContent = if (matchingSubtitle.isUrl) {
                    youtubeRepository.downloadSubtitle(subtitleContent)
                } else {
                    subtitleContent
                }

                subtitleDao.replaceContentForRedownload(
                    id = subtitle.id,
                    content = rawContent,
                    createdAt = System.currentTimeMillis()
                )
                _uiState.update { it.copy(isLoading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Download failed") }
            } finally {
                _uiState.update { state ->
                    state.copy(downloadingSubtitleIds = state.downloadingSubtitleIds - subtitle.id)
                }
            }
        }
    }


    fun deleteLibraryItem(subtitle: SubtitleEntity) {
        viewModelScope.launch {
            userPreferencesRepository.removeVideoFromAllCollections(subtitle.videoId)
            subtitleDao.deleteByVideoId(subtitle.videoId)
        }
    }

    fun restoreLibraryItem(subtitles: List<SubtitleEntity>) {
        viewModelScope.launch {
            subtitles.forEach { subtitle ->
                subtitleDao.upsertByIdentity(subtitle)
            }
        }
    }

    fun deleteSubtitle(subtitle: SubtitleEntity) {
        viewModelScope.launch {
            val subtitleCountForVideo = subtitleDao.countByVideoId(subtitle.videoId)
            if (subtitleCountForVideo <= 1) {
                userPreferencesRepository.removeVideoFromAllCollections(subtitle.videoId)
            }
            subtitleDao.delete(subtitle)
        }
    }

    fun createCollection(name: String): Boolean {
        return userPreferencesRepository.createCollection(name)
    }

    fun renameCollection(collectionId: String, newName: String): Boolean {
        return userPreferencesRepository.renameCollection(collectionId, newName)
    }

    fun deleteCollection(collectionId: String) {
        userPreferencesRepository.deleteCollection(collectionId)
    }

    fun addVideoToCollection(collectionId: String, videoId: String): Boolean {
        return userPreferencesRepository.addVideoToCollection(collectionId, videoId)
    }

    fun removeVideoFromCollection(collectionId: String, videoId: String) {
        userPreferencesRepository.removeVideoFromCollection(collectionId, videoId)
    }

    private fun resolveVideoLookupUrl(subtitle: SubtitleEntity): String {
        val savedUrl = subtitle.videoUrl.trim()
        if (savedUrl.isNotBlank()) {
            return savedUrl
        }
        val normalizedId = YouTubeVideoIdNormalizer.extractVideoId(subtitle.videoId)
        if (normalizedId != null) {
            return YouTubeVideoIdNormalizer.canonicalWatchUrl(normalizedId)
        }
        return subtitle.videoId
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
            observeLibraryItemsForRows(rows)
        }
    }

    fun observeCollectionVideoCount(videoIds: List<String>): Flow<Int> {
        val normalizedIds = normalizeVideoIds(videoIds)
        if (normalizedIds.isEmpty()) {
            return flowOf(0)
        }
        return subtitleDao.observeCollectionVideoCount(normalizedIds)
    }

    private fun observeLibraryItemsForRows(rows: List<LibraryVideoRow>): Flow<List<LibraryItem>> {
        val videoIds = rows.map { it.videoId }
        if (videoIds.isEmpty()) {
            return flowOf(emptyList())
        }

        return subtitleDao.observeSubtitleTracksForVideos(videoIds)
            .map { tracks ->
                val tracksByVideoId = tracks.groupBy { it.videoId }
                rows.map { row ->
                    LibraryItem(
                        videoId = row.videoId,
                        videoUrl = displayUrlFor(row.videoId, row.videoUrl),
                        title = row.title,
                        channelName = row.channelName,
                        subtitles = tracksByVideoId[row.videoId].orEmpty(),
                        uploadDate = row.uploadDate,
                        lastDownloaded = row.lastDownloaded,
                        lastOpenedAt = row.lastOpenedAt
                    )
                }
            }
    }

    private fun normalizeVideoIds(videoIds: List<String>): List<String> {
        return videoIds.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun displayUrlFor(videoId: String, videoUrl: String): String {
        val savedUrl = videoUrl.trim()
        if (savedUrl.isNotBlank()) {
            return savedUrl
        }
        val normalizedVideoId = YouTubeVideoIdNormalizer.extractVideoId(videoId)
        if (normalizedVideoId != null) {
            return YouTubeVideoIdNormalizer.canonicalWatchUrl(normalizedVideoId)
        }
        return videoId
    }

    private data class LibraryQueryParams(
        val channelName: String?,
        val sortOption: SortOption,
        val isAscending: Boolean
    )

    companion object {
        fun provideFactory(
            repository: YoutubeRepository,
            dao: SubtitleDao,
            userPreferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(repository, dao, userPreferencesRepository) as T
            }
        }
    }
}
