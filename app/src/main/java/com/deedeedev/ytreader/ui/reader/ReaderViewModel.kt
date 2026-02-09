package com.deedeedev.ytreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleParser
import com.deedeedev.ytreader.domain.SubtitleSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val subtitle: SubtitleEntity? = null,
    val segments: List<SubtitleSegment> = emptyList(),
    val fontSize: Float = 16f,
    val isLoading: Boolean = false
)

class ReaderViewModel(
    private val subtitleDao: SubtitleDao,
    private val subtitleId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState(isLoading = true))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        loadSubtitle()
    }

    private fun loadSubtitle() {
        viewModelScope.launch {
            val subtitle = subtitleDao.getById(subtitleId)
            if (subtitle != null) {
                val segments = SubtitleParser.parseToSegments(subtitle.content)
                _uiState.update { it.copy(
                    subtitle = subtitle,
                    segments = segments,
                    fontSize = subtitle.fontSize,
                    isLoading = false
                ) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateLastTimestamp(timestamp: Long) {
        viewModelScope.launch {
            subtitleDao.updateLastTimestamp(subtitleId, timestamp)
        }
    }

    fun updateFontSize(fontSize: Float) {
        _uiState.update { it.copy(fontSize = fontSize) }
        viewModelScope.launch {
            subtitleDao.updateFontSize(subtitleId, fontSize)
        }
    }

    companion object {
        fun provideFactory(
            dao: SubtitleDao,
            subtitleId: Long
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(dao, subtitleId) as T
            }
        }
    }
}
