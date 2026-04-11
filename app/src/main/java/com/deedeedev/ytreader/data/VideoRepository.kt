package com.deedeedev.ytreader.data

import android.content.Context
import com.deedeedev.ytreader.data.local.VideoDao
import com.deedeedev.ytreader.data.local.VideoEntity
import com.deedeedev.ytreader.ui.home.downloadThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

class VideoRepository(
    private val videoDao: VideoDao
) {

    suspend fun upsert(video: VideoEntity) = withContext(Dispatchers.IO) {
        videoDao.upsert(video)
    }

    suspend fun getByVideoId(videoId: String): VideoEntity? = withContext(Dispatchers.IO) {
        videoDao.getByVideoId(videoId)
    }

    suspend fun getAll(): List<VideoEntity> = withContext(Dispatchers.IO) {
        videoDao.getAll()
    }

    suspend fun getAllMissingThumbnailPath(): List<VideoEntity> = withContext(Dispatchers.IO) {
        videoDao.getAllMissingThumbnailPath()
    }

    suspend fun getAllReferencedThumbnailPaths(): List<String> = withContext(Dispatchers.IO) {
        videoDao.getAllReferencedThumbnailPaths()
    }

    suspend fun deleteByVideoId(videoId: String) = withContext(Dispatchers.IO) {
        videoDao.deleteByVideoId(videoId)
    }

    suspend fun upsertVideoMetadata(
        youtubeRepository: YoutubeRepository,
        appContext: Context,
        videoId: String,
        fallbackVideoUrl: String,
        fallbackTitle: String,
        fallbackChannelName: String,
        fallbackUploadDate: Long,
        info: StreamInfo
    ) = withContext(Dispatchers.IO) {
        val existingVideo = videoDao.getByVideoId(videoId)
        val thumbnailSourceUrl = preferredThumbnailUrl(info.thumbnails)
        val localThumbnailPath = downloadThumbnail(youtubeRepository, appContext, videoId, thumbnailSourceUrl)
            ?: existingVideo?.thumbnailLocalPath

        if (thumbnailSourceUrl != null && localThumbnailPath == null) {
            existingVideo?.thumbnailLocalPath?.let { VideoThumbnailStore.delete(appContext, it) }
        }

        videoDao.upsert(
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

    suspend fun deleteVideoIfUnreferenced(
        subtitleRepository: SubtitleRepository,
        collectionRepository: CollectionRepository,
        appContext: Context,
        videoId: String
    ) = withContext(Dispatchers.IO) {
        val hasLibraryEntry = subtitleRepository.countLibraryEntriesByVideoId(videoId) > 0
        if (!hasLibraryEntry && !collectionRepository.isVideoInAnyCollection(videoId)) {
            videoDao.getByVideoId(videoId)?.thumbnailLocalPath?.let { path ->
                VideoThumbnailStore.delete(appContext, path)
            }
            videoDao.deleteByVideoId(videoId)
            subtitleRepository.deleteByVideoId(videoId)
        }
    }
}
