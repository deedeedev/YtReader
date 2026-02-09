package com.deedeedev.ytreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subtitles")
data class SubtitleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String,
    val title: String,
    val channelName: String = "", // Default for migration, though we'll likely clear DB
    val languageCode: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
