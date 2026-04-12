package com.deedeedev.ytreader.ui.home

import android.content.Context
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.StringProvider
import com.deedeedev.ytreader.data.CollectionRepository
import com.deedeedev.ytreader.data.NoteRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.VideoRepository
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.VideoCollection
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer

internal class VideoOperationsHelper(
    private val stringProvider: StringProvider,
    private val appContext: Context,
    private val youtubeRepository: YoutubeRepository,
    private val subtitleRepository: SubtitleRepository,
    private val videoRepository: VideoRepository,
    private val noteRepository: NoteRepository,
    private val collectionRepository: CollectionRepository
) {
    suspend fun downloadSubtitleAgain(
        subtitle: SubtitleEntity,
        onDownloadingChange: (Long, Boolean) -> Unit,
        onError: (String?) -> Unit,
        onEvent: (String) -> Unit
    ) {
        onDownloadingChange(subtitle.id, true)
        try {
            val info = youtubeRepository.getStreamInfo(resolveVideoLookupUrl(subtitle))
            val matchingSubtitle = SubtitleIdentityMatcher.findMatchingStream(
                savedSubtitle = subtitle,
                streams = info.subtitles
            )
                ?: throw IllegalStateException(
                    stringProvider.getString(R.string.matching_subtitle_not_found)
                )

            val subtitleContent = matchingSubtitle.content
            val rawContent = if (matchingSubtitle.isUrl) {
                youtubeRepository.downloadSubtitle(subtitleContent)
            } else {
                subtitleContent
            }

            subtitleRepository.replaceContentForRedownload(
                id = subtitle.id,
                content = rawContent,
                createdAt = System.currentTimeMillis()
            )
            noteRepository.deleteHighlightsBySubtitleId(subtitle.id)
            noteRepository.deleteBookmarksBySubtitleId(subtitle.id)
            upsertVideoMetadata(
                videoRepository = videoRepository,
                youtubeRepository = youtubeRepository,
                appContext = appContext,
                videoId = subtitle.videoId,
                fallbackVideoUrl = displayUrlFor(subtitle.videoId, subtitle.videoUrl),
                fallbackTitle = info.name,
                fallbackChannelName = info.uploaderName ?: stringProvider.getString(R.string.channel_unknown),
                fallbackUploadDate = info.uploadDate?.instant?.toEpochMilli() ?: 0L,
                info = info
            )
            onError(null)
        } catch (e: Exception) {
            val message = e.message ?: stringProvider.getString(R.string.download_failed)
            onError(message)
        } finally {
            onDownloadingChange(subtitle.id, false)
        }
    }

    suspend fun downloadThumbnailForVideo(
        videoId: String,
        videoUrl: String,
        title: String,
        channelName: String,
        uploadDate: Long,
        onDownloadingChange: (String, Boolean) -> Unit,
        onError: (String?) -> Unit,
        onEvent: (String) -> Unit
    ) {
        onDownloadingChange(videoId, true)
        try {
            val info = youtubeRepository.getStreamInfo(displayUrlFor(videoId, videoUrl))
            upsertVideoMetadata(
                videoRepository = videoRepository,
                youtubeRepository = youtubeRepository,
                appContext = appContext,
                videoId = videoId,
                fallbackVideoUrl = displayUrlFor(videoId, videoUrl),
                fallbackTitle = info.name.ifBlank { title },
                fallbackChannelName = (info.uploaderName ?: channelName).ifBlank { channelName },
                fallbackUploadDate = info.uploadDate?.instant?.toEpochMilli() ?: uploadDate,
                info = info
            )
            onError(null)
            onEvent(stringProvider.getString(R.string.library_thumbnail_downloaded))
        } catch (e: Exception) {
            val errorMessage = e.message ?: stringProvider.getString(R.string.library_thumbnail_download_failed)
            onError(errorMessage)
            onEvent(errorMessage)
        } finally {
            onDownloadingChange(videoId, false)
        }
    }

    fun createCollection(name: String, existingCollections: List<VideoCollection>): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return false
        }
        if (existingCollections.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            return false
        }
        return true
    }

    fun addVideoToCollection(
        collectionId: String,
        videoId: String,
        existingCollections: List<VideoCollection>
    ): Boolean {
        val collection = existingCollections.firstOrNull { c -> c.id == collectionId } ?: return false
        val normalizedVideoId = YouTubeVideoIdNormalizer.extractVideoId(videoId.trim()) ?: videoId.trim()
        if (normalizedVideoId.isBlank()) {
            return false
        }
        if (normalizedVideoId in collection.videoIds) {
            return true
        }
        return true
    }

    suspend fun deleteSubtitle(subtitle: SubtitleEntity) {
        val subtitleCountForVideo = subtitleRepository.countByVideoId(subtitle.videoId)
        if (subtitleCountForVideo <= 1) {
            collectionRepository.removeVideoFromAllCollections(subtitle.videoId)
        }
        subtitleRepository.delete(subtitle)
    }

    suspend fun markVideoAsRead(videoId: String) {
        subtitleRepository.markVideoAsRead(videoId)
    }

    suspend fun resetVideoProgress(videoId: String) {
        subtitleRepository.resetReadingProgressForVideo(videoId)
    }

    suspend fun restoreLibraryItem(subtitles: List<SubtitleEntity>) {
        val videoId = subtitles.firstOrNull()?.videoId ?: return
        subtitles.forEach { subtitle ->
            subtitleRepository.upsertByIdentity(subtitle.copy(isInLibrary = true))
        }
        subtitleRepository.updateLibraryVisibility(videoId, true)
    }
}
