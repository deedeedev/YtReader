package com.deedeedev.ytreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey
    val videoId: String,
    val videoUrl: String = "",
    val title: String = "",
    val channelName: String = "",
    val uploadDate: Long = 0L,
    val thumbnailLocalPath: String? = null,
    val thumbnailSourceUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
