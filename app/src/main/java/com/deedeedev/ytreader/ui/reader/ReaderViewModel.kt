package com.deedeedev.ytreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.AiCleaningRepository
import com.deedeedev.ytreader.data.AiCleaningRequest
import com.deedeedev.ytreader.data.DEFAULT_AI_CLEANING_PROMPT
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

data class ReaderUiState(
    val subtitle: SubtitleEntity? = null,
    val content: String = "",
    val originalParsedText: String = "",
    val highlights: List<TextHighlight> = emptyList(),
    val fontSize: Float = 16f,
    val fontFamily: String = "Default",
    val lineHeightMultiplier: Float = 1.5f,
    val isAiCleaning: Boolean = false,
    val isLoading: Boolean = false
)

class ReaderViewModel(
    private val subtitleDao: SubtitleDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiCleaningRepository: AiCleaningRepository,
    private val subtitleId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState(isLoading = true))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var aiEndpoint: String = ""
    private var aiApiKey: String = ""
    private var aiModel: String = ""
    private var aiPrompt: String = DEFAULT_AI_CLEANING_PROMPT

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
        viewModelScope.launch {
            userPreferencesRepository.aiEndpoint.collect { endpoint ->
                aiEndpoint = endpoint
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.aiApiKey.collect { key ->
                aiApiKey = key
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.aiModel.collect { model ->
                aiModel = model
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.aiPrompt.collect { prompt ->
                aiPrompt = prompt
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

    fun updateHighlightColor(target: TextHighlight, newColor: HighlightColor) {
        val state = _uiState.value
        val updated = recolorHighlight(state.highlights, target, newColor)
        if (updated == state.highlights) return
        persistHighlights(updated)
    }

    fun deleteHighlight(target: TextHighlight) {
        val state = _uiState.value
        val updated = deleteHighlightFromList(state.highlights, target)
        if (updated == state.highlights) return
        persistHighlights(updated)
    }

    suspend fun cleanTextWithAi(inputText: String): Result<String> {
        val endpoint = aiEndpoint.trim()
        val key = aiApiKey.trim()
        val model = aiModel.trim()
        if (endpoint.isBlank() || key.isBlank() || model.isBlank()) {
            return Result.failure(
                IllegalStateException("Set AI endpoint, API key, and model in Settings.")
            )
        }

        _uiState.update { it.copy(isAiCleaning = true) }
        return try {
            val cleaned = aiCleaningRepository.cleanText(
                AiCleaningRequest(
                    endpointBaseUrl = endpoint,
                    apiKey = key,
                    model = model,
                    userInstructions = aiPrompt,
                    subtitleText = inputText
                )
            )
            if (cleaned.isBlank()) {
                Result.failure(IllegalStateException("AI returned empty cleaned text."))
            } else {
                Result.success(cleaned)
            }
        } catch (error: Exception) {
            Result.failure(IllegalStateException(mapAiError(error)))
        } finally {
            _uiState.update { it.copy(isAiCleaning = false) }
        }
    }

    private fun mapAiError(error: Exception): String {
        return when (error) {
            is SocketTimeoutException, is InterruptedIOException ->
                "AI cleaning timed out. Try shorter text or check endpoint/model."
            else -> error.message?.takeIf { it.isNotBlank() } ?: "AI cleaning failed."
        }
    }

    private fun persistHighlights(highlights: List<TextHighlight>) {
        val serialized = serializeHighlights(highlights)
        _uiState.update {
            it.copy(
                highlights = highlights,
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
            aiCleaningRepository: AiCleaningRepository,
            subtitleId: Long
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(
                    dao,
                    userPreferencesRepository,
                    aiCleaningRepository,
                    subtitleId
                ) as T
            }
        }
    }
}
