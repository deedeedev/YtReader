package com.deedeedev.ytreader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subtitle_reading_states",
    foreignKeys = [ForeignKey(
        entity = SubtitleEntity::class,
        parentColumns = ["id"],
        childColumns = ["subtitleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["lastOpenedAt"])]
)
data class SubtitleReadingStateEntity(
    @PrimaryKey val subtitleId: Long,
    val lastTimestamp: Long = 0L,
    val lastOpenedAt: Long = 0L,
    val readingProgressPercent: Int = 0,
    val isRead: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val lastStudyScroll: Int = 0,
    val lastProgressRatio: Float = 0f
)