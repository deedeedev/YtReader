package com.deedeedev.ytreader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.CollectionRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val subtitleRepository: SubtitleRepository,
    private val collectionRepository: CollectionRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyItems: StateFlow<List<LibraryItem>?> = subtitleRepository.observeHistoryVideoRows()
        .flatMapLatest { rows ->
            observeLibraryItemsForRows(subtitleRepository, collectionRepository, rows)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val videoCardSize: StateFlow<VideoCardSize> = userPreferencesRepository.videoCardSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoCardSize.LARGE)

    fun removeFromHistory(videoId: String) {
        viewModelScope.launch {
            subtitleRepository.clearHistoryForVideo(videoId)
        }
    }

    suspend fun getPreferredSubtitleIdForVideo(videoId: String): Long? {
        return subtitleRepository.getPreferredSubtitleForVideo(videoId)?.id
    }

    companion object {
        fun provideFactory(
            subtitleRepository: SubtitleRepository,
            collectionRepository: CollectionRepository,
            userPreferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel(
                    subtitleRepository,
                    collectionRepository,
                    userPreferencesRepository
                ) as T
            }
        }
    }
}