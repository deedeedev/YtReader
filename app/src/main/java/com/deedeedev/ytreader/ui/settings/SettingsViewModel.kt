package com.deedeedev.ytreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.UserPreferencesRepository
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
    val aiApiKey: String = ""
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
            userPreferencesRepository.aiApiKey.collect { key ->
                _uiState.update { it.copy(aiApiKey = key) }
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

    fun setAiApiKey(key: String) {
        userPreferencesRepository.setAiApiKey(key)
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
