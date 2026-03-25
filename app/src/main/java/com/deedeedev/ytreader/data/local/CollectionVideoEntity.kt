package com.deedeedev.ytreader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "collection_videos",
    primaryKeys = ["collectionId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["collectionId"]),
        Index(value = ["videoId"])
    ]
)
data class CollectionVideoEntity(
    val collectionId: String,
    val videoId: String,
    val addedAt: Long = System.currentTimeMillis()
)
