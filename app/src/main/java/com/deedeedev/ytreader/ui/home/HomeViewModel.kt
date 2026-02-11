package com.deedeedev.ytreader.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream

enum class SortOption {
    TITLE, CHANNEL_NAME, DATE_PUBLISHED, DOWNLOADED
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
    val isAscending: Boolean = false
)

class HomeViewModel(
    private val youtubeRepository: YoutubeRepository,
    private val subtitleDao: SubtitleDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadSavedSubtitles()
        loadFavoriteLanguages()
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
                val url = subtitle.url ?: throw IllegalArgumentException("Subtitle URL is missing")
                val content = youtubeRepository.downloadSubtitle(url)
                val plainText = SubtitleParser.parse(content)
                
                val entity = SubtitleEntity(
                    videoId = info.url ?: "unknown", // Using URL as ID for now or info.id
                    title = info.name,
                    channelName = info.uploaderName ?: "Unknown Channel",
                    languageCode = subtitle.languageTag ?: "unknown",
                    content = plainText,
                    fontSize = userPreferencesRepository.defaultFontSize.value,
                    fontFamily = userPreferencesRepository.fontFamily.value,
                    uploadDate = info.uploadDate?.instant?.toEpochMilli() ?: 0L
                )
                subtitleDao.insert(entity)
                
                _uiState.update { it.copy(isLoading = false, error = null) } // Success
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Download failed") }
            }
        }
    }


    fun deleteLibraryItem(subtitle: SubtitleEntity) {
        viewModelScope.launch {
            subtitleDao.deleteByVideoId(subtitle.videoId)
        }
    }

    fun deleteSubtitle(subtitle: SubtitleEntity) {
        viewModelScope.launch {
            subtitleDao.delete(subtitle)
        }
    }

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
