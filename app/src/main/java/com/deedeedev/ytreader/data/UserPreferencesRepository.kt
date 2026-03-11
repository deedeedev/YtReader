package com.deedeedev.ytreader.data

import android.content.Context
import android.content.SharedPreferences
import com.deedeedev.ytreader.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _favoriteLanguages = MutableStateFlow<Set<String>>(emptySet())
    val favoriteLanguages: StateFlow<Set<String>> = _favoriteLanguages.asStateFlow()

    private val _defaultFontSize = MutableStateFlow(16f)
    val defaultFontSize: StateFlow<Float> = _defaultFontSize.asStateFlow()

    private val _fontFamily = MutableStateFlow("Default")
    val fontFamily: StateFlow<String> = _fontFamily.asStateFlow()

    private val _lineHeightMultiplier = MutableStateFlow(1.5f)
    val lineHeightMultiplier: StateFlow<Float> = _lineHeightMultiplier.asStateFlow()

    private val _appTheme = MutableStateFlow(AppTheme.SYSTEM)
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _aiEndpoint = MutableStateFlow("")
    val aiEndpoint: StateFlow<String> = _aiEndpoint.asStateFlow()

    private val _aiApiKey = MutableStateFlow("")
    val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()

    private val _aiModel = MutableStateFlow(DEFAULT_AI_MODEL)
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _aiPrompt = MutableStateFlow(DEFAULT_AI_CLEANING_PROMPT)
    val aiPrompt: StateFlow<String> = _aiPrompt.asStateFlow()

    init {
        loadFavorites()
        loadDisplaySettings()
    }

    private fun loadFavorites() {
        val saved = prefs.getStringSet(KEY_FAVORITE_LANGUAGES, emptySet()) ?: emptySet()
        _favoriteLanguages.value = saved
    }

    private fun loadDisplaySettings() {
        _defaultFontSize.value = prefs.getFloat(KEY_DEFAULT_FONT_SIZE, 16f)
        _fontFamily.value = prefs.getString(KEY_FONT_FAMILY, "Default") ?: "Default"
        _lineHeightMultiplier.value = prefs.getFloat(KEY_LINE_HEIGHT_MULTIPLIER, 1.5f)
        _appTheme.value = AppTheme.fromStorageValue(prefs.getString(KEY_APP_THEME, AppTheme.SYSTEM.storageValue))
        _aiEndpoint.value = prefs.getString(KEY_AI_ENDPOINT, "") ?: ""
        _aiApiKey.value = prefs.getString(KEY_AI_API_KEY, "") ?: ""
        _aiModel.value = prefs.getString(KEY_AI_MODEL, DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL
        _aiPrompt.value = prefs.getString(KEY_AI_PROMPT, DEFAULT_AI_CLEANING_PROMPT)
            ?: DEFAULT_AI_CLEANING_PROMPT
    }

    fun toggleFavoriteLanguage(languageCode: String) {
        val current = _favoriteLanguages.value.toMutableSet()
        if (current.contains(languageCode)) {
            current.remove(languageCode)
        } else {
            current.add(languageCode)
        }
        
        prefs.edit().putStringSet(KEY_FAVORITE_LANGUAGES, current).apply()
        _favoriteLanguages.value = current
    }

    fun setDefaultFontSize(size: Float) {
        prefs.edit().putFloat(KEY_DEFAULT_FONT_SIZE, size).apply()
        _defaultFontSize.value = size
    }

    fun setFontFamily(family: String) {
        prefs.edit().putString(KEY_FONT_FAMILY, family).apply()
        _fontFamily.value = family
    }

    fun setLineHeightMultiplier(multiplier: Float) {
        prefs.edit().putFloat(KEY_LINE_HEIGHT_MULTIPLIER, multiplier).apply()
        _lineHeightMultiplier.value = multiplier
    }

    fun setAppTheme(appTheme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, appTheme.storageValue).apply()
        _appTheme.value = appTheme
    }

    fun setAiEndpoint(endpoint: String) {
        prefs.edit().putString(KEY_AI_ENDPOINT, endpoint).apply()
        _aiEndpoint.value = endpoint
    }

    fun setAiApiKey(key: String) {
        prefs.edit().putString(KEY_AI_API_KEY, key).apply()
        _aiApiKey.value = key
    }

    fun setAiModel(model: String) {
        prefs.edit().putString(KEY_AI_MODEL, model).apply()
        _aiModel.value = model
    }

    fun setAiPrompt(prompt: String) {
        prefs.edit().putString(KEY_AI_PROMPT, prompt).apply()
        _aiPrompt.value = prompt
    }

    companion object {
        private const val PREFS_NAME = "user_preferences"
        private const val KEY_FAVORITE_LANGUAGES = "favorite_languages"
        private const val KEY_DEFAULT_FONT_SIZE = "default_font_size"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_LINE_HEIGHT_MULTIPLIER = "line_height_multiplier"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_AI_ENDPOINT = "ai_endpoint"
        private const val KEY_AI_API_KEY = "ai_api_key"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_PROMPT = "ai_prompt"
    }
}
