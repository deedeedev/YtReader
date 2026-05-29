package com.deedeedev.ytreader.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.StringProvider
import com.deedeedev.ytreader.data.AiCleaningWorkScheduler
import com.deedeedev.ytreader.widget.ReaderWidgetProvider
import com.deedeedev.ytreader.data.NoteRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.SubtitleWithStates
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.data.local.HighlightNoteEntity
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val subtitleWithStates: SubtitleWithStates? = null,
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
) {
    val subtitle: SubtitleEntity? get() = subtitleWithStates?.subtitle
    val lastStudyScroll: Int get() = subtitleWithStates?.readingState?.lastStudyScroll ?: 0
    val readingProgressPercent: Int get() = subtitleWithStates?.readingState?.readingProgressPercent ?: 0
    val lastProgressRatio: Float get() = subtitleWithStates?.readingState?.lastProgressRatio ?: 0f
    val lastTimestamp: Long get() = subtitleWithStates?.readingState?.lastTimestamp ?: 0L
    val currentPage: Int get() = subtitleWithStates?.readingState?.currentPage ?: 0
    val totalPages: Int get() = subtitleWithStates?.readingState?.totalPages ?: 0
}

class ReaderViewModel(
    private val stringProvider: StringProvider,
    private val appContext: Context,
    private val subtitleRepository: SubtitleRepository,
    private val noteRepository: NoteRepository,
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
            subtitleRepository.updateLastOpenedAt(subtitleId, System.currentTimeMillis())
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
            subtitleRepository.observeSubtitleWithStates(subtitleId)
                .combine(noteRepository.observeHighlightsBySubtitleId(subtitleId)) { subtitle, notes ->
                    subtitle to notes
                }
                .combine(noteRepository.observeBookmarksBySubtitleId(subtitleId)) { (subtitle, notes), bookmarks ->
                    Triple(subtitle, notes, bookmarks)
                }
                .collectLatest { (subtitleWithStates, notes, bookmarks) ->
                if (subtitleWithStates != null) {
                    val subtitle = subtitleWithStates.subtitle
                    val readingState = subtitleWithStates.readingState
                    val aiState = subtitleWithStates.aiCleaningState
                    val originalParsedText = SubtitleParser.parse(subtitle.content)
                    val content = subtitle.studyContent ?: originalParsedText
                    val notesByRange = notes.associateBy { HighlightRangeKey(it.highlightStart, it.highlightEnd) }
                    val highlights = parseHighlights(subtitle.highlights).map { highlight ->
                        highlight.copy(
                            note = notesByRange[HighlightRangeKey(highlight.start, highlight.end)]?.noteText
                        )
                    }

                    val current = _uiState.value
                    val subtitleChanged = current.subtitleWithStates == null || current.subtitleWithStates.subtitle.content != subtitle.content || current.subtitleWithStates.subtitle.studyContent != subtitle.studyContent || current.subtitleWithStates.subtitle.highlights != subtitle.highlights || current.subtitleWithStates.subtitle.fontSize != subtitle.fontSize || current.subtitleWithStates.subtitle.fontFamily != subtitle.fontFamily || (current.subtitleWithStates.aiCleaningState?.aiCleaningInProgress != aiState?.aiCleaningInProgress) || (current.subtitleWithStates.aiCleaningState?.aiCleaningPendingResult != aiState?.aiCleaningPendingResult) || (current.subtitleWithStates.aiCleaningState?.aiCleaningErrorSummary != aiState?.aiCleaningErrorSummary) || (current.subtitleWithStates.aiCleaningState?.aiCleaningErrorLog != aiState?.aiCleaningErrorLog)
                    val highlightsChanged = current.highlights != highlights
                    val bookmarksChanged = current.bookmarks != bookmarks

                    if (subtitleChanged || highlightsChanged || bookmarksChanged || current.isLoading) {
                        _uiState.update {
                            it.copy(
                                subtitleWithStates = subtitleWithStates,
                                content = content,
                                originalParsedText = originalParsedText,
                                highlights = highlights,
                                bookmarks = bookmarks,
                                fontSize = subtitle.fontSize,
                                fontFamily = subtitle.fontFamily,
                                isAiCleaning = aiState?.aiCleaningInProgress ?: false,
                                pendingAiCleanedText = aiState?.aiCleaningPendingResult,
                                aiCleaningErrorSummary = aiState?.aiCleaningErrorSummary,
                                aiCleaningErrorLog = aiState?.aiCleaningErrorLog,
                                isLoading = false
                            )
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun updateLastTimestamp(timestamp: Long) {
        viewModelScope.launch {
            subtitleRepository.updateLastTimestamp(subtitleId, timestamp)
        }
    }

    fun updateLastStudyScroll(scroll: Int) {
        viewModelScope.launch {
            subtitleRepository.updateLastStudyScroll(subtitleId, scroll.coerceAtLeast(0))
        }
    }

    private var lastSavedRatio: Float = -1f

    fun updateProgressRatio(ratio: Float) {
        if (ratio == lastSavedRatio) return
        lastSavedRatio = ratio
        viewModelScope.launch {
            subtitleRepository.updateProgressRatio(subtitleId, ratio.coerceIn(0f, 1f))
        }
    }

    private data class ProgressState(val percent: Int, val currentPage: Int, val totalPages: Int)

    private val lastSavedProgress = MutableStateFlow<ProgressState?>(null)

    fun updateReadingProgress(percent: Int, currentPage: Int, totalPages: Int) {
        val progress = ProgressState(
            percent.coerceIn(0, 100),
            currentPage.coerceAtLeast(0),
            totalPages.coerceAtLeast(0)
        )
        if (progress == lastSavedProgress.value) return
        lastSavedProgress.value = progress
        viewModelScope.launch {
            subtitleRepository.updateReadingProgress(
                subtitleId = subtitleId,
                percent = progress.percent,
                currentPage = progress.currentPage,
                totalPages = progress.totalPages
            )
        }
    }

    fun updateFontSize(fontSize: Float) {
        _uiState.update { it.copy(fontSize = fontSize) }
        viewModelScope.launch {
            subtitleRepository.updateFontSize(subtitleId, fontSize)
        }
    }

    fun updateFontFamily(fontFamily: String) {
        _uiState.update { it.copy(fontFamily = fontFamily) }
        viewModelScope.launch {
            subtitleRepository.updateFontFamily(subtitleId, fontFamily)
        }
    }

    fun updateContent(content: String) {
        val oldContent = _uiState.value.content
        val oldHighlights = _uiState.value.highlights
        val oldBookmarks = _uiState.value.bookmarks

        val hasAnnotations = oldHighlights.isNotEmpty() || oldBookmarks.isNotEmpty()
        val textChanged = oldContent != content

        if (!textChanged) {
            return
        }

        if (!hasAnnotations) {
            val newHighlights = emptyList<TextHighlight>()
            val newBookmarks = emptyList<BookmarkEntity>()

            _uiState.update { state ->
                state.copy(
                    content = content,
                    highlights = newHighlights,
                    subtitleWithStates = state.subtitleWithStates?.let {
                        it.copy(
                            subtitle = it.subtitle.copy(
                                studyContent = content,
                                highlights = serializeHighlights(newHighlights)
                            )
                        )
                    }
                )
            }

            viewModelScope.launch {
                subtitleRepository.updateStudyContent(subtitleId, content)
                subtitleRepository.updateHighlights(subtitleId = subtitleId, serializeHighlights(newHighlights))
                noteRepository.deleteHighlightsBySubtitleId(subtitleId)
                noteRepository.deleteBookmarksBySubtitleId(subtitleId)
            }
            return
        }

        viewModelScope.launch {
            val oldContentSnapshot = oldContent
            val remapResult = withContext(Dispatchers.Default) {
                remapAnnotations(oldContentSnapshot, content, oldHighlights, oldBookmarks)
            }

            val newHighlights = remapResult.highlights
            val newBookmarks = remapResult.bookmarks

            _uiState.update { state ->
                state.copy(
                    content = content,
                    highlights = newHighlights,
                    subtitleWithStates = state.subtitleWithStates?.let {
                        it.copy(
                            subtitle = it.subtitle.copy(
                                studyContent = content,
                                highlights = serializeHighlights(newHighlights)
                            )
                        )
                    }
                )
            }

            subtitleRepository.updateStudyContent(subtitleId, content)
            subtitleRepository.updateHighlights(subtitleId = subtitleId, serializeHighlights(newHighlights))

            noteRepository.deleteHighlightsBySubtitleId(subtitleId)
            noteRepository.deleteBookmarksBySubtitleId(subtitleId)

            val timestamp = System.currentTimeMillis()

            remapResult.highlights.filter { it.note != null }.forEach { highlight ->
                noteRepository.upsertHighlight(
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

            newBookmarks.forEach { bookmark ->
                noteRepository.upsertBookmark(
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
            noteRepository.upsertBookmark(updatedBookmark)
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
            noteRepository.deleteBookmarkByAnchor(subtitleId = subtitleId, anchorStart = boundedAnchor)
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
            noteRepository.deleteHighlightByRange(subtitleId, target.start, target.end)
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
                subtitleWithStates = it.subtitleWithStates?.let { sws ->
                    sws.copy(subtitle = sws.subtitle.copy(highlights = serialized))
                }
            )
        }

        viewModelScope.launch {
            subtitleRepository.updateHighlights(subtitleId = subtitleId, highlights = serialized)
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
            noteRepository.deleteHighlightByRange(subtitleId, target.start, target.end)
        }
    }

    suspend fun enqueueAiCleaning(inputText: String): Result<Unit> {
        val endpoint = userPreferencesRepository.getAiEndpoint().trim()
        val key = userPreferencesRepository.getAiApiKey().trim()
        val model = userPreferencesRepository.getAiModel().trim()
        if (endpoint.isBlank() || key.isBlank() || model.isBlank()) {
            return Result.failure(
                IllegalStateException(stringProvider.getString(R.string.ai_cleaning_missing_settings))
            )
        }
        if (_uiState.value.isAiCleaning) {
            return Result.failure(
                IllegalStateException(stringProvider.getString(R.string.ai_cleaning_already_running))
            )
        }

        return try {
            subtitleRepository.markAiCleaningQueued(subtitleId, inputText, System.currentTimeMillis())
            AiCleaningWorkScheduler.enqueue(appContext, subtitleId)
            Result.success(Unit)
        } catch (error: Exception) {
            subtitleRepository.storeAiCleaningFailure(
                subtitleId = subtitleId,
                summary = error.message?.takeIf { it.isNotBlank() }
                    ?: stringProvider.getString(R.string.ai_cleaning_start_failed),
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
                subtitleWithStates = it.subtitleWithStates?.let { sws ->
                    sws.copy(subtitle = sws.subtitle.copy(highlights = serialized))
                }
            )
        }
        viewModelScope.launch {
            subtitleRepository.updateHighlights(subtitleId = subtitleId, highlights = serialized)
        }
    }

    private suspend fun persistMergedHighlightNotes(result: HighlightMergeResult) {
        val timestamp = System.currentTimeMillis()
        result.replacedHighlights.forEach { highlight ->
            noteRepository.deleteHighlightByRange(subtitleId, highlight.start, highlight.end)
        }
        persistNoteForHighlight(result.mergedHighlight, timestamp)
    }

    private suspend fun persistNoteForHighlight(highlight: TextHighlight, timestamp: Long) {
        val normalizedNote = normalizeHighlightNote(highlight.note)
        if (normalizedNote == null) {
            noteRepository.deleteHighlightByRange(subtitleId, highlight.start, highlight.end)
            return
        }
        noteRepository.upsertHighlight(
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
            subtitleRepository.clearAiCleaningResult(subtitleId)
        }
    }

    fun clearAiCleaningError() {
        viewModelScope.launch {
            subtitleRepository.clearAiCleaningError(subtitleId)
        }
    }

    companion object {
        fun provideFactory(
            stringProvider: StringProvider,
            appContext: Context,
            subtitleRepository: SubtitleRepository,
            noteRepository: NoteRepository,
            userPreferencesRepository: UserPreferencesRepository,
            subtitleId: Long
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(
                    stringProvider,
                    appContext,
                    subtitleRepository,
                    noteRepository,
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
