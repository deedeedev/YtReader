package com.deedeedev.ytreader.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.StringProvider
import com.deedeedev.ytreader.data.SearchHistoryRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.VideoRepository
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.local.SearchHistoryEntity
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleIdentity
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream

data class SearchUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val streamInfo: StreamInfo? = null,
    val savedSubtitles: List<SubtitleEntity> = emptyList(),
    val favoriteLanguages: Set<String> = emptySet(),
    val searchHistory: List<SearchHistoryEntity> = emptyList(),
    val showHistory: Boolean = false
)

sealed interface SearchEvent {
    data class ShowMessage(val message: String) : SearchEvent
}

class SearchViewModel(
    private val stringProvider: StringProvider,
    private val appContext: Context,
    private val youtubeRepository: YoutubeRepository,
    private val subtitleRepository: SubtitleRepository,
    private val videoRepository: VideoRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val searchHistoryRepository: SearchHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        loadSavedSubtitles()
        loadFavoriteLanguages()
        loadSearchHistory()
    }

    private fun loadSavedSubtitles() {
        viewModelScope.launch {
            subtitleRepository.observeAll().collect { subtitles ->
                _uiState.update { it.copy(savedSubtitles = subtitles) }
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

    private fun loadSearchHistory() {
        viewModelScope.launch {
            searchHistoryRepository.observeAll().collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
    }

    fun onUrlChange(newUrl: String?) {
        val trimmed = newUrl ?: ""
        _uiState.update {
            it.copy(
                url = trimmed,
                error = null,
                streamInfo = if (trimmed.isEmpty()) null else it.streamInfo,
                showHistory = if (trimmed.isEmpty()) false else it.showHistory
            )
        }
    }

    fun searchVideo() {
        val url = _uiState.value.url
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, streamInfo = null, showHistory = false) }
            try {
                val info = youtubeRepository.getStreamInfo(url)
                _uiState.update { it.copy(isLoading = false, streamInfo = info) }
                saveToSearchHistory(url, info.name, info.uploaderName ?: stringProvider.getString(R.string.channel_unknown))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: stringProvider.getString(R.string.unknown_error)
                    )
                }
            }
        }
    }

    fun toggleHistory() {
        _uiState.update { it.copy(showHistory = !it.showHistory) }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch {
            searchHistoryRepository.delete(id)
        }
    }

    fun searchFromHistory(url: String) {
        _uiState.update { it.copy(url = url) }
        searchVideo()
    }

    private suspend fun saveToSearchHistory(url: String, videoTitle: String, channelName: String) {
        searchHistoryRepository.upsert(
            SearchHistoryEntity(
                url = url,
                videoTitle = videoTitle,
                channelName = channelName,
                searchedAt = System.currentTimeMillis()
            )
        )
        val count = searchHistoryRepository.getCount()
        if (count > 100) {
            searchHistoryRepository.deleteOldest(count - 100)
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
                    channelName = info.uploaderName ?: stringProvider.getString(R.string.channel_unknown),
                    languageCode = subtitle.languageTag
                        ?: stringProvider.getString(R.string.library_unknown_code),
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
                subtitleRepository.upsertByIdentity(entity)
                videoRepository.upsertVideoMetadata(
                    youtubeRepository = youtubeRepository,
                    appContext = appContext,
                    videoId = canonicalVideoRef.videoId,
                    fallbackVideoUrl = canonicalVideoRef.videoUrl,
                    fallbackTitle = info.name,
                    fallbackChannelName = info.uploaderName ?: stringProvider.getString(R.string.channel_unknown),
                    fallbackUploadDate = info.uploadDate?.instant?.toEpochMilli() ?: 0L,
                    info = info
                )

                _uiState.update { it.copy(isLoading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: stringProvider.getString(R.string.download_failed)
                    )
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            stringProvider: StringProvider,
            appContext: Context,
            youtubeRepository: YoutubeRepository,
            subtitleRepository: SubtitleRepository,
            videoRepository: VideoRepository,
            userPreferencesRepository: UserPreferencesRepository,
            searchHistoryRepository: SearchHistoryRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(
                    stringProvider,
                    appContext,
                    youtubeRepository,
                    subtitleRepository,
                    videoRepository,
                    userPreferencesRepository,
                    searchHistoryRepository
                ) as T
            }
        }
    }
}
