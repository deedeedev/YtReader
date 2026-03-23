package com.deedeedev.ytreader.data.local

data class LibraryVideoRow(
    val videoId: String,
    val videoUrl: String,
    val title: String,
    val channelName: String,
    val uploadDate: Long,
    val lastDownloaded: Long,
    val lastOpenedAt: Long
)
