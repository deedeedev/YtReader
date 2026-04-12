package com.deedeedev.ytreader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_cleaning_states",
    foreignKeys = [ForeignKey(
        entity = SubtitleEntity::class,
        parentColumns = ["id"],
        childColumns = ["subtitleId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class AiCleaningStateEntity(
    @PrimaryKey val subtitleId: Long,
    val aiCleaningInProgress: Boolean = false,
    val aiCleaningSourceText: String? = null,
    val aiCleaningPendingResult: String? = null,
    val aiCleaningErrorSummary: String? = null,
    val aiCleaningErrorLog: String? = null,
    val aiCleaningUpdatedAt: Long = 0L
)