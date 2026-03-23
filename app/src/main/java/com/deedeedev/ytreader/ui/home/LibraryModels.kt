package com.deedeedev.ytreader.ui.home

import com.deedeedev.ytreader.data.local.SubtitleEntity

data class LibraryItem(
    val videoId: String,
    val videoUrl: String,
    val title: String,
    val channelName: String,
    val subtitles: List<SubtitleEntity>,
    val uploadDate: Long,
    val lastDownloaded: Long,
    val lastOpenedAt: Long
)
