package com.deedeedev.ytreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val subtitle: SubtitleEntity? = null,
    val content: String = "",
    val originalParsedText: String = "",
    val highlights: List<TextHighlight> = emptyList(),
    val fontSize: Float = 16f,
    val fontFamily: String = "Default",
    val lineHeightMultiplier: Float = 1.5f,
    val isLoading: Boolean = false
)

class ReaderViewModel(
    private val subtitleDao: SubtitleDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val subtitleId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState(isLoading = true))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        loadSubtitle()
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            userPreferencesRepository.lineHeightMultiplier.collect { multiplier ->
                _uiState.update { it.copy(lineHeightMultiplier = multiplier) }
            }
        }
    }

    private fun loadSubtitle() {
        viewModelScope.launch {
            val subtitle = subtitleDao.getById(subtitleId)
            if (subtitle != null) {
                val originalParsedText = SubtitleParser.parse(subtitle.content)
                val content = subtitle.studyContent ?: originalParsedText
                val highlights = parseHighlights(subtitle.highlights)
                
                _uiState.update { it.copy(
                    subtitle = subtitle,
                    content = content,
                    originalParsedText = originalParsedText,
                    highlights = highlights,
                    fontSize = subtitle.fontSize,
                    fontFamily = subtitle.fontFamily,
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

    fun updateFontFamily(fontFamily: String) {
        _uiState.update { it.copy(fontFamily = fontFamily) }
        viewModelScope.launch {
            subtitleDao.updateFontFamily(subtitleId, fontFamily)
        }
    }

    fun updateContent(content: String) {
        _uiState.update { state ->
            state.copy(
                content = content,
                highlights = emptyList(),
                subtitle = state.subtitle?.copy(
                    studyContent = content,
                    highlights = ""
                )
            )
        }
        viewModelScope.launch {
            subtitleDao.updateStudyContent(subtitleId, content)
            subtitleDao.updateHighlights(subtitleId, "")
        }
    }

    fun applyHighlight(start: Int, end: Int, color: HighlightColor) {
        val state = _uiState.value
        val contentLength = state.content.length
        if (contentLength == 0) return

        val boundedStart = start.coerceIn(0, contentLength)
        val boundedEnd = end.coerceIn(0, contentLength)
        val normalizedStart = minOf(boundedStart, boundedEnd)
        val normalizedEnd = maxOf(boundedStart, boundedEnd)
        if (normalizedStart == normalizedEnd) return

        val merged = mergeHighlight(
            current = state.highlights,
            start = normalizedStart,
            end = normalizedEnd,
            color = color
        )
        val serialized = serializeHighlights(merged)

        _uiState.update {
            it.copy(
                highlights = merged,
                subtitle = it.subtitle?.copy(highlights = serialized)
            )
        }

        viewModelScope.launch {
            subtitleDao.updateHighlights(subtitleId, serialized)
        }
    }

    companion object {
        fun provideFactory(
            dao: SubtitleDao,
            userPreferencesRepository: UserPreferencesRepository,
            subtitleId: Long
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(dao, userPreferencesRepository, subtitleId) as T
            }
        }
    }
}
