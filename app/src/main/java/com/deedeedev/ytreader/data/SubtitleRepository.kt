package com.deedeedev.ytreader.data

import com.deedeedev.ytreader.data.local.AiCleaningStateDao
import android.util.Log
import com.deedeedev.ytreader.data.local.AiCleaningStateEntity
import com.deedeedev.ytreader.data.local.LibraryVideoRow
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.data.local.SubtitleReadingStateDao
import com.deedeedev.ytreader.data.local.SubtitleReadingStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class SubtitleWithStates(
    val subtitle: SubtitleEntity,
    val readingState: SubtitleReadingStateEntity?,
    val aiCleaningState: AiCleaningStateEntity?
)

class SubtitleRepository(
    private val subtitleDao: SubtitleDao,
    private val readingStateDao: SubtitleReadingStateDao,
    private val aiCleaningStateDao: AiCleaningStateDao
) {

    fun observeAll(): Flow<List<SubtitleEntity>> = subtitleDao.getAll()

    fun observeLibraryChannels(): Flow<List<String>> = subtitleDao.observeLibraryChannels()

    fun observeLibraryVideoRows(
        channelName: String?,
        sortOption: String,
        isAscending: Boolean
    ): Flow<List<LibraryVideoRow>> = subtitleDao.observeLibraryVideoRows(channelName, sortOption, isAscending)

    fun observeCollectionVideoRows(
        videoIds: List<String>,
        channelName: String?,
        sortOption: String,
        isAscending: Boolean
    ): Flow<List<LibraryVideoRow>> = subtitleDao.observeCollectionVideoRows(videoIds, channelName, sortOption, isAscending)

    fun observeHistoryVideoRows(): Flow<List<LibraryVideoRow>> = subtitleDao.observeHistoryVideoRows()

    fun observeCollectionChannels(videoIds: List<String>): Flow<List<String>> =
        subtitleDao.observeCollectionChannels(videoIds)

    fun observeSubtitleTracksForVideos(videoIds: List<String>): Flow<List<SubtitleEntity>> =
        subtitleDao.observeSubtitleTracksForVideos(videoIds)

    suspend fun getAiCleaningStatesForSubtitles(subtitleIds: List<Long>): Map<Long, AiCleaningStateEntity> = withContext(Dispatchers.IO) {
        subtitleIds.associate { id ->
            id to aiCleaningStateDao.getBySubtitleId(id)
        }.mapValues { it.value ?: AiCleaningStateEntity(subtitleId = it.key) }
    }

    suspend fun getReadingStatesForSubtitles(subtitleIds: List<Long>): Map<Long, SubtitleReadingStateEntity> = withContext(Dispatchers.IO) {
        subtitleIds.associate { id ->
            id to readingStateDao.getBySubtitleId(id)
        }.mapValues { it.value ?: SubtitleReadingStateEntity(subtitleId = it.key) }
    }

    suspend fun getMaxReadingProgressForVideos(videoIds: List<String>): Map<String, Int> = withContext(Dispatchers.IO) {
        val allSubtitles = subtitleDao.getAllSync()
        val relevantSubtitles = allSubtitles.filter { it.videoId in videoIds }
        if (relevantSubtitles.isEmpty()) return@withContext emptyMap()
        val states = getReadingStatesForSubtitles(relevantSubtitles.map { it.id })
        relevantSubtitles.groupBy { it.videoId }.mapValues { (_, subs) ->
            subs.maxOfOrNull { states[it.id]?.readingProgressPercent ?: 0 } ?: 0
        }
    }

    fun observeCollectionVideoCount(videoIds: List<String>): Flow<Int> =
        subtitleDao.observeCollectionVideoCount(videoIds)

    fun observeById(id: Long): Flow<SubtitleEntity?> = subtitleDao.observeById(id)

    fun observeByVideoId(videoId: String): Flow<List<SubtitleEntity>> = subtitleDao.observeByVideoId(videoId)

    fun observeAllAccessibleSubtitles(): Flow<List<SubtitleEntity>> = subtitleDao.observeAllAccessibleSubtitles()

    fun observeSubtitleWithStates(id: Long): Flow<SubtitleWithStates?> {
        return combine(
            subtitleDao.observeById(id),
            readingStateDao.observeBySubtitleId(id),
            aiCleaningStateDao.observeBySubtitleId(id)
        ) { subtitle, readingState, aiCleaningState ->
            subtitle?.let { SubtitleWithStates(it, readingState, aiCleaningState) }
        }
    }

    suspend fun getById(id: Long): SubtitleEntity? = withContext(Dispatchers.IO) {
        subtitleDao.getById(id)
    }

    suspend fun getPreferredSubtitleForVideo(videoId: String): SubtitleEntity? = withContext(Dispatchers.IO) {
        subtitleDao.getPreferredSubtitleForVideo(videoId)
    }

    suspend fun countByVideoId(videoId: String): Int = withContext(Dispatchers.IO) {
        subtitleDao.countByVideoId(videoId)
    }

    suspend fun insertIgnore(subtitle: SubtitleEntity): Long = withContext(Dispatchers.IO) {
        subtitleDao.insertIgnore(subtitle)
    }

    suspend fun upsertByIdentity(subtitle: SubtitleEntity): Long = withContext(Dispatchers.IO) {
        subtitleDao.upsertByIdentity(subtitle)
    }

    suspend fun getByIdentity(videoId: String, trackIdentity: String): SubtitleEntity? = withContext(Dispatchers.IO) {
        subtitleDao.getByIdentity(videoId, trackIdentity)
    }

    suspend fun delete(subtitle: SubtitleEntity) = withContext(Dispatchers.IO) {
        subtitleDao.delete(subtitle)
    }

    suspend fun updateDownloadedSubtitle(
        id: Long,
        videoUrl: String,
        title: String,
        channelName: String,
        languageCode: String,
        subtitleTrackId: String?,
        trackIdentity: String,
        isAutoGenerated: Boolean,
        content: String,
        createdAt: Long,
        uploadDate: Long,
        fontSize: Float,
        fontFamily: String
    ) = withContext(Dispatchers.IO) {
        subtitleDao.updateDownloadedSubtitle(
            id, videoUrl, title, channelName, languageCode, subtitleTrackId,
            trackIdentity, isAutoGenerated, content, createdAt, uploadDate, fontSize, fontFamily
        )
    }

    suspend fun updateLastTimestamp(id: Long, timestamp: Long) = withContext(Dispatchers.IO) {
        readingStateDao.updateLastTimestamp(id, timestamp)
    }

    suspend fun updateLastOpenedAt(id: Long, openedAt: Long) = withContext(Dispatchers.IO) {
        readingStateDao.insertIfNotExists(SubtitleReadingStateEntity(subtitleId = id))
        readingStateDao.updateLastOpenedAt(id, openedAt)
    }

    suspend fun replaceContentForRedownload(id: Long, content: String, createdAt: Long) = withContext(Dispatchers.IO) {
        subtitleDao.replaceContentForRedownload(id, content, createdAt)
        val readingState = SubtitleReadingStateEntity(
            subtitleId = id,
            lastTimestamp = 0L,
            lastStudyScroll = 0,
            readingProgressPercent = 0,
            currentPage = 0,
            totalPages = 0
        )
        readingStateDao.insert(readingState)
    }

    suspend fun resetReadingProgressForVideo(videoId: String) = withContext(Dispatchers.IO) {
        readingStateDao.resetReadingProgressForVideo(videoId)
    }

    suspend fun markVideoAsRead(videoId: String) = withContext(Dispatchers.IO) {
        readingStateDao.markVideoAsRead(videoId)
    }

    suspend fun deleteByVideoId(videoId: String) = withContext(Dispatchers.IO) {
        subtitleDao.deleteByVideoId(videoId)
    }

    suspend fun getMostRecentlyOpened(): SubtitleWithStates? = withContext(Dispatchers.IO) {
        val readingState = readingStateDao.getMostRecentlyOpened()
        readingState?.let { rs ->
            val subtitle = subtitleDao.getById(rs.subtitleId)
            subtitle?.let {
                val aiState = aiCleaningStateDao.getBySubtitleId(rs.subtitleId)
                SubtitleWithStates(it, rs, aiState)
            }
        }
    }

    suspend fun updateLibraryVisibility(videoId: String, isInLibrary: Boolean) = withContext(Dispatchers.IO) {
        subtitleDao.updateLibraryVisibility(videoId, isInLibrary)
    }

    suspend fun updateHighlights(subtitleId: Long, highlights: String) = withContext(Dispatchers.IO) {
        subtitleDao.updateHighlights(subtitleId, highlights)
    }

    suspend fun updateLastStudyScroll(subtitleId: Long, scrollPosition: Int) = withContext(Dispatchers.IO) {
        Log.d("PosRestore", "updateLastStudyScroll: subtitleId=$subtitleId scroll=$scrollPosition")
        val existing = readingStateDao.getBySubtitleId(subtitleId)
        if (existing == null) {
            readingStateDao.insertIfNotExists(SubtitleReadingStateEntity(subtitleId = subtitleId))
        }
        readingStateDao.updateLastStudyScroll(subtitleId, scrollPosition)
    }

    suspend fun updateReadingProgress(subtitleId: Long, percent: Int, currentPage: Int, totalPages: Int) = withContext(Dispatchers.IO) {
        readingStateDao.updateReadingProgress(subtitleId, percent, currentPage, totalPages)
    }

    suspend fun updateProgressRatio(subtitleId: Long, ratio: Float) = withContext(Dispatchers.IO) {
        readingStateDao.updateProgressRatio(subtitleId, ratio)
    }

    suspend fun updateFontSize(subtitleId: Long, fontSize: Float) = withContext(Dispatchers.IO) {
        subtitleDao.updateFontSize(subtitleId, fontSize)
    }

    suspend fun updateFontFamily(subtitleId: Long, fontFamily: String) = withContext(Dispatchers.IO) {
        subtitleDao.updateFontFamily(subtitleId, fontFamily)
    }

    suspend fun updateStudyContent(subtitleId: Long, studyContent: String) = withContext(Dispatchers.IO) {
        subtitleDao.updateStudyContent(subtitleId, studyContent)
    }

    suspend fun markAiCleaningQueued(subtitleId: Long, sourceText: String, updatedAt: Long) = withContext(Dispatchers.IO) {
        aiCleaningStateDao.markAiCleaningQueued(subtitleId, sourceText, updatedAt)
    }

    suspend fun storeAiCleaningResult(subtitleId: Long, result: String, updatedAt: Long) = withContext(Dispatchers.IO) {
        aiCleaningStateDao.storeAiCleaningResult(subtitleId, result, updatedAt)
    }

    suspend fun storeAiCleaningFailure(subtitleId: Long, summary: String?, log: String?, updatedAt: Long) = withContext(Dispatchers.IO) {
        aiCleaningStateDao.storeAiCleaningFailure(subtitleId, summary, log, updatedAt)
    }

    suspend fun cancelAiCleaning(subtitleId: Long, updatedAt: Long) = withContext(Dispatchers.IO) {
        aiCleaningStateDao.cancelAiCleaning(subtitleId, updatedAt)
    }

    suspend fun clearAiCleaningResult(subtitleId: Long) = withContext(Dispatchers.IO) {
        aiCleaningStateDao.clearAiCleaningResult(subtitleId)
    }

    suspend fun clearAiCleaningError(subtitleId: Long) = withContext(Dispatchers.IO) {
        aiCleaningStateDao.clearAiCleaningError(subtitleId)
    }

    suspend fun countLibraryEntriesByVideoId(videoId: String): Int = withContext(Dispatchers.IO) {
        subtitleDao.countLibraryEntriesByVideoId(videoId)
    }

    suspend fun getLibraryVideoIds(): List<String> = withContext(Dispatchers.IO) {
        subtitleDao.getLibraryVideoIds()
    }

    suspend fun clearHistoryForVideo(videoId: String) = withContext(Dispatchers.IO) {
        readingStateDao.clearLastOpenedAtForVideo(videoId)
    }
}