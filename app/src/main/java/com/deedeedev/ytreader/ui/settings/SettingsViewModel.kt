package com.deedeedev.ytreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val defaultFontSize: Float = 16f,
    val fontFamily: String = "Default",
    val availableFonts: List<String> = listOf("Default", "Serif", "SansSerif", "Monospace", "Cursive"),
    val lineHeightMultiplier: Float = 1.5f,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val availableThemes: List<AppTheme> = AppTheme.entries,
    val aiEndpoint: String = "",
    val aiApiKey: String = "",
    val aiModel: String = "",
    val aiPrompt: String = ""
)

class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.defaultFontSize.collect { size ->
                _uiState.update { it.copy(defaultFontSize = size) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.fontFamily.collect { family ->
                _uiState.update { it.copy(fontFamily = family) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.lineHeightMultiplier.collect { multiplier ->
                _uiState.update { it.copy(lineHeightMultiplier = multiplier) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.appTheme.collect { appTheme ->
                _uiState.update { it.copy(appTheme = appTheme) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.aiEndpoint.collect { endpoint ->
                _uiState.update { it.copy(aiEndpoint = endpoint) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.aiApiKey.collect { key ->
                _uiState.update { it.copy(aiApiKey = key) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.aiModel.collect { model ->
                _uiState.update { it.copy(aiModel = model) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.aiPrompt.collect { prompt ->
                _uiState.update { it.copy(aiPrompt = prompt) }
            }
        }
    }

    fun setDefaultFontSize(size: Float) {
        userPreferencesRepository.setDefaultFontSize(size)
    }

    fun setFontFamily(family: String) {
        userPreferencesRepository.setFontFamily(family)
    }

    fun setLineHeightMultiplier(multiplier: Float) {
        userPreferencesRepository.setLineHeightMultiplier(multiplier)
    }

    fun setAppTheme(appTheme: AppTheme) {
        userPreferencesRepository.setAppTheme(appTheme)
    }

    fun setAiEndpoint(endpoint: String) {
        userPreferencesRepository.setAiEndpoint(endpoint)
    }

    fun setAiApiKey(key: String) {
        userPreferencesRepository.setAiApiKey(key)
    }

    fun setAiModel(model: String) {
        userPreferencesRepository.setAiModel(model)
    }

    fun setAiPrompt(prompt: String) {
        userPreferencesRepository.setAiPrompt(prompt)
    }

    companion object {
        fun provideFactory(
            userPreferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(userPreferencesRepository) as T
            }
        }
    }
}
