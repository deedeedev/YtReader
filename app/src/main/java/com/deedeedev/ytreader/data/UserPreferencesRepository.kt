package com.deedeedev.ytreader.data

import android.content.Context
import android.content.SharedPreferences
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer
import com.deedeedev.ytreader.ui.AppLanguage
import com.deedeedev.ytreader.ui.home.VideoCardSize
import com.deedeedev.ytreader.ui.settings.ProgressIndicatorMode
import com.deedeedev.ytreader.ui.theme.AppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PersistedLibraryFilters(
    val selectedChannelFilter: String? = null,
    val visibilityFilter: String = "ALL",
    val readStatusFilter: String = "ALL",
    val sortOption: String = "DOWNLOADED",
    val isAscending: Boolean = false
)

data class PersistedCollectionFilters(
    val selectedChannelFilter: String? = null,
    val readStatusFilter: String = "ALL",
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
    val appLanguage: String = AppLanguage.SYSTEM.storageValue,
    val progressIndicatorMode: String = ProgressIndicatorMode.FADE_OUT.storageValue,
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

    private val _progressIndicatorMode = MutableStateFlow(ProgressIndicatorMode.FADE_OUT)
    val progressIndicatorMode: StateFlow<ProgressIndicatorMode> = _progressIndicatorMode.asStateFlow()

    private val _appTheme = MutableStateFlow(AppTheme.SYSTEM)
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _appBrightness = MutableStateFlow(BRIGHTNESS_FOLLOW_SYSTEM)
    val appBrightness: StateFlow<Float> = _appBrightness.asStateFlow()

    private val _appLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _videoCardSize = MutableStateFlow(VideoCardSize.LARGE)
    val videoCardSize: StateFlow<VideoCardSize> = _videoCardSize.asStateFlow()

    private val _aiEndpoint = MutableStateFlow("")
    val aiEndpoint: StateFlow<String> = _aiEndpoint.asStateFlow()

    private val _aiApiKey = MutableStateFlow("")
    val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()

    private val _aiModel = MutableStateFlow(DEFAULT_AI_MODEL)
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _aiPrompt = MutableStateFlow(DEFAULT_AI_CLEANING_PROMPT)
    val aiPrompt: StateFlow<String> = _aiPrompt.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(false)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _autoBackupDirectoryUri = MutableStateFlow<String?>(null)
    val autoBackupDirectoryUri: StateFlow<String?> = _autoBackupDirectoryUri.asStateFlow()

    private val _autoBackupTime = MutableStateFlow("02:00")
    val autoBackupTime: StateFlow<String> = _autoBackupTime.asStateFlow()

    private val _autoBackupIncludeSettings = MutableStateFlow(false)
    val autoBackupIncludeSettings: StateFlow<Boolean> = _autoBackupIncludeSettings.asStateFlow()

    private val _localCleanNormalizeUnicodeWhitespace = MutableStateFlow(true)
    val localCleanNormalizeUnicodeWhitespace: StateFlow<Boolean> = _localCleanNormalizeUnicodeWhitespace.asStateFlow()

    private val _localCleanRemoveHtmlTags = MutableStateFlow(true)
    val localCleanRemoveHtmlTags: StateFlow<Boolean> = _localCleanRemoveHtmlTags.asStateFlow()

    private val _localCleanRemoveAsdCcArtifacts = MutableStateFlow(true)
    val localCleanRemoveAsdCcArtifacts: StateFlow<Boolean> = _localCleanRemoveAsdCcArtifacts.asStateFlow()

    private val _localCleanNormalizeQuotationMarks = MutableStateFlow(true)
    val localCleanNormalizeQuotationMarks: StateFlow<Boolean> = _localCleanNormalizeQuotationMarks.asStateFlow()

    private val _localCleanNormalizeEllipsis = MutableStateFlow(true)
    val localCleanNormalizeEllipsis: StateFlow<Boolean> = _localCleanNormalizeEllipsis.asStateFlow()

    private val _localCleanRemoveDuplicateSpaces = MutableStateFlow(true)
    val localCleanRemoveDuplicateSpaces: StateFlow<Boolean> = _localCleanRemoveDuplicateSpaces.asStateFlow()

    private val _localCleanRemoveSpacesBeforePunctuation = MutableStateFlow(true)
    val localCleanRemoveSpacesBeforePunctuation: StateFlow<Boolean> = _localCleanRemoveSpacesBeforePunctuation.asStateFlow()

    private val _localCleanTrimLines = MutableStateFlow(true)
    val localCleanTrimLines: StateFlow<Boolean> = _localCleanTrimLines.asStateFlow()

    private val _localCleanRemoveBlankLines = MutableStateFlow(true)
    val localCleanRemoveBlankLines: StateFlow<Boolean> = _localCleanRemoveBlankLines.asStateFlow()

    private val _localCleanCapitalizeFirstLetter = MutableStateFlow(true)
    val localCleanCapitalizeFirstLetter: StateFlow<Boolean> = _localCleanCapitalizeFirstLetter.asStateFlow()

    private val _localCleanAddSpaceAfterPunctuation = MutableStateFlow(true)
    val localCleanAddSpaceAfterPunctuation: StateFlow<Boolean> = _localCleanAddSpaceAfterPunctuation.asStateFlow()

    private val _localCleanCapitalizeAfterSentenceEnd = MutableStateFlow(true)
    val localCleanCapitalizeAfterSentenceEnd: StateFlow<Boolean> = _localCleanCapitalizeAfterSentenceEnd.asStateFlow()

    private val _localCleanMergeShortFragments = MutableStateFlow(true)
    val localCleanMergeShortFragments: StateFlow<Boolean> = _localCleanMergeShortFragments.asStateFlow()

    private val _localCleanRemoveMidSentenceLineBreaks = MutableStateFlow(true)
    val localCleanRemoveMidSentenceLineBreaks: StateFlow<Boolean> = _localCleanRemoveMidSentenceLineBreaks.asStateFlow()

    private val _localCleanReplaceLineBreaksWithSpace = MutableStateFlow(false)
    val localCleanReplaceLineBreaksWithSpace: StateFlow<Boolean> = _localCleanReplaceLineBreaksWithSpace.asStateFlow()

    init {
        loadFavorites()
        loadDisplaySettings()
        loadAutoBackupSettings()
        loadLocalCleaningSettings()
    }

    private fun loadFavorites() {
        val saved = prefs.getStringSet(KEY_FAVORITE_LANGUAGES, emptySet()) ?: emptySet()
        _favoriteLanguages.value = saved
    }

    private fun loadDisplaySettings() {
        _defaultFontSize.value = prefs.getFloat(KEY_DEFAULT_FONT_SIZE, 16f)
        _fontFamily.value = prefs.getString(KEY_FONT_FAMILY, "Default") ?: "Default"
        _lineHeightMultiplier.value = prefs.getFloat(KEY_LINE_HEIGHT_MULTIPLIER, 1.5f)
        _progressIndicatorMode.value = ProgressIndicatorMode.fromStorageValue(
            prefs.getString(KEY_PROGRESS_INDICATOR_MODE, ProgressIndicatorMode.FADE_OUT.storageValue) ?: ProgressIndicatorMode.FADE_OUT.storageValue
        )
        _appTheme.value = AppTheme.fromStorageValue(prefs.getString(KEY_APP_THEME, AppTheme.SYSTEM.storageValue))
        _appBrightness.value = prefs.getFloat(KEY_APP_BRIGHTNESS, BRIGHTNESS_FOLLOW_SYSTEM)
        _appLanguage.value = AppLanguage.fromStorageValue(prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.storageValue))
        _videoCardSize.value = VideoCardSize.valueOf(
            prefs.getString(KEY_VIDEO_CARD_SIZE, VideoCardSize.LARGE.name) ?: VideoCardSize.LARGE.name
        )
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

    fun setProgressIndicatorMode(mode: ProgressIndicatorMode) {
        prefs.edit().putString(KEY_PROGRESS_INDICATOR_MODE, mode.storageValue).apply()
        _progressIndicatorMode.value = mode
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

    fun setAppLanguage(appLanguage: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, appLanguage.storageValue).apply()
        _appLanguage.value = appLanguage
    }

    fun setVideoCardSize(size: VideoCardSize) {
        prefs.edit().putString(KEY_VIDEO_CARD_SIZE, size.name).apply()
        _videoCardSize.value = size
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

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
        _autoBackupEnabled.value = enabled
    }

    fun setAutoBackupDirectoryUri(uri: String?) {
        prefs.edit().putString(KEY_AUTO_BACKUP_DIRECTORY_URI, uri).apply()
        _autoBackupDirectoryUri.value = uri
    }

    fun setAutoBackupTime(time: String) {
        prefs.edit().putString(KEY_AUTO_BACKUP_TIME, time).apply()
        _autoBackupTime.value = time
    }

    fun setAutoBackupIncludeSettings(include: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_INCLUDE_SETTINGS, include).apply()
        _autoBackupIncludeSettings.value = include
    }

    fun setLocalCleanNormalizeUnicodeWhitespace(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_NORMALIZE_UNICODE_WHITESPACE, enabled).apply()
        _localCleanNormalizeUnicodeWhitespace.value = enabled
    }

    fun setLocalCleanRemoveHtmlTags(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_REMOVE_HTML_TAGS, enabled).apply()
        _localCleanRemoveHtmlTags.value = enabled
    }

    fun setLocalCleanRemoveAsdCcArtifacts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_REMOVE_ASD_CC_ARTIFACTS, enabled).apply()
        _localCleanRemoveAsdCcArtifacts.value = enabled
    }

    fun setLocalCleanNormalizeQuotationMarks(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_NORMALIZE_QUOTATION_MARKS, enabled).apply()
        _localCleanNormalizeQuotationMarks.value = enabled
    }

    fun setLocalCleanNormalizeEllipsis(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_NORMALIZE_ELLIPSIS, enabled).apply()
        _localCleanNormalizeEllipsis.value = enabled
    }

    fun setLocalCleanRemoveDuplicateSpaces(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_REMOVE_DUPLICATE_SPACES, enabled).apply()
        _localCleanRemoveDuplicateSpaces.value = enabled
    }

    fun setLocalCleanRemoveSpacesBeforePunctuation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_REMOVE_SPACES_BEFORE_PUNCTUATION, enabled).apply()
        _localCleanRemoveSpacesBeforePunctuation.value = enabled
    }

    fun setLocalCleanTrimLines(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_TRIM_LINES, enabled).apply()
        _localCleanTrimLines.value = enabled
    }

    fun setLocalCleanRemoveBlankLines(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_REMOVE_BLANK_LINES, enabled).apply()
        _localCleanRemoveBlankLines.value = enabled
    }

    fun setLocalCleanCapitalizeFirstLetter(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_CAPITALIZE_FIRST_LETTER, enabled).apply()
        _localCleanCapitalizeFirstLetter.value = enabled
    }

    fun setLocalCleanAddSpaceAfterPunctuation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_ADD_SPACE_AFTER_PUNCTUATION, enabled).apply()
        _localCleanAddSpaceAfterPunctuation.value = enabled
    }

    fun setLocalCleanCapitalizeAfterSentenceEnd(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_CAPITALIZE_AFTER_SENTENCE_END, enabled).apply()
        _localCleanCapitalizeAfterSentenceEnd.value = enabled
    }

    fun setLocalCleanMergeShortFragments(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_MERGE_SHORT_FRAGMENTS, enabled).apply()
        _localCleanMergeShortFragments.value = enabled
    }

    fun setLocalCleanRemoveMidSentenceLineBreaks(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_REMOVE_MID_SENTENCE_LINE_BREAKS, enabled).apply()
        _localCleanRemoveMidSentenceLineBreaks.value = enabled
    }

    fun setLocalCleanReplaceLineBreaksWithSpace(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_CLEAN_REPLACE_LINE_BREAKS_WITH_SPACE, enabled).apply()
        _localCleanReplaceLineBreaksWithSpace.value = enabled
    }

    fun getAutoBackupEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
    fun getAutoBackupDirectoryUri(): String? = prefs.getString(KEY_AUTO_BACKUP_DIRECTORY_URI, null)
    fun getAutoBackupTime(): String = prefs.getString(KEY_AUTO_BACKUP_TIME, "02:00") ?: "02:00"
    fun getAutoBackupIncludeSettings(): Boolean = prefs.getBoolean(KEY_AUTO_BACKUP_INCLUDE_SETTINGS, false)

    private fun loadAutoBackupSettings() {
        _autoBackupEnabled.value = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
        _autoBackupDirectoryUri.value = prefs.getString(KEY_AUTO_BACKUP_DIRECTORY_URI, null)
        _autoBackupTime.value = prefs.getString(KEY_AUTO_BACKUP_TIME, "02:00") ?: "02:00"
        _autoBackupIncludeSettings.value = prefs.getBoolean(KEY_AUTO_BACKUP_INCLUDE_SETTINGS, false)
    }

    private fun loadLocalCleaningSettings() {
        _localCleanNormalizeUnicodeWhitespace.value = prefs.getBoolean(KEY_LOCAL_CLEAN_NORMALIZE_UNICODE_WHITESPACE, true)
        _localCleanRemoveHtmlTags.value = prefs.getBoolean(KEY_LOCAL_CLEAN_REMOVE_HTML_TAGS, true)
        _localCleanRemoveAsdCcArtifacts.value = prefs.getBoolean(KEY_LOCAL_CLEAN_REMOVE_ASD_CC_ARTIFACTS, true)
        _localCleanNormalizeQuotationMarks.value = prefs.getBoolean(KEY_LOCAL_CLEAN_NORMALIZE_QUOTATION_MARKS, true)
        _localCleanNormalizeEllipsis.value = prefs.getBoolean(KEY_LOCAL_CLEAN_NORMALIZE_ELLIPSIS, true)
        _localCleanRemoveDuplicateSpaces.value = prefs.getBoolean(KEY_LOCAL_CLEAN_REMOVE_DUPLICATE_SPACES, true)
        _localCleanRemoveSpacesBeforePunctuation.value = prefs.getBoolean(KEY_LOCAL_CLEAN_REMOVE_SPACES_BEFORE_PUNCTUATION, true)
        _localCleanTrimLines.value = prefs.getBoolean(KEY_LOCAL_CLEAN_TRIM_LINES, true)
        _localCleanRemoveBlankLines.value = prefs.getBoolean(KEY_LOCAL_CLEAN_REMOVE_BLANK_LINES, true)
        _localCleanCapitalizeFirstLetter.value = prefs.getBoolean(KEY_LOCAL_CLEAN_CAPITALIZE_FIRST_LETTER, true)
        _localCleanAddSpaceAfterPunctuation.value = prefs.getBoolean(KEY_LOCAL_CLEAN_ADD_SPACE_AFTER_PUNCTUATION, true)
        _localCleanCapitalizeAfterSentenceEnd.value = prefs.getBoolean(KEY_LOCAL_CLEAN_CAPITALIZE_AFTER_SENTENCE_END, true)
        _localCleanMergeShortFragments.value = prefs.getBoolean(KEY_LOCAL_CLEAN_MERGE_SHORT_FRAGMENTS, true)
        _localCleanRemoveMidSentenceLineBreaks.value = prefs.getBoolean(KEY_LOCAL_CLEAN_REMOVE_MID_SENTENCE_LINE_BREAKS, true)
        _localCleanReplaceLineBreaksWithSpace.value = prefs.getBoolean(KEY_LOCAL_CLEAN_REPLACE_LINE_BREAKS_WITH_SPACE, false)
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
            progressIndicatorMode = _progressIndicatorMode.value.storageValue,
            appTheme = _appTheme.value.storageValue,
            appBrightness = _appBrightness.value,
            appLanguage = _appLanguage.value.storageValue,
            aiEndpoint = _aiEndpoint.value,
            aiApiKey = "",
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

        val editor = prefs.edit()
            .putStringSet(KEY_FAVORITE_LANGUAGES, backup.favoriteLanguages.toMutableSet())
            .putFloat(KEY_DEFAULT_FONT_SIZE, backup.defaultFontSize)
            .putString(KEY_FONT_FAMILY, backup.fontFamily)
            .putFloat(KEY_LINE_HEIGHT_MULTIPLIER, backup.lineHeightMultiplier)
            .putString(KEY_PROGRESS_INDICATOR_MODE, backup.progressIndicatorMode)
            .putString(KEY_APP_THEME, backup.appTheme)
            .putFloat(KEY_APP_BRIGHTNESS, backup.appBrightness)
            .putString(KEY_APP_LANGUAGE, backup.appLanguage)
            .putString(KEY_AI_ENDPOINT, backup.aiEndpoint)
            .putString(KEY_AI_MODEL, backup.aiModel)
            .putString(KEY_AI_PROMPT, backup.aiPrompt)

        if (backup.aiApiKey.isNotEmpty()) {
            editor.putString(KEY_AI_API_KEY, backup.aiApiKey)
        }

        editor.apply()
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
        private const val KEY_PROGRESS_INDICATOR_MODE = "progress_indicator_mode"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_APP_BRIGHTNESS = "app_brightness"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_VIDEO_CARD_SIZE = "video_card_size"
        private const val KEY_AI_ENDPOINT = "ai_endpoint"
        private const val KEY_AI_API_KEY = "ai_api_key"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_PROMPT = "ai_prompt"
        private const val KEY_VIDEO_COLLECTIONS = "video_collections"
        private const val KEY_COLLECTION_IDS_NORMALIZED = "collection_ids_normalized"
        private const val KEY_COLLECTIONS_MIGRATED_TO_DATABASE = "collections_migrated_to_database"
        private const val KEY_LIBRARY_FILTER_STATE = "library_filter_state"
        private const val KEY_COLLECTION_FILTER_STATES = "collection_filter_states"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_AUTO_BACKUP_DIRECTORY_URI = "auto_backup_directory_uri"
        private const val KEY_AUTO_BACKUP_TIME = "auto_backup_time"
        private const val KEY_AUTO_BACKUP_INCLUDE_SETTINGS = "auto_backup_include_settings"
        private const val KEY_LOCAL_CLEAN_NORMALIZE_UNICODE_WHITESPACE = "local_clean_normalize_unicode_whitespace"
        private const val KEY_LOCAL_CLEAN_REMOVE_HTML_TAGS = "local_clean_remove_html_tags"
        private const val KEY_LOCAL_CLEAN_REMOVE_ASD_CC_ARTIFACTS = "local_clean_remove_asd_cc_artifacts"
        private const val KEY_LOCAL_CLEAN_NORMALIZE_QUOTATION_MARKS = "local_clean_normalize_quotation_marks"
        private const val KEY_LOCAL_CLEAN_NORMALIZE_ELLIPSIS = "local_clean_normalize_ellipsis"
        private const val KEY_LOCAL_CLEAN_REMOVE_DUPLICATE_SPACES = "local_clean_remove_duplicate_spaces"
        private const val KEY_LOCAL_CLEAN_REMOVE_SPACES_BEFORE_PUNCTUATION = "local_clean_remove_spaces_before_punctuation"
        private const val KEY_LOCAL_CLEAN_TRIM_LINES = "local_clean_trim_lines"
        private const val KEY_LOCAL_CLEAN_REMOVE_BLANK_LINES = "local_clean_remove_blank_lines"
        private const val KEY_LOCAL_CLEAN_CAPITALIZE_FIRST_LETTER = "local_clean_capitalize_first_letter"
        private const val KEY_LOCAL_CLEAN_ADD_SPACE_AFTER_PUNCTUATION = "local_clean_add_space_after_punctuation"
        private const val KEY_LOCAL_CLEAN_CAPITALIZE_AFTER_SENTENCE_END = "local_clean_capitalize_after_sentence_end"
        private const val KEY_LOCAL_CLEAN_MERGE_SHORT_FRAGMENTS = "local_clean_merge_short_fragments"
        private const val KEY_LOCAL_CLEAN_REMOVE_MID_SENTENCE_LINE_BREAKS = "local_clean_remove_mid_sentence_line_breaks"
        private const val KEY_LOCAL_CLEAN_REPLACE_LINE_BREAKS_WITH_SPACE = "local_clean_replace_line_breaks_with_space"

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
