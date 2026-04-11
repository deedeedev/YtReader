package com.deedeedev.ytreader.data

import com.deedeedev.ytreader.data.local.HighlightNoteEntity
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
        subtitleRepository: SubtitleRepository,
        noteRepository: NoteRepository
    ) {
        when (action) {
            is DeletableAnnotationAction.Bookmark -> {
                noteRepository.deleteBookmarkByAnchor(
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
                subtitleRepository.updateHighlights(
                    subtitleId = action.subtitleId,
                    highlights = serializeHighlights(updatedHighlights)
                )
                noteRepository.deleteHighlightByRange(
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
        subtitleRepository: SubtitleRepository,
        noteRepository: NoteRepository
    ) {
        when (action) {
            is DeletableAnnotationAction.Bookmark -> {
                noteRepository.upsertBookmark(action.bookmark)
            }
            is DeletableAnnotationAction.Highlight -> {
                val subtitle = subtitlesById[action.subtitleId] ?: return
                val current = parseHighlights(subtitle.highlights)
                val restored = restoreHighlight(current, action.highlight.copy(note = null))
                subtitleRepository.updateHighlights(
                    subtitleId = action.subtitleId,
                    highlights = serializeHighlights(restored)
                )
                normalizeHighlightNote(action.highlight.note)?.let { noteText ->
                    val timestamp = System.currentTimeMillis()
                    noteRepository.upsertHighlight(
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
