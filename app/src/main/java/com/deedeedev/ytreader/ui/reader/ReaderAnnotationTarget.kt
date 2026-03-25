package com.deedeedev.ytreader.ui.reader

data class ReaderAnnotationTarget(
    val subtitleId: Long,
    val highlightStart: Int? = null,
    val highlightEnd: Int? = null,
    val bookmarkStart: Int? = null
)
