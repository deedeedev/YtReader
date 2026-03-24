package com.deedeedev.ytreader.ui.home

import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.local.SubtitleEntity

enum class LibraryVisibilityFilter {
    ALL, NOT_IN_COLLECTIONS, IN_COLLECTIONS
}

data class LibraryItem(
    val videoId: String,
    val videoUrl: String,
    val title: String,
    val channelName: String,
    val subtitles: List<SubtitleEntity>,
    val uploadDate: Long,
    val lastDownloaded: Long,
    val lastOpenedAt: Long,
    val isInLibrary: Boolean,
    val collectionCount: Int
)

val LibraryItem.isInCollections: Boolean
    get() = collectionCount > 0

internal fun collectionCountsByVideoId(collections: List<VideoCollection>): Map<String, Int> {
    val counts = LinkedHashMap<String, Int>()
    collections.forEach { collection ->
        collection.videoIds.forEach { videoId ->
            counts[videoId] = (counts[videoId] ?: 0) + 1
        }
    }
    return counts
}

internal fun List<LibraryItem>.filterByVisibility(
    visibilityFilter: LibraryVisibilityFilter
): List<LibraryItem> = when (visibilityFilter) {
    LibraryVisibilityFilter.ALL -> this
    LibraryVisibilityFilter.NOT_IN_COLLECTIONS -> filterNot { it.isInCollections }
    LibraryVisibilityFilter.IN_COLLECTIONS -> filter { it.isInCollections }
}
