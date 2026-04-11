package com.deedeedev.ytreader.data

import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.ui.reader.TextHighlight

sealed interface DeletableAnnotationAction {
    val subtitleId: Long

    data class Bookmark(
        val bookmark: BookmarkEntity
    ) : DeletableAnnotationAction {
        override val subtitleId = bookmark.subtitleId
    }

    data class Highlight(
        override val subtitleId: Long,
        val highlight: TextHighlight
    ) : DeletableAnnotationAction
}
