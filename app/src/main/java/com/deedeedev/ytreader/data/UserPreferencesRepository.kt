package com.deedeedev.ytreader.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.deedeedev.ytreader.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class VideoCollection(
    val id: String,
    val name: String,
    val videoIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _favoriteLanguages = MutableStateFlow<Set<String>>(emptySet())
    val favoriteLanguages: StateFlow<Set<String>> = _favoriteLanguages.asStateFlow()

    private val _videoCollections = MutableStateFlow<List<VideoCollection>>(emptyList())
    val videoCollections: StateFlow<List<VideoCollection>> = _videoCollections.asStateFlow()

    private val _defaultFontSize = MutableStateFlow(16f)
    val defaultFontSize: StateFlow<Float> = _defaultFontSize.asStateFlow()

    private val _fontFamily = MutableStateFlow("Default")
    val fontFamily: StateFlow<String> = _fontFamily.asStateFlow()

    private val _lineHeightMultiplier = MutableStateFlow(1.5f)
    val lineHeightMultiplier: StateFlow<Float> = _lineHeightMultiplier.asStateFlow()

    private val _appTheme = MutableStateFlow(AppTheme.SYSTEM)
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _appBrightness = MutableStateFlow(BRIGHTNESS_FOLLOW_SYSTEM)
    val appBrightness: StateFlow<Float> = _appBrightness.asStateFlow()

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
        loadCollections()
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
        _appBrightness.value = prefs.getFloat(KEY_APP_BRIGHTNESS, BRIGHTNESS_FOLLOW_SYSTEM)
        _aiEndpoint.value = prefs.getString(KEY_AI_ENDPOINT, "") ?: ""
        _aiApiKey.value = prefs.getString(KEY_AI_API_KEY, "") ?: ""
        _aiModel.value = prefs.getString(KEY_AI_MODEL, DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL
        _aiPrompt.value = prefs.getString(KEY_AI_PROMPT, DEFAULT_AI_CLEANING_PROMPT)
            ?: DEFAULT_AI_CLEANING_PROMPT
    }

    private fun loadCollections() {
        _videoCollections.value = decodeCollections(prefs.getString(KEY_VIDEO_COLLECTIONS, null))
    }

    private fun persistCollections(collections: List<VideoCollection>) {
        prefs.edit().putString(KEY_VIDEO_COLLECTIONS, gson.toJson(collections)).apply()
        _videoCollections.value = collections
    }

    private fun decodeCollections(raw: String?): List<VideoCollection> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }

        return try {
            val type = object : TypeToken<List<VideoCollection>>() {}.type
            val parsed = gson.fromJson<List<VideoCollection>>(raw, type) ?: emptyList()
            parsed
                .map { collection ->
                    collection.copy(
                        name = collection.name.trim(),
                        videoIds = collection.videoIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    )
                }
                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
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

    fun createCollection(name: String): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return false
        }
        if (_videoCollections.value.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            return false
        }

        val updated = _videoCollections.value + VideoCollection(
            id = UUID.randomUUID().toString(),
            name = trimmedName
        )
        persistCollections(updated)
        return true
    }

    fun renameCollection(collectionId: String, newName: String): Boolean {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            return false
        }

        val existing = _videoCollections.value
        if (existing.none { it.id == collectionId }) {
            return false
        }
        if (existing.any { it.id != collectionId && it.name.equals(trimmedName, ignoreCase = true) }) {
            return false
        }

        val updated = existing.map { collection ->
            if (collection.id == collectionId) {
                collection.copy(name = trimmedName)
            } else {
                collection
            }
        }
        persistCollections(updated)
        return true
    }

    fun deleteCollection(collectionId: String) {
        val updated = _videoCollections.value.filterNot { it.id == collectionId }
        persistCollections(updated)
    }

    fun addVideoToCollection(collectionId: String, videoId: String): Boolean {
        val trimmedVideoId = videoId.trim()
        if (trimmedVideoId.isBlank()) {
            return false
        }

        var foundCollection = false
        var changed = false
        val updated = _videoCollections.value.map { collection ->
            if (collection.id != collectionId) {
                return@map collection
            }
            foundCollection = true
            if (collection.videoIds.contains(trimmedVideoId)) {
                return@map collection
            }
            changed = true
            collection.copy(videoIds = collection.videoIds + trimmedVideoId)
        }

        if (!foundCollection) {
            return false
        }
        if (changed) {
            persistCollections(updated)
        }
        return true
    }

    fun removeVideoFromCollection(collectionId: String, videoId: String) {
        val updated = _videoCollections.value.map { collection ->
            if (collection.id == collectionId) {
                collection.copy(videoIds = collection.videoIds.filterNot { it == videoId })
            } else {
                collection
            }
        }
        persistCollections(updated)
    }

    fun removeVideoFromAllCollections(videoId: String) {
        val updated = _videoCollections.value.map { collection ->
            collection.copy(videoIds = collection.videoIds.filterNot { it == videoId })
        }
        persistCollections(updated)
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

    fun setAppBrightness(brightness: Float) {
        val normalized = if (brightness == BRIGHTNESS_FOLLOW_SYSTEM) {
            BRIGHTNESS_FOLLOW_SYSTEM
        } else {
            brightness.coerceIn(0f, 1f)
        }
        prefs.edit().putFloat(KEY_APP_BRIGHTNESS, normalized).apply()
        _appBrightness.value = normalized
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

    fun getAiEndpoint(): String = prefs.getString(KEY_AI_ENDPOINT, "") ?: ""

    fun getAiApiKey(): String = prefs.getString(KEY_AI_API_KEY, "") ?: ""

    fun getAiModel(): String = prefs.getString(KEY_AI_MODEL, DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL

    fun getAiPrompt(): String =
        prefs.getString(KEY_AI_PROMPT, DEFAULT_AI_CLEANING_PROMPT) ?: DEFAULT_AI_CLEANING_PROMPT

    companion object {
        private const val PREFS_NAME = "user_preferences"
        private const val KEY_FAVORITE_LANGUAGES = "favorite_languages"
        private const val KEY_DEFAULT_FONT_SIZE = "default_font_size"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_LINE_HEIGHT_MULTIPLIER = "line_height_multiplier"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_APP_BRIGHTNESS = "app_brightness"
        private const val KEY_AI_ENDPOINT = "ai_endpoint"
        private const val KEY_AI_API_KEY = "ai_api_key"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_PROMPT = "ai_prompt"
        private const val KEY_VIDEO_COLLECTIONS = "video_collections"

        const val BRIGHTNESS_FOLLOW_SYSTEM = -1f
    }
}
