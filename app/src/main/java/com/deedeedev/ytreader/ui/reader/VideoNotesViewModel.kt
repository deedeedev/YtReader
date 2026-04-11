package com.deedeedev.ytreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.AnnotationOperations
import com.deedeedev.ytreader.data.DeletableAnnotationAction
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.HighlightNoteEntity
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleParser
import com.deedeedev.ytreader.domain.lineTextAtOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VideoAnnotationType {
    BOOKMARK,
    NOTE,
    HIGHLIGHT
}

data class VideoNotesUiState(
    val videoId: String = "",
    val title: String = "",
    val channelName: String = "",
    val uploadDate: Long = 0L,
    val items: List<VideoAnnotationItem> = emptyList(),
    val totalBookmarks: Int = 0,
    val totalHighlights: Int = 0,
    val totalNotes: Int = 0,
    val isLoading: Boolean = true
)

data class VideoAnnotationItem(
    val key: String,
    val type: VideoAnnotationType,
    val navigationTarget: ReaderAnnotationTarget,
    val title: String,
    val note: String? = null,
    val color: HighlightColor? = null,
    val updatedAt: Long,
    val progressPercent: Int,
    val action: VideoAnnotationAction
)

sealed interface VideoAnnotationAction {
    fun toDeletableAction(): DeletableAnnotationAction

    data class Bookmark(
        val bookmark: BookmarkEntity
    ) : VideoAnnotationAction {
        override fun toDeletableAction() = DeletableAnnotationAction.Bookmark(bookmark)
    }

    data class Highlight(
        val subtitleId: Long,
        val highlight: TextHighlight
    ) : VideoAnnotationAction {
        override fun toDeletableAction() = DeletableAnnotationAction.Highlight(subtitleId, highlight)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VideoNotesViewModel(
    private val subtitleDao: SubtitleDao,
    private val highlightNoteDao: HighlightNoteDao,
    private val bookmarkDao: BookmarkDao,
    private val videoId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoNotesUiState(videoId = videoId))
    val uiState: StateFlow<VideoNotesUiState> = _uiState.asStateFlow()
    private var subtitlesById: Map<Long, SubtitleEntity> = emptyMap()

    init {
        loadAnnotations()
    }

    private fun loadAnnotations() {
        viewModelScope.launch {
            subtitleDao.observeByVideoId(videoId)
                .flatMapLatest { subtitles ->
                    if (subtitles.isEmpty()) {
                        flowOf(VideoNotesPayload(emptyList(), emptyList(), emptyList()))
                    } else {
                        val subtitleIds = subtitles.map { it.id }
                        highlightNoteDao.observeBySubtitleIds(subtitleIds)
                            .flatMapLatest { notes ->
                                bookmarkDao.observeBySubtitleIds(subtitleIds)
                                    .map { bookmarks ->
                                        VideoNotesPayload(
                                            subtitles = subtitles,
                                            notes = notes,
                                            bookmarks = bookmarks
                                        )
                                    }
                            }
                    }
                }
                .collectLatest { payload ->
                    subtitlesById = payload.subtitles.associateBy { subtitle -> subtitle.id }
                    val firstSubtitle = payload.subtitles.firstOrNull()
                    val items = buildVideoAnnotationItems(
                        subtitles = payload.subtitles,
                        notes = payload.notes,
                        bookmarks = payload.bookmarks
                    )
                    _uiState.update {
                        it.copy(
                            title = firstSubtitle?.title.orEmpty(),
                            channelName = firstSubtitle?.channelName.orEmpty(),
                            uploadDate = firstSubtitle?.uploadDate ?: 0L,
                            items = items,
                            totalBookmarks = items.count { item -> item.type == VideoAnnotationType.BOOKMARK },
                            totalHighlights = items.count { item -> item.type == VideoAnnotationType.HIGHLIGHT },
                            totalNotes = items.count { item -> item.type == VideoAnnotationType.NOTE },
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun deleteAnnotation(item: VideoAnnotationItem) {
        viewModelScope.launch {
            AnnotationOperations.delete(
                action = item.action.toDeletableAction(),
                subtitlesById = subtitlesById,
                subtitleDao = subtitleDao,
                highlightNoteDao = highlightNoteDao,
                bookmarkDao = bookmarkDao
            )
        }
    }

    fun restoreAnnotation(item: VideoAnnotationItem) {
        viewModelScope.launch {
            AnnotationOperations.restore(
                action = item.action.toDeletableAction(),
                subtitlesById = subtitlesById,
                subtitleDao = subtitleDao,
                highlightNoteDao = highlightNoteDao,
                bookmarkDao = bookmarkDao
            )
        }
    }

    companion object {
        fun provideFactory(
            subtitleDao: SubtitleDao,
            highlightNoteDao: HighlightNoteDao,
            bookmarkDao: BookmarkDao,
            videoId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VideoNotesViewModel(
                    subtitleDao = subtitleDao,
                    highlightNoteDao = highlightNoteDao,
                    bookmarkDao = bookmarkDao,
                    videoId = videoId
                ) as T
            }
        }
    }
}

private data class VideoNotesPayload(
    val subtitles: List<SubtitleEntity>,
    val notes: List<HighlightNoteEntity>,
    val bookmarks: List<BookmarkEntity>
)

private data class HighlightNoteKey(
    val subtitleId: Long,
    val start: Int,
    val end: Int
)

internal fun buildVideoAnnotationItems(
    subtitles: List<SubtitleEntity>,
    notes: List<HighlightNoteEntity>,
    bookmarks: List<BookmarkEntity>
): List<VideoAnnotationItem> {
    val notesByKey = notes.associateBy { note ->
        HighlightNoteKey(
            subtitleId = note.subtitleId,
            start = note.highlightStart,
            end = note.highlightEnd
        )
    }

    val subtitleTextById = subtitles.associate { subtitle ->
        subtitle.id to (subtitle.studyContent ?: SubtitleParser.parse(subtitle.content))
    }

    val bookmarkItems = bookmarks.mapNotNull { bookmark ->
        val text = subtitleTextById[bookmark.subtitleId] ?: return@mapNotNull null
        if (text.isEmpty()) return@mapNotNull null
        val anchorStart = bookmark.anchorStart.coerceIn(0, text.lastIndex)
        val textLength = text.length.coerceAtLeast(1)
        val progressRatio = anchorStart.toFloat() / textLength.toFloat()
        SortableVideoAnnotationItem(
            item = VideoAnnotationItem(
                key = "bookmark:${bookmark.subtitleId}:${bookmark.anchorStart}",
                type = VideoAnnotationType.BOOKMARK,
                navigationTarget = ReaderAnnotationTarget(
                    subtitleId = bookmark.subtitleId,
                    bookmarkStart = anchorStart
                ),
                title = bookmark.title.trim().ifBlank { lineTextAtOffset(text, anchorStart) },
                updatedAt = bookmark.updatedAt,
                progressPercent = (progressRatio * 100f)
                    .toInt()
                    .coerceIn(0, 100),
                action = VideoAnnotationAction.Bookmark(bookmark)
            ),
            progressRatio = progressRatio,
            startOffset = anchorStart,
            endOffset = anchorStart,
            updatedAt = bookmark.updatedAt
        )
    }

    val highlightItems = subtitles.flatMap { subtitle ->
        val baseText = subtitleTextById[subtitle.id].orEmpty()
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
                val annotationType = if (note != null) {
                    VideoAnnotationType.NOTE
                } else {
                    VideoAnnotationType.HIGHLIGHT
                }
                val updatedAt = note?.updatedAt
                    ?: subtitle.lastOpenedAt.takeIf { it > 0L }
                    ?: subtitle.createdAt
                val progressRatio = start.toFloat() / textLength.toFloat()
                SortableVideoAnnotationItem(
                    item = VideoAnnotationItem(
                        key = "highlight:${subtitle.id}:$start:$end:${highlight.color.name}",
                        type = annotationType,
                        navigationTarget = ReaderAnnotationTarget(
                            subtitleId = subtitle.id,
                            highlightStart = start,
                            highlightEnd = end
                        ),
                        title = baseText.substring(start, end)
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .ifBlank { baseText.substring(start, end) },
                        note = note?.noteText,
                        color = highlight.color,
                        updatedAt = updatedAt,
                        progressPercent = (progressRatio * 100f)
                            .toInt()
                            .coerceIn(0, 100),
                        action = VideoAnnotationAction.Highlight(
                            subtitleId = subtitle.id,
                            highlight = TextHighlight(
                                start = start,
                                end = end,
                                color = highlight.color,
                                note = note?.noteText
                            )
                        )
                    ),
                    progressRatio = progressRatio,
                    startOffset = start,
                    endOffset = end,
                    updatedAt = updatedAt
                )
            }
        }
    }

    return (bookmarkItems + highlightItems)
        .sortedWith(
            compareBy<SortableVideoAnnotationItem> { item -> item.progressRatio }
                .thenBy { item -> item.startOffset }
                .thenBy { item -> item.endOffset }
                .thenBy { item -> item.item.navigationTarget.subtitleId }
                .thenByDescending { item -> item.updatedAt }
                .thenBy { item -> item.item.title }
        )
        .map { item -> item.item }
}

private data class SortableVideoAnnotationItem(
    val item: VideoAnnotationItem,
    val progressRatio: Float,
    val startOffset: Int,
    val endOffset: Int,
    val updatedAt: Long
)
