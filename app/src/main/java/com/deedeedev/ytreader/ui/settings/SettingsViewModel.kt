package com.deedeedev.ytreader.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.StringProvider
import com.deedeedev.ytreader.data.AutoBackupScheduler
import com.deedeedev.ytreader.data.VideoThumbnailStore
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.VideoRepository
import com.deedeedev.ytreader.data.preferredThumbnailUrl
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer
import com.deedeedev.ytreader.ui.AppLanguage
import com.deedeedev.ytreader.ui.FontOption
import com.deedeedev.ytreader.ui.home.VideoCardSize
import com.deedeedev.ytreader.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val defaultFontSize: Float = 16f,
    val fontFamily: String = FontOption.DEFAULT.storageValue,
    val availableFonts: List<String> = FontOption.entries.map { it.storageValue },
    val lineHeightMultiplier: Float = 1.5f,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val availableThemes: List<AppTheme> = AppTheme.entries,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val availableLanguages: List<AppLanguage> = AppLanguage.entries,
    val videoCardSize: VideoCardSize = VideoCardSize.LARGE,
    val availableVideoCardSizes: List<VideoCardSize> = VideoCardSize.entries,
    val aiEndpoint: String = "",
    val aiApiKey: String = "",
    val aiModel: String = "",
    val aiPrompt: String = "",
    val isRunningThumbnailBulkAction: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val autoBackupDirectoryUri: String? = null,
    val autoBackupTime: String = "02:00",
    val autoBackupIncludeSettings: Boolean = false
)

class SettingsViewModel(
    private val stringProvider: StringProvider,
    private val appContext: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val youtubeRepository: YoutubeRepository,
    private val videoRepository: VideoRepository
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
            userPreferencesRepository.appLanguage.collect { appLanguage ->
                _uiState.update { it.copy(appLanguage = appLanguage) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.videoCardSize.collect { size ->
                _uiState.update { it.copy(videoCardSize = size) }
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
        viewModelScope.launch {
            userPreferencesRepository.autoBackupEnabled.collect { enabled ->
                _uiState.update { it.copy(autoBackupEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.autoBackupDirectoryUri.collect { uri ->
                _uiState.update { it.copy(autoBackupDirectoryUri = uri) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.autoBackupTime.collect { time ->
                _uiState.update { it.copy(autoBackupTime = time) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.autoBackupIncludeSettings.collect { include ->
                _uiState.update { it.copy(autoBackupIncludeSettings = include) }
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

    fun setAppLanguage(appLanguage: AppLanguage) {
        userPreferencesRepository.setAppLanguage(appLanguage)
    }

    fun setVideoCardSize(size: VideoCardSize) {
        userPreferencesRepository.setVideoCardSize(size)
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

    fun setAutoBackupEnabled(enabled: Boolean) {
        userPreferencesRepository.setAutoBackupEnabled(enabled)
        if (enabled) {
            val uri = userPreferencesRepository.getAutoBackupDirectoryUri()
            if (!uri.isNullOrBlank()) {
                AutoBackupScheduler.schedule(appContext, userPreferencesRepository.getAutoBackupTime())
            }
        } else {
            AutoBackupScheduler.cancel(appContext)
        }
    }

    fun setAutoBackupDirectoryUri(uri: String?) {
        userPreferencesRepository.setAutoBackupDirectoryUri(uri)
        if (userPreferencesRepository.getAutoBackupEnabled() && !uri.isNullOrBlank()) {
            AutoBackupScheduler.schedule(appContext, userPreferencesRepository.getAutoBackupTime())
        } else if (uri.isNullOrBlank()) {
            AutoBackupScheduler.cancel(appContext)
        }
    }

    fun setAutoBackupTime(time: String) {
        userPreferencesRepository.setAutoBackupTime(time)
        if (userPreferencesRepository.getAutoBackupEnabled()) {
            val uri = userPreferencesRepository.getAutoBackupDirectoryUri()
            if (!uri.isNullOrBlank()) {
                AutoBackupScheduler.schedule(appContext, time)
            }
        }
    }

    fun setAutoBackupIncludeSettings(include: Boolean) {
        userPreferencesRepository.setAutoBackupIncludeSettings(include)
    }

    suspend fun downloadMissingThumbnails(): String {
        if (_uiState.value.isRunningThumbnailBulkAction) {
            return stringProvider.getString(R.string.settings_working)
        }
        _uiState.update { it.copy(isRunningThumbnailBulkAction = true) }
        return try {
            val videos = videoRepository.getAllMissingThumbnailPath()
            if (videos.isEmpty()) {
                return stringProvider.getString(R.string.settings_thumbnail_download_none_missing)
            }

            var downloadedCount = 0
            var skippedCount = 0
            videos.forEach { video ->
                val streamUrl = video.videoUrl.takeIf { it.isNotBlank() }
                    ?: YouTubeVideoIdNormalizer.extractVideoId(video.videoId)?.let {
                        YouTubeVideoIdNormalizer.canonicalWatchUrl(it)
                    }
                    ?: video.videoId

                val result = runCatching {
                    val info = youtubeRepository.getStreamInfo(streamUrl)
                    val thumbnailUrl = preferredThumbnailUrl(info.thumbnails)
                    if (thumbnailUrl.isNullOrBlank()) {
                        false
                    } else {
                        val bytes = youtubeRepository.downloadBytes(thumbnailUrl)
                        if (bytes.isEmpty()) {
                            false
                        } else {
                            val localPath = VideoThumbnailStore.save(appContext, video.videoId, thumbnailUrl, bytes)
                            videoRepository.upsert(
                                video.copy(
                                    videoUrl = if (video.videoUrl.isBlank()) streamUrl else video.videoUrl,
                                    title = info.name.ifBlank { video.title },
                                    channelName = (info.uploaderName ?: video.channelName).ifBlank { video.channelName },
                                    uploadDate = info.uploadDate?.instant?.toEpochMilli() ?: video.uploadDate,
                                    thumbnailLocalPath = localPath,
                                    thumbnailSourceUrl = thumbnailUrl,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            true
                        }
                    }
                }.getOrDefault(false)

                if (result) {
                    downloadedCount += 1
                } else {
                    skippedCount += 1
                }
            }

            when {
                downloadedCount == 0 -> stringProvider.getString(
                    R.string.settings_thumbnail_download_no_results,
                    skippedCount
                )
                skippedCount == 0 -> stringProvider.getString(
                    R.string.settings_thumbnail_download_success,
                    downloadedCount
                )
                else -> stringProvider.getString(
                    R.string.settings_thumbnail_download_partial,
                    downloadedCount,
                    skippedCount
                )
            }
        } finally {
            _uiState.update { it.copy(isRunningThumbnailBulkAction = false) }
        }
    }

    suspend fun cleanOrphanThumbnails(): String {
        if (_uiState.value.isRunningThumbnailBulkAction) {
            return stringProvider.getString(R.string.settings_working)
        }
        _uiState.update { it.copy(isRunningThumbnailBulkAction = true) }
        return try {
            val videos = videoRepository.getAll()
            val referencedPaths = videoRepository.getAllReferencedThumbnailPaths().toSet()
            var removedFiles = 0
            VideoThumbnailStore.listFiles(appContext).forEach { file ->
                if (file.name !in referencedPaths && file.delete()) {
                    removedFiles += 1
                }
            }

            var clearedReferences = 0
            videos.forEach { video ->
                val path = video.thumbnailLocalPath ?: return@forEach
                if (VideoThumbnailStore.resolve(appContext, path) == null) {
                    videoRepository.upsert(
                        video.copy(
                            thumbnailLocalPath = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    clearedReferences += 1
                }
            }

            if (removedFiles == 0 && clearedReferences == 0) {
                stringProvider.getString(R.string.settings_thumbnail_cleanup_nothing_to_do)
            } else {
                stringProvider.getString(
                    R.string.settings_thumbnail_cleanup_result,
                    removedFiles,
                    clearedReferences
                )
            }
        } finally {
            _uiState.update { it.copy(isRunningThumbnailBulkAction = false) }
        }
    }

    companion object {
        fun provideFactory(
            stringProvider: StringProvider,
            appContext: Context,
            userPreferencesRepository: UserPreferencesRepository,
            youtubeRepository: YoutubeRepository,
            videoRepository: VideoRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    stringProvider,
                    appContext,
                    userPreferencesRepository,
                    youtubeRepository,
                    videoRepository
                ) as T
            }
        }
    }
}
