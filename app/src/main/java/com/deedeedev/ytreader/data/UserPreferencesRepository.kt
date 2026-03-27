package com.deedeedev.ytreader.data

import android.content.Context
import android.content.SharedPreferences
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer
import com.deedeedev.ytreader.ui.theme.AppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PersistedLibraryFilters(
    val selectedChannelFilter: String? = null,
    val visibilityFilter: String = "ALL",
    val sortOption: String = "DOWNLOADED",
    val isAscending: Boolean = false
)

data class PersistedCollectionFilters(
    val selectedChannelFilter: String? = null,
    val sortOption: String = "DOWNLOADED",
    val isAscending: Boolean = false
)

data class VideoCollection(
    val id: String,
    val name: String,
    val videoIds: List<String> = emptyList(),
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class PreferencesBackup(
    val favoriteLanguages: Set<String> = emptySet(),
    val defaultFontSize: Float = 16f,
    val fontFamily: String = "Default",
    val lineHeightMultiplier: Float = 1.5f,
    val appTheme: String = AppTheme.SYSTEM.storageValue,
    val appBrightness: Float = UserPreferencesRepository.BRIGHTNESS_FOLLOW_SYSTEM,
    val aiEndpoint: String = "",
    val aiApiKey: String = "",
    val aiModel: String = DEFAULT_AI_MODEL,
    val aiPrompt: String = DEFAULT_AI_CLEANING_PROMPT
)

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
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

    fun exportPreferencesJson(): String {
        val backup = PreferencesBackup(
            favoriteLanguages = _favoriteLanguages.value,
            defaultFontSize = _defaultFontSize.value,
            fontFamily = _fontFamily.value,
            lineHeightMultiplier = _lineHeightMultiplier.value,
            appTheme = _appTheme.value.storageValue,
            appBrightness = _appBrightness.value,
            aiEndpoint = _aiEndpoint.value,
            aiApiKey = _aiApiKey.value,
            aiModel = _aiModel.value,
            aiPrompt = _aiPrompt.value
        )
        return gson.toJson(backup)
    }

    fun importPreferencesJson(json: String): Boolean {
        val backup = try {
            gson.fromJson(json, PreferencesBackup::class.java)
        } catch (_: Exception) {
            null
        } ?: return false

        prefs.edit()
            .putStringSet(KEY_FAVORITE_LANGUAGES, backup.favoriteLanguages.toMutableSet())
            .putFloat(KEY_DEFAULT_FONT_SIZE, backup.defaultFontSize)
            .putString(KEY_FONT_FAMILY, backup.fontFamily)
            .putFloat(KEY_LINE_HEIGHT_MULTIPLIER, backup.lineHeightMultiplier)
            .putString(KEY_APP_THEME, backup.appTheme)
            .putFloat(KEY_APP_BRIGHTNESS, backup.appBrightness)
            .putString(KEY_AI_ENDPOINT, backup.aiEndpoint)
            .putString(KEY_AI_API_KEY, backup.aiApiKey)
            .putString(KEY_AI_MODEL, backup.aiModel)
            .putString(KEY_AI_PROMPT, backup.aiPrompt)
            .apply()

        loadFavorites()
        loadDisplaySettings()
        return true
    }

    fun getLegacyCollectionsJson(): String? = prefs.getString(KEY_VIDEO_COLLECTIONS, null)

    fun getLibraryFilterState(): PersistedLibraryFilters {
        val raw = prefs.getString(KEY_LIBRARY_FILTER_STATE, null)
        return try {
            gson.fromJson(raw, PersistedLibraryFilters::class.java) ?: PersistedLibraryFilters()
        } catch (_: Exception) {
            PersistedLibraryFilters()
        }
    }

    fun saveLibraryFilterState(state: PersistedLibraryFilters) {
        prefs.edit().putString(KEY_LIBRARY_FILTER_STATE, gson.toJson(state)).apply()
    }

    fun getCollectionFilterStates(): Map<String, PersistedCollectionFilters> {
        val raw = prefs.getString(KEY_COLLECTION_FILTER_STATES, null)
        return try {
            val type = object : TypeToken<Map<String, PersistedCollectionFilters>>() {}.type
            gson.fromJson<Map<String, PersistedCollectionFilters>>(raw, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getCollectionFilterState(collectionId: String): PersistedCollectionFilters? {
        return getCollectionFilterStates()[collectionId]
    }

    fun saveCollectionFilterState(collectionId: String, state: PersistedCollectionFilters) {
        val updated = getCollectionFilterStates().toMutableMap()
        updated[collectionId] = state
        prefs.edit().putString(KEY_COLLECTION_FILTER_STATES, gson.toJson(updated)).apply()
    }

    fun removeCollectionFilterState(collectionId: String) {
        val updated = getCollectionFilterStates().toMutableMap()
        if (updated.remove(collectionId) != null) {
            prefs.edit().putString(KEY_COLLECTION_FILTER_STATES, gson.toJson(updated)).apply()
        }
    }

    fun clearLegacyCollections() {
        prefs.edit().remove(KEY_VIDEO_COLLECTIONS).remove(KEY_COLLECTION_IDS_NORMALIZED).apply()
    }

    fun areCollectionsMigratedToDatabase(): Boolean {
        return prefs.getBoolean(KEY_COLLECTIONS_MIGRATED_TO_DATABASE, false)
    }

    fun markCollectionsMigratedToDatabase() {
        prefs.edit().putBoolean(KEY_COLLECTIONS_MIGRATED_TO_DATABASE, true).apply()
    }

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
        private const val KEY_COLLECTION_IDS_NORMALIZED = "collection_ids_normalized"
        private const val KEY_COLLECTIONS_MIGRATED_TO_DATABASE = "collections_migrated_to_database"
        private const val KEY_LIBRARY_FILTER_STATE = "library_filter_state"
        private const val KEY_COLLECTION_FILTER_STATES = "collection_filter_states"

        const val BRIGHTNESS_FOLLOW_SYSTEM = -1f

        fun parseLegacyCollectionsJson(raw: String?): List<VideoCollection> {
            if (raw.isNullOrBlank()) {
                return emptyList()
            }

            return try {
                val gson = Gson()
                val type = object : TypeToken<List<VideoCollection>>() {}.type
                val parsed = gson.fromJson<List<VideoCollection>>(raw, type) ?: emptyList()
                parsed
                    .map { collection ->
                        collection.copy(
                            name = collection.name.trim(),
                            videoIds = collection.videoIds.mapNotNull { rawValue ->
                                val trimmed = rawValue.trim()
                                if (trimmed.isBlank()) {
                                    null
                                } else {
                                    YouTubeVideoIdNormalizer.extractVideoId(trimmed) ?: trimmed
                                }
                            }.distinct()
                        )
                    }
                    .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
