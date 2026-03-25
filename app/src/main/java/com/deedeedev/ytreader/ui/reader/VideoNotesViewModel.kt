package com.deedeedev.ytreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.HighlightNoteEntity
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoNotesUiState(
    val videoId: String = "",
    val title: String = "",
    val items: List<VideoNoteItem> = emptyList(),
    val totalHighlights: Int = 0,
    val totalNotes: Int = 0,
    val isLoading: Boolean = true
)

data class VideoNoteItem(
    val key: String,
    val subtitleId: Long,
    val highlightStart: Int,
    val highlightEnd: Int,
    val highlightedText: String,
    val note: String?,
    val color: HighlightColor,
    val updatedAt: Long,
    val progressPercent: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
class VideoNotesViewModel(
    private val subtitleDao: SubtitleDao,
    private val highlightNoteDao: HighlightNoteDao,
    private val videoId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoNotesUiState(videoId = videoId))
    val uiState: StateFlow<VideoNotesUiState> = _uiState.asStateFlow()

    init {
        loadNotes()
    }

    private fun loadNotes() {
        viewModelScope.launch {
            subtitleDao.observeByVideoId(videoId)
                .flatMapLatest { subtitles ->
                    if (subtitles.isEmpty()) {
                        flowOf(VideoNotesPayload(emptyList(), emptyList()))
                    } else {
                        highlightNoteDao.observeBySubtitleIds(subtitles.map { it.id })
                            .map { notes -> VideoNotesPayload(subtitles, notes) }
                    }
                }
                .collectLatest { payload ->
                    val items = buildVideoNoteItems(payload.subtitles, payload.notes)
                    _uiState.update {
                        it.copy(
                            title = payload.subtitles.firstOrNull()?.title.orEmpty(),
                            items = items,
                            totalHighlights = items.size,
                            totalNotes = items.count { item -> item.note != null },
                            isLoading = false
                        )
                    }
                }
        }
    }

    companion object {
        fun provideFactory(
            subtitleDao: SubtitleDao,
            highlightNoteDao: HighlightNoteDao,
            videoId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VideoNotesViewModel(
                    subtitleDao = subtitleDao,
                    highlightNoteDao = highlightNoteDao,
                    videoId = videoId
                ) as T
            }
        }
    }
}

private data class VideoNotesPayload(
    val subtitles: List<SubtitleEntity>,
    val notes: List<HighlightNoteEntity>
)

private data class HighlightNoteKey(
    val subtitleId: Long,
    val start: Int,
    val end: Int
)

private fun buildVideoNoteItems(
    subtitles: List<SubtitleEntity>,
    notes: List<HighlightNoteEntity>
): List<VideoNoteItem> {
    val notesByKey = notes.associateBy { note ->
        HighlightNoteKey(
            subtitleId = note.subtitleId,
            start = note.highlightStart,
            end = note.highlightEnd
        )
    }

    return subtitles.flatMap { subtitle ->
        val baseText = subtitle.studyContent ?: SubtitleParser.parse(subtitle.content)
        val textLength = baseText.length.coerceAtLeast(1)

        parseHighlights(subtitle.highlights).mapNotNull { highlight ->
            val start = highlight.start.coerceIn(0, baseText.length)
            val end = highlight.end.coerceIn(0, baseText.length)
            if (end <= start) {
                null
            } else {
                val note = notesByKey[
                    HighlightNoteKey(
                        subtitleId = subtitle.id,
                        start = start,
                        end = end
                    )
                ]
                val highlightedText = baseText.substring(start, end)
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .ifBlank { baseText.substring(start, end) }
                val updatedAt = note?.updatedAt
                    ?: subtitle.lastOpenedAt.takeIf { it > 0L }
                    ?: subtitle.createdAt
                VideoNoteItem(
                    key = "${subtitle.id}:$start:$end:${highlight.color.name}",
                    subtitleId = subtitle.id,
                    highlightStart = start,
                    highlightEnd = end,
                    highlightedText = highlightedText,
                    note = note?.noteText,
                    color = highlight.color,
                    updatedAt = updatedAt,
                    progressPercent = ((start.toFloat() / textLength.toFloat()) * 100f).toInt().coerceIn(0, 100)
                )
            }
        }
    }.sortedWith(
        compareByDescending<VideoNoteItem> { item -> item.updatedAt }
            .thenBy { item -> item.highlightedText }
    )
}
