package com.deedeedev.ytreader.data.local

data class LibraryVideoRow(
    val videoId: String,
    val videoUrl: String,
    val title: String,
    val channelName: String,
    val uploadDate: Long,
    val thumbnailLocalPath: String?,
    val lastDownloaded: Long,
    val lastOpenedAt: Long,
    val readingProgressPercent: Int,
    val currentPage: Int,
    val totalPages: Int,
    val isInLibrary: Boolean
)
