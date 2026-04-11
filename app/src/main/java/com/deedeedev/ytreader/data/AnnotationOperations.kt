package com.deedeedev.ytreader.data

import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.HighlightNoteEntity
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.ui.reader.TextHighlight
import com.deedeedev.ytreader.ui.reader.deleteHighlightFromList
import com.deedeedev.ytreader.ui.reader.normalizeHighlightNote
import com.deedeedev.ytreader.ui.reader.parseHighlights
import com.deedeedev.ytreader.ui.reader.serializeHighlights

object AnnotationOperations {

    suspend fun delete(
        action: DeletableAnnotationAction,
        subtitlesById: Map<Long, SubtitleEntity>,
        subtitleDao: SubtitleDao,
        highlightNoteDao: HighlightNoteDao,
        bookmarkDao: BookmarkDao
    ) {
        when (action) {
            is DeletableAnnotationAction.Bookmark -> {
                bookmarkDao.deleteByAnchor(
                    subtitleId = action.bookmark.subtitleId,
                    anchorStart = action.bookmark.anchorStart
                )
            }
            is DeletableAnnotationAction.Highlight -> {
                val subtitle = subtitlesById[action.subtitleId] ?: return
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

    suspend fun restore(
        action: DeletableAnnotationAction,
        subtitlesById: Map<Long, SubtitleEntity>,
        subtitleDao: SubtitleDao,
        highlightNoteDao: HighlightNoteDao,
        bookmarkDao: BookmarkDao
    ) {
        when (action) {
            is DeletableAnnotationAction.Bookmark -> {
                bookmarkDao.upsert(action.bookmark)
            }
            is DeletableAnnotationAction.Highlight -> {
                val subtitle = subtitlesById[action.subtitleId] ?: return
                val current = parseHighlights(subtitle.highlights)
                val restored = restoreHighlight(current, action.highlight.copy(note = null))
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

private fun restoreHighlight(current: List<TextHighlight>, target: TextHighlight): List<TextHighlight> {
    return if (current.any { highlight ->
            highlight.start == target.start &&
                highlight.end == target.end &&
                highlight.color == target.color
        }
    ) {
        current
    } else {
        (current + target.copy(note = null)).sortedBy { highlight -> highlight.start }
    }
}
