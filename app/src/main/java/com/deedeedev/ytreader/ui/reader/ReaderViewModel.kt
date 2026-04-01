package com.deedeedev.ytreader.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.AiCleaningWorkScheduler
import com.deedeedev.ytreader.widget.ReaderWidgetProvider
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.HighlightNoteEntity
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val subtitle: SubtitleEntity? = null,
    val content: String = "",
    val originalParsedText: String = "",
    val highlights: List<TextHighlight> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val fontSize: Float = 16f,
    val fontFamily: String = "Default",
    val lineHeightMultiplier: Float = 1.5f,
    val isAiCleaning: Boolean = false,
    val pendingAiCleanedText: String? = null,
    val aiCleaningErrorSummary: String? = null,
    val aiCleaningErrorLog: String? = null,
    val isLoading: Boolean = false
)

class ReaderViewModel(
    private val appContext: Context,
    private val subtitleDao: SubtitleDao,
    private val highlightNoteDao: HighlightNoteDao,
    private val bookmarkDao: BookmarkDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val subtitleId: Long,
    private val widgetUpdater: (Context) -> Unit = { context -> ReaderWidgetProvider.notifyWidgetChanged(context) }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState(isLoading = true))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        markSubtitleOpened()
        loadSubtitle()
        loadPreferences()
    }

    private fun markSubtitleOpened() {
        viewModelScope.launch {
            subtitleDao.updateLastOpenedAt(subtitleId, System.currentTimeMillis())
            widgetUpdater(appContext)
        }
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
            subtitleDao.observeById(subtitleId)
                .combine(highlightNoteDao.observeBySubtitleId(subtitleId)) { subtitle, notes ->
                    subtitle to notes
                }
                .combine(bookmarkDao.observeBySubtitleId(subtitleId)) { (subtitle, notes), bookmarks ->
                    Triple(subtitle, notes, bookmarks)
                }
                .collectLatest { (subtitle, notes, bookmarks) ->
                if (subtitle != null) {
                    val originalParsedText = SubtitleParser.parse(subtitle.content)
                    val content = subtitle.studyContent ?: originalParsedText
                    val notesByRange = notes.associateBy { HighlightRangeKey(it.highlightStart, it.highlightEnd) }
                    val highlights = parseHighlights(subtitle.highlights).map { highlight ->
                        highlight.copy(
                            note = notesByRange[HighlightRangeKey(highlight.start, highlight.end)]?.noteText
                        )
                    }

                    _uiState.update {
                        it.copy(
                            subtitle = subtitle,
                            content = content,
                            originalParsedText = originalParsedText,
                            highlights = highlights,
                            bookmarks = bookmarks,
                            fontSize = subtitle.fontSize,
                            fontFamily = subtitle.fontFamily,
                            isAiCleaning = subtitle.aiCleaningInProgress,
                            pendingAiCleanedText = subtitle.aiCleaningPendingResult,
                            aiCleaningErrorSummary = subtitle.aiCleaningErrorSummary,
                            aiCleaningErrorLog = subtitle.aiCleaningErrorLog,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun updateLastTimestamp(timestamp: Long) {
        viewModelScope.launch {
            subtitleDao.updateLastTimestamp(subtitleId, timestamp)
        }
    }

    fun updateLastStudyScroll(scroll: Int) {
        viewModelScope.launch {
            subtitleDao.updateLastStudyScroll(subtitleId, scroll.coerceAtLeast(0))
        }
    }

    private var lastSavedPercent = 0

    fun updateReadingProgress(percent: Int, currentPage: Int, totalPages: Int) {
        if (percent < lastSavedPercent && percent < 100) return
        lastSavedPercent = percent.coerceIn(0, 100)
        viewModelScope.launch {
            subtitleDao.updateReadingProgress(
                subtitleId = subtitleId,
                percent = percent.coerceIn(0, 100),
                currentPage = currentPage.coerceAtLeast(0),
                totalPages = totalPages.coerceAtLeast(0)
            )
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
        val oldContent = _uiState.value.content
        val oldHighlights = _uiState.value.highlights
        val oldBookmarks = _uiState.value.bookmarks

        val hasAnnotations = oldHighlights.isNotEmpty() || oldBookmarks.isNotEmpty()
        val textChanged = oldContent != content

        val remapResult = if (textChanged && hasAnnotations) {
            remapAnnotations(oldContent, content, oldHighlights, oldBookmarks)
        } else null

        val newHighlights = remapResult?.highlights ?: if (textChanged) emptyList() else oldHighlights
        val newBookmarks = remapResult?.bookmarks ?: if (textChanged) emptyList() else oldBookmarks

        _uiState.update { state ->
            state.copy(
                content = content,
                highlights = newHighlights,
                subtitle = state.subtitle?.copy(
                    studyContent = content,
                    highlights = serializeHighlights(newHighlights)
                )
            )
        }

        viewModelScope.launch {
            subtitleDao.updateStudyContent(subtitleId, content)
            subtitleDao.updateHighlights(subtitleId = subtitleId, serializeHighlights(newHighlights))

            highlightNoteDao.deleteBySubtitleId(subtitleId)
            bookmarkDao.deleteBySubtitleId(subtitleId)

            remapResult?.let { result ->
                val timestamp = System.currentTimeMillis()

                result.highlights.filter { it.note != null }.forEach { highlight ->
                    highlightNoteDao.upsert(
                        HighlightNoteEntity(
                            subtitleId = subtitleId,
                            highlightStart = highlight.start,
                            highlightEnd = highlight.end,
                            noteText = highlight.note!!,
                            createdAt = timestamp,
                            updatedAt = timestamp
                        )
                    )
                }

                result.bookmarks.forEach { bookmark ->
                    bookmarkDao.upsert(
                        BookmarkEntity(
                            id = bookmark.id,
                            subtitleId = subtitleId,
                            anchorStart = bookmark.anchorStart,
                            title = bookmark.title,
                            createdAt = bookmark.createdAt,
                            updatedAt = timestamp
                        )
                    )
                }
            }
        }
    }

    fun saveBookmark(anchorStart: Int, title: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) return
        val state = _uiState.value
        val contentLength = state.content.length
        if (contentLength == 0) return

        val boundedAnchor = anchorStart.coerceIn(0, contentLength - 1)
        val timestamp = System.currentTimeMillis()
        val updatedBookmark = BookmarkEntity(
            subtitleId = subtitleId,
            anchorStart = boundedAnchor,
            title = normalizedTitle,
            createdAt = state.bookmarks.firstOrNull { it.anchorStart == boundedAnchor }?.createdAt ?: timestamp,
            updatedAt = timestamp
        )

        _uiState.update { current ->
            current.copy(
                bookmarks = current.bookmarks
                    .filterNot { it.anchorStart == boundedAnchor }
                    .plus(updatedBookmark)
                    .sortedBy { it.anchorStart }
            )
        }

        viewModelScope.launch {
            bookmarkDao.upsert(updatedBookmark)
        }
    }

    fun deleteBookmark(anchorStart: Int) {
        val state = _uiState.value
        val contentLength = state.content.length
        if (contentLength == 0) return

        val boundedAnchor = anchorStart.coerceIn(0, contentLength - 1)
        val updatedBookmarks = state.bookmarks.filterNot { it.anchorStart == boundedAnchor }
        if (updatedBookmarks.size == state.bookmarks.size) return

        _uiState.update { it.copy(bookmarks = updatedBookmarks) }

        viewModelScope.launch {
            bookmarkDao.deleteByAnchor(subtitleId = subtitleId, anchorStart = boundedAnchor)
        }
    }

    fun applyHighlight(start: Int, end: Int, color: HighlightColor): TextHighlight? {
        return applyHighlightInternal(start = start, end = end, color = color, note = null)
    }

    fun applyHighlightWithNote(
        start: Int,
        end: Int,
        color: HighlightColor,
        note: String
    ): TextHighlight? {
        return applyHighlightInternal(start = start, end = end, color = color, note = note)
    }

    fun saveHighlightNote(target: TextHighlight, note: String) {
        val normalizedNote = normalizeHighlightNote(note)
        val state = _uiState.value
        val updated = state.highlights.map { highlight ->
            if (highlight == target) {
                highlight.copy(note = normalizedNote)
            } else {
                highlight
            }
        }
        if (updated == state.highlights) return
        _uiState.update { it.copy(highlights = updated) }

        viewModelScope.launch {
            persistNoteForHighlight(
                highlight = target.copy(note = normalizedNote),
                timestamp = System.currentTimeMillis()
            )
        }
    }

    fun deleteHighlightNote(target: TextHighlight) {
        val state = _uiState.value
        val updated = state.highlights.map { highlight ->
            if (highlight == target) {
                highlight.copy(note = null)
            } else {
                highlight
            }
        }
        if (updated == state.highlights) return
        _uiState.update { it.copy(highlights = updated) }

        viewModelScope.launch {
            highlightNoteDao.deleteByRange(subtitleId, target.start, target.end)
        }
    }

    private fun applyHighlightInternal(
        start: Int,
        end: Int,
        color: HighlightColor,
        note: String?
    ): TextHighlight? {
        val state = _uiState.value
        val contentLength = state.content.length
        if (contentLength == 0) return null

        val boundedStart = start.coerceIn(0, contentLength)
        val boundedEnd = end.coerceIn(0, contentLength)
        val normalizedStart = minOf(boundedStart, boundedEnd)
        val normalizedEnd = maxOf(boundedStart, boundedEnd)
        if (normalizedStart == normalizedEnd) return null

        val mergeResult = mergeHighlight(
            current = state.highlights,
            start = normalizedStart,
            end = normalizedEnd,
            color = color,
            note = note
        ) ?: return null
        val serialized = serializeHighlights(mergeResult.highlights)

        _uiState.update {
            it.copy(
                highlights = mergeResult.highlights,
                subtitle = it.subtitle?.copy(highlights = serialized)
            )
        }

        viewModelScope.launch {
            subtitleDao.updateHighlights(subtitleId = subtitleId, highlights = serialized)
            persistMergedHighlightNotes(mergeResult)
        }

        return mergeResult.mergedHighlight
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
        viewModelScope.launch {
            highlightNoteDao.deleteByRange(subtitleId, target.start, target.end)
        }
    }

    suspend fun enqueueAiCleaning(inputText: String): Result<Unit> {
        val endpoint = userPreferencesRepository.getAiEndpoint().trim()
        val key = userPreferencesRepository.getAiApiKey().trim()
        val model = userPreferencesRepository.getAiModel().trim()
        if (endpoint.isBlank() || key.isBlank() || model.isBlank()) {
            return Result.failure(
                IllegalStateException(appContext.getString(R.string.ai_cleaning_missing_settings))
            )
        }
        if (_uiState.value.isAiCleaning) {
            return Result.failure(
                IllegalStateException(appContext.getString(R.string.ai_cleaning_already_running))
            )
        }

        return try {
            subtitleDao.markAiCleaningQueued(subtitleId)
            AiCleaningWorkScheduler.enqueue(appContext, subtitleId)
            Result.success(Unit)
        } catch (error: Exception) {
            subtitleDao.storeAiCleaningFailure(
                subtitleId = subtitleId,
                summary = error.message?.takeIf { it.isNotBlank() }
                    ?: appContext.getString(R.string.ai_cleaning_start_failed),
                log = error.stackTraceToString(),
                updatedAt = System.currentTimeMillis()
            )
            Result.failure(error)
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
            subtitleDao.updateHighlights(subtitleId = subtitleId, highlights = serialized)
        }
    }

    private suspend fun persistMergedHighlightNotes(result: HighlightMergeResult) {
        val timestamp = System.currentTimeMillis()
        result.replacedHighlights.forEach { highlight ->
            highlightNoteDao.deleteByRange(subtitleId, highlight.start, highlight.end)
        }
        persistNoteForHighlight(result.mergedHighlight, timestamp)
    }

    private suspend fun persistNoteForHighlight(highlight: TextHighlight, timestamp: Long) {
        val normalizedNote = normalizeHighlightNote(highlight.note)
        if (normalizedNote == null) {
            highlightNoteDao.deleteByRange(subtitleId, highlight.start, highlight.end)
            return
        }
        highlightNoteDao.upsert(
            HighlightNoteEntity(
                subtitleId = subtitleId,
                highlightStart = highlight.start,
                highlightEnd = highlight.end,
                noteText = normalizedNote,
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )
    }

    fun clearPendingAiCleaningResult() {
        viewModelScope.launch {
            subtitleDao.clearAiCleaningResult(subtitleId)
        }
    }

    fun clearAiCleaningError() {
        viewModelScope.launch {
            subtitleDao.clearAiCleaningError(subtitleId)
        }
    }

    companion object {
        fun provideFactory(
            appContext: Context,
            dao: SubtitleDao,
            highlightNoteDao: HighlightNoteDao,
            bookmarkDao: BookmarkDao,
            userPreferencesRepository: UserPreferencesRepository,
            subtitleId: Long
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(
                    appContext,
                    dao,
                    highlightNoteDao,
                    bookmarkDao,
                    userPreferencesRepository,
                    subtitleId
                ) as T
            }
        }
    }
}

private data class HighlightRangeKey(
    val start: Int,
    val end: Int
)
