package com.deedeedev.ytreader.ui.home

import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.local.SubtitleEntity

enum class LibraryVisibilityFilter {
    ALL, NOT_IN_COLLECTIONS, IN_COLLECTIONS
}

enum class ReadStatusFilter {
    ALL, READ, NOT_READ
}

enum class SortOption {
    TITLE, CHANNEL_NAME, DATE_PUBLISHED, DOWNLOADED, LAST_OPENED
}

data class CollectionFilterState(
    val selectedChannelFilter: String? = null,
    val readStatusFilter: ReadStatusFilter = ReadStatusFilter.ALL,
    val sortOption: SortOption = SortOption.DOWNLOADED,
    val isAscending: Boolean = false
)

data class LibraryItem(
    val videoId: String,
    val videoUrl: String,
    val title: String,
    val channelName: String,
    val thumbnailLocalPath: String?,
    val subtitles: List<SubtitleEntity>,
    val uploadDate: Long,
    val lastDownloaded: Long,
    val lastOpenedAt: Long,
    val readingProgressPercent: Int,
    val currentPage: Int,
    val totalPages: Int,
    val isInLibrary: Boolean,
    val collectionCount: Int
)

val LibraryItem.isInCollections: Boolean
    get() = collectionCount > 0

val LibraryItem.isRead: Boolean
    get() = readingProgressPercent >= 100

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

internal fun List<LibraryItem>.filterByReadStatus(
    readStatusFilter: ReadStatusFilter
): List<LibraryItem> = when (readStatusFilter) {
    ReadStatusFilter.ALL -> this
    ReadStatusFilter.READ -> filter { it.isRead }
    ReadStatusFilter.NOT_READ -> filterNot { it.isRead }
}

internal fun List<LibraryItem>.filterByTitle(query: String): List<LibraryItem> {
    if (query.isBlank()) return this
    val lower = query.lowercase()
    return filter { it.title.lowercase().contains(lower) }
}
