package com.deedeedev.ytreader.ui.home

import android.content.Context
import com.deedeedev.ytreader.data.CollectionRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.VideoRepository
import com.deedeedev.ytreader.data.VideoThumbnailStore
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.local.LibraryVideoRow
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.data.local.VideoEntity
import com.deedeedev.ytreader.data.preferredThumbnailUrl
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.schabi.newpipe.extractor.stream.StreamInfo

internal suspend fun deleteVideoIfUnreferenced(
    subtitleRepository: SubtitleRepository,
    videoRepository: VideoRepository,
    collectionRepository: CollectionRepository,
    appContext: Context,
    videoId: String
) {
    val hasLibraryEntry = subtitleRepository.countLibraryEntriesByVideoId(videoId) > 0
    if (!hasLibraryEntry && !collectionRepository.isVideoInAnyCollection(videoId)) {
        videoRepository.getByVideoId(videoId)?.thumbnailLocalPath?.let { path ->
            VideoThumbnailStore.delete(appContext, path)
        }
        videoRepository.deleteByVideoId(videoId)
        subtitleRepository.deleteByVideoId(videoId)
    }
}

internal suspend fun upsertVideoMetadata(
    videoRepository: VideoRepository,
    youtubeRepository: YoutubeRepository,
    appContext: Context,
    videoId: String,
    fallbackVideoUrl: String,
    fallbackTitle: String,
    fallbackChannelName: String,
    fallbackUploadDate: Long,
    info: StreamInfo
) {
    val existingVideo = videoRepository.getByVideoId(videoId)
    val thumbnailSourceUrl = preferredThumbnailUrl(info.thumbnails)
    val localThumbnailPath = downloadThumbnail(youtubeRepository, appContext, videoId, thumbnailSourceUrl)
        ?: existingVideo?.thumbnailLocalPath

    if (thumbnailSourceUrl != null && localThumbnailPath == null) {
        existingVideo?.thumbnailLocalPath?.let { VideoThumbnailStore.delete(appContext, it) }
    }

    videoRepository.upsert(
        VideoEntity(
            videoId = videoId,
            videoUrl = fallbackVideoUrl,
            title = fallbackTitle,
            channelName = fallbackChannelName,
            uploadDate = fallbackUploadDate,
            thumbnailLocalPath = localThumbnailPath,
            thumbnailSourceUrl = thumbnailSourceUrl,
            updatedAt = System.currentTimeMillis()
        )
    )
}

internal suspend fun downloadThumbnail(
    youtubeRepository: YoutubeRepository,
    appContext: Context,
    videoId: String,
    sourceUrl: String?
): String? {
    if (sourceUrl.isNullOrBlank()) {
        return null
    }
    return runCatching {
        val bytes = youtubeRepository.downloadBytes(sourceUrl)
        if (bytes.isEmpty()) {
            null
        } else {
            VideoThumbnailStore.save(appContext, videoId, sourceUrl, bytes)
        }
    }.getOrNull()
}

internal fun displayUrlFor(videoId: String, videoUrl: String): String {
    val savedUrl = videoUrl.trim()
    if (savedUrl.isNotBlank()) {
        return savedUrl
    }
    val normalizedVideoId = YouTubeVideoIdNormalizer.extractVideoId(videoId)
    if (normalizedVideoId != null) {
        return YouTubeVideoIdNormalizer.canonicalWatchUrl(normalizedVideoId)
    }
    return videoId
}

internal fun resolveVideoLookupUrl(subtitle: SubtitleEntity): String {
    val savedUrl = subtitle.videoUrl.trim()
    if (savedUrl.isNotBlank()) {
        return savedUrl
    }
    val normalizedId = YouTubeVideoIdNormalizer.extractVideoId(subtitle.videoId)
    if (normalizedId != null) {
        return YouTubeVideoIdNormalizer.canonicalWatchUrl(normalizedId)
    }
    return subtitle.videoId
}

internal fun observeLibraryItemsForRows(
    subtitleRepository: SubtitleRepository,
    collectionRepository: CollectionRepository,
    rows: List<LibraryVideoRow>
): Flow<List<LibraryItem>> {
    val videoIds = rows.map { it.videoId }
    if (videoIds.isEmpty()) {
        return flowOf(emptyList())
    }

    return subtitleRepository.observeSubtitleTracksForVideos(videoIds)
        .combine(collectionRepository.collections) { tracks, collections ->
            val tracksByVideoId = tracks.groupBy { it.videoId }
            val collectionCounts = collectionCountsByVideoId(collections)
            rows.map { row ->
                LibraryItem(
                    videoId = row.videoId,
                    videoUrl = displayUrlFor(row.videoId, row.videoUrl),
                    title = row.title,
                    channelName = row.channelName,
                    thumbnailLocalPath = row.thumbnailLocalPath,
                    subtitles = tracksByVideoId[row.videoId].orEmpty(),
                    uploadDate = row.uploadDate,
                    lastDownloaded = row.lastDownloaded,
                    lastOpenedAt = row.lastOpenedAt,
                    readingProgressPercent = row.readingProgressPercent,
                    currentPage = row.currentPage,
                    totalPages = row.totalPages,
                    isInLibrary = row.isInLibrary,
                    collectionCount = collectionCounts[row.videoId] ?: 0
                )
            }
        }
}

internal fun normalizeVideoIds(videoIds: List<String>): List<String> {
    return videoIds.map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

internal fun String.toSortOption(): SortOption {
    return SortOption.entries.firstOrNull { it.name == this } ?: SortOption.DOWNLOADED
}

internal fun String.toLibraryVisibilityFilter(): LibraryVisibilityFilter {
    return LibraryVisibilityFilter.entries.firstOrNull { it.name == this } ?: LibraryVisibilityFilter.ALL
}

internal fun String.toReadStatusFilter(): ReadStatusFilter {
    return ReadStatusFilter.entries.firstOrNull { it.name == this } ?: ReadStatusFilter.ALL
}
