package com.deedeedev.ytreader.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class CollectionWithVideos(
    @Embedded val collection: CollectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "collectionId",
        entity = CollectionVideoEntity::class,
        projection = ["videoId"]
    )
    val videoIds: List<String>
)
