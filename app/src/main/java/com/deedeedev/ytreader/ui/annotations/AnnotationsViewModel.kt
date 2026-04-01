package com.deedeedev.ytreader.ui.annotations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.HighlightNoteEntity
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleParser
import com.deedeedev.ytreader.ui.reader.HighlightColor
import com.deedeedev.ytreader.ui.reader.ReaderAnnotationTarget
import com.deedeedev.ytreader.ui.reader.TextHighlight
import com.deedeedev.ytreader.ui.reader.deleteHighlightFromList
import com.deedeedev.ytreader.ui.reader.normalizeHighlightNote
import com.deedeedev.ytreader.ui.reader.parseHighlights
import com.deedeedev.ytreader.ui.reader.serializeHighlights
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AnnotationType { BOOKMARK, HIGHLIGHT, NOTE }

enum class AnnotationSortOption { NEWEST, OLDEST, VIDEO_TITLE }

data class AnnotationItem(
    val key: String,
    val type: AnnotationType,
    val title: String,
    val noteText: String? = null,
    val color: HighlightColor? = null,
    val videoTitle: String,
    val channelName: String,
    val videoId: String,
    val subtitleId: Long,
    val createdAt: Long,
    val progressPercent: Int,
    val navigationTarget: ReaderAnnotationTarget,
    val action: AnnotationAction
)

data class AnnotationGroup(
    val videoId: String,
    val videoTitle: String,
    val channelName: String,
    val items: List<AnnotationItem>
)

data class AnnotationCounts(
    val bookmarks: Int,
    val highlights: Int,
    val notes: Int
) {
    val total get() = bookmarks + highlights + notes
}

data class AnnotationsUiState(
    val isLoading: Boolean = true,
    val typeFilter: Set<AnnotationType> = AnnotationType.entries.toSet(),
    val sortOption: AnnotationSortOption = AnnotationSortOption.NEWEST,
    val groupByVideo: Boolean = true,
    val searchQuery: String = "",
    val isSearchExpanded: Boolean = false
)

sealed interface AnnotationAction {
    data class Bookmark(val bookmark: BookmarkEntity) : AnnotationAction

    data class Highlight(
        val subtitleId: Long,
        val highlight: TextHighlight
    ) : AnnotationAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class AnnotationsViewModel(
    private val subtitleDao: SubtitleDao,
    private val highlightNoteDao: HighlightNoteDao,
    private val bookmarkDao: BookmarkDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnnotationsUiState())
    val uiState: StateFlow<AnnotationsUiState> = _uiState.asStateFlow()

    private var subtitlesById: Map<Long, SubtitleEntity> = emptyMap()

    private val _allItems = MutableStateFlow<List<AnnotationItem>>(emptyList())
    val allItems: StateFlow<List<AnnotationItem>> = _allItems.asStateFlow()

    val filteredItems: StateFlow<List<AnnotationItem>> = combineUiStateAndItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groupedItems: StateFlow<List<AnnotationGroup>> = filteredItems
        .map { items ->
            if (_uiState.value.groupByVideo) groupByVideo(items) else emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val counts: StateFlow<AnnotationCounts> = allItems
        .map { items ->
            AnnotationCounts(
                bookmarks = items.count { it.type == AnnotationType.BOOKMARK },
                highlights = items.count { it.type == AnnotationType.HIGHLIGHT },
                notes = items.count { it.type == AnnotationType.NOTE }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnnotationCounts(0, 0, 0))

    init {
        loadAnnotations()
    }

    private fun combineUiStateAndItems() = _allItems.combineWithState()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun MutableStateFlow<List<AnnotationItem>>.combineWithState() =
        kotlinx.coroutines.flow.combine(
            this,
            _uiState
        ) { items, state ->
            applyFilters(items, state.typeFilter, state.sortOption, state.searchQuery)
        }

    private fun loadAnnotations() {
        viewModelScope.launch {
            subtitleDao.observeAllAccessibleSubtitles()
                .flatMapLatest { subtitles ->
                    if (subtitles.isEmpty()) {
                        flowOf(AnnotationsPayload(emptyList(), emptyList(), emptyList()))
                    } else {
                        val subtitleIds = subtitles.map { it.id }
                        highlightNoteDao.observeBySubtitleIds(subtitleIds)
                            .flatMapLatest { notes ->
                                bookmarkDao.observeBySubtitleIds(subtitleIds)
                                    .map { bookmarks ->
                                        AnnotationsPayload(subtitles, notes, bookmarks)
                                    }
                            }
                    }
                }
                .collectLatest { payload ->
                    subtitlesById = payload.subtitles.associateBy { it.id }
                    val items = buildAnnotationItems(
                        subtitles = payload.subtitles,
                        notes = payload.notes,
                        bookmarks = payload.bookmarks
                    )
                    _allItems.value = items
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    fun toggleTypeFilter(type: AnnotationType) {
        _uiState.update { state ->
            val filter = state.typeFilter.toMutableSet()
            if (!filter.add(type)) filter.remove(type)
            state.copy(typeFilter = filter)
        }
    }

    fun setSortOption(option: AnnotationSortOption) {
        _uiState.update { it.copy(sortOption = option) }
    }

    fun toggleGroupByVideo() {
        _uiState.update { it.copy(groupByVideo = !it.groupByVideo) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearch() {
        _uiState.update {
            it.copy(
                isSearchExpanded = !it.isSearchExpanded,
                searchQuery = if (it.isSearchExpanded) "" else it.searchQuery
            )
        }
    }

    fun deleteAnnotation(item: AnnotationItem) {
        viewModelScope.launch {
            when (val action = item.action) {
                is AnnotationAction.Bookmark -> {
                    bookmarkDao.deleteByAnchor(
                        subtitleId = action.bookmark.subtitleId,
                        anchorStart = action.bookmark.anchorStart
                    )
                }
                is AnnotationAction.Highlight -> {
                    val subtitle = subtitlesById[action.subtitleId] ?: return@launch
                    val updatedHighlights = deleteHighlightFromList(
                        highlights = parseHighlights(subtitle.highlights),
                        target = action.highlight.copy(note = null)
                    )
                    subtitleDao.updateHighlights(
                        subtitleId = action.subtitleId,
                        highlights = serializeHighlights(updatedHighlights)
                    )
                    highlightNoteDao.deleteByRange(
                        subtitleId = action.subtitleId,
                        highlightStart = action.highlight.start,
                        highlightEnd = action.highlight.end
                    )
                }
            }
        }
    }

    fun restoreAnnotation(item: AnnotationItem) {
        viewModelScope.launch {
            when (val action = item.action) {
                is AnnotationAction.Bookmark -> {
                    bookmarkDao.upsert(action.bookmark)
                }
                is AnnotationAction.Highlight -> {
                    val subtitle = subtitlesById[action.subtitleId] ?: return@launch
                    val current = parseHighlights(subtitle.highlights)
                    val restored = if (current.any { h ->
                            h.start == action.highlight.start &&
                                h.end == action.highlight.end &&
                                h.color == action.highlight.color
                        }) {
                        current
                    } else {
                        (current + action.highlight.copy(note = null)).sortedBy { it.start }
                    }
                    subtitleDao.updateHighlights(
                        subtitleId = action.subtitleId,
                        highlights = serializeHighlights(restored)
                    )
                    normalizeHighlightNote(action.highlight.note)?.let { noteText ->
                        val timestamp = System.currentTimeMillis()
                        highlightNoteDao.upsert(
                            HighlightNoteEntity(
                                subtitleId = action.subtitleId,
                                highlightStart = action.highlight.start,
                                highlightEnd = action.highlight.end,
                                noteText = noteText,
                                createdAt = timestamp,
                                updatedAt = timestamp
                            )
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            subtitleDao: SubtitleDao,
            highlightNoteDao: HighlightNoteDao,
            bookmarkDao: BookmarkDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AnnotationsViewModel(subtitleDao, highlightNoteDao, bookmarkDao) as T
            }
        }
    }
}

private data class AnnotationsPayload(
    val subtitles: List<SubtitleEntity>,
    val notes: List<HighlightNoteEntity>,
    val bookmarks: List<BookmarkEntity>
)

private data class HighlightNoteKey(
    val subtitleId: Long,
    val start: Int,
    val end: Int
)

private fun buildAnnotationItems(
    subtitles: List<SubtitleEntity>,
    notes: List<HighlightNoteEntity>,
    bookmarks: List<BookmarkEntity>
): List<AnnotationItem> {
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

    val subtitleInfoById = subtitles.associate { subtitle ->
        subtitle.id to SubtitleInfo(subtitle.videoId, subtitle.title, subtitle.channelName)
    }

    val bookmarkItems = bookmarks.mapNotNull { bookmark ->
        val text = subtitleTextById[bookmark.subtitleId] ?: return@mapNotNull null
        if (text.isEmpty()) return@mapNotNull null
        val info = subtitleInfoById[bookmark.subtitleId] ?: return@mapNotNull null
        val anchorStart = bookmark.anchorStart.coerceIn(0, text.lastIndex)
        val textLength = text.length.coerceAtLeast(1)
        val progressRatio = anchorStart.toFloat() / textLength.toFloat()
        AnnotationItem(
            key = "bookmark:${bookmark.subtitleId}:${bookmark.anchorStart}",
            type = AnnotationType.BOOKMARK,
            title = bookmark.title.trim().ifBlank { lineTextAtOffset(text, anchorStart) },
            videoTitle = info.videoTitle,
            channelName = info.channelName,
            videoId = info.videoId,
            subtitleId = bookmark.subtitleId,
            createdAt = bookmark.createdAt,
            progressPercent = (progressRatio * 100f).toInt().coerceIn(0, 100),
            navigationTarget = ReaderAnnotationTarget(
                subtitleId = bookmark.subtitleId,
                bookmarkStart = anchorStart
            ),
            action = AnnotationAction.Bookmark(bookmark)
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
                    AnnotationType.NOTE
                } else {
                    AnnotationType.HIGHLIGHT
                }
                val updatedAt = note?.updatedAt
                    ?: subtitle.lastOpenedAt.takeIf { it > 0L }
                    ?: subtitle.createdAt
                val progressRatio = start.toFloat() / textLength.toFloat()
                AnnotationItem(
                    key = "highlight:${subtitle.id}:$start:$end:${highlight.color.name}",
                    type = annotationType,
                    title = baseText.substring(start, end)
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .ifBlank { baseText.substring(start, end) },
                    noteText = note?.noteText,
                    color = highlight.color,
                    videoTitle = subtitle.title,
                    channelName = subtitle.channelName,
                    videoId = subtitle.videoId,
                    subtitleId = subtitle.id,
                    createdAt = updatedAt,
                    progressPercent = (progressRatio * 100f).toInt().coerceIn(0, 100),
                    navigationTarget = ReaderAnnotationTarget(
                        subtitleId = subtitle.id,
                        highlightStart = start,
                        highlightEnd = end
                    ),
                    action = AnnotationAction.Highlight(
                        subtitleId = subtitle.id,
                        highlight = TextHighlight(
                            start = start,
                            end = end,
                            color = highlight.color,
                            note = note?.noteText
                        )
                    )
                )
            }
        }
    }

    return bookmarkItems + highlightItems
}

private data class SubtitleInfo(
    val videoId: String,
    val videoTitle: String,
    val channelName: String
)

private fun lineTextAtOffset(text: String, offset: Int): String {
    if (text.isEmpty()) return ""
    val safeOffset = offset.coerceIn(0, text.lastIndex)
    val lineStart = text.lastIndexOf('\n', startIndex = safeOffset)
        .let { if (it == -1) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', startIndex = safeOffset)
        .let { if (it == -1) text.length else it }
    return text.substring(lineStart, lineEnd)
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun applyFilters(
    items: List<AnnotationItem>,
    typeFilter: Set<AnnotationType>,
    sortOption: AnnotationSortOption,
    searchQuery: String
): List<AnnotationItem> {
    var result = items
    if (typeFilter.isNotEmpty() && typeFilter.size < AnnotationType.entries.size) {
        result = result.filter { it.type in typeFilter }
    }
    result = result.applySearch(searchQuery)
    result = when (sortOption) {
        AnnotationSortOption.NEWEST -> result.sortedByDescending { it.createdAt }
        AnnotationSortOption.OLDEST -> result.sortedBy { it.createdAt }
        AnnotationSortOption.VIDEO_TITLE -> result.sortedWith(
            compareBy({ it.videoTitle.lowercase() }, { it.progressPercent })
        )
    }
    return result
}

private fun List<AnnotationItem>.applySearch(query: String): List<AnnotationItem> {
    if (query.isBlank()) return this
    val q = query.lowercase()
    return filter { item ->
        item.title.lowercase().contains(q) ||
            item.noteText?.lowercase()?.contains(q) == true ||
            item.videoTitle.lowercase().contains(q) ||
            item.channelName.lowercase().contains(q)
    }
}

private fun groupByVideo(items: List<AnnotationItem>): List<AnnotationGroup> {
    return items
        .groupBy { it.videoId }
        .map { (videoId, videoItems) ->
            AnnotationGroup(
                videoId = videoId,
                videoTitle = videoItems.first().videoTitle,
                channelName = videoItems.first().channelName,
                items = videoItems
            )
        }
        .sortedBy { group -> group.videoTitle.lowercase() }
}
