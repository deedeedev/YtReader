package com.deedeedev.ytreader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "highlight_notes",
    foreignKeys = [
        ForeignKey(
            entity = SubtitleEntity::class,
            parentColumns = ["id"],
            childColumns = ["subtitleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["subtitleId"]),
        Index(value = ["subtitleId", "highlightStart", "highlightEnd"], unique = true)
    ]
)
data class HighlightNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subtitleId: Long,
    val highlightStart: Int,
    val highlightEnd: Int,
    val noteText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
