package com.deedeedev.ytreader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleDao {
    @Query("SELECT * FROM subtitles ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SubtitleEntity>>

    @Query(
        """
        SELECT DISTINCT channelName
        FROM subtitles
        WHERE channelName != ''
          AND isInLibrary = 1
        ORDER BY channelName COLLATE NOCASE ASC
        """
    )
    fun observeLibraryChannels(): Flow<List<String>>

    @Query(
        """
        SELECT
            agg.videoId AS videoId,
            COALESCE((
                SELECT s.videoUrl
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS videoUrl,
            COALESCE((
                SELECT s.title
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS title,
            COALESCE((
                SELECT s.channelName
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS channelName,
            COALESCE((
                SELECT s.uploadDate
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), 0) AS uploadDate,
            agg.lastDownloaded AS lastDownloaded,
            agg.lastOpenedAt AS lastOpenedAt,
            agg.isInLibrary AS isInLibrary
        FROM (
            SELECT
                videoId,
                MAX(createdAt) AS lastDownloaded,
                MAX(lastOpenedAt) AS lastOpenedAt,
                MAX(isInLibrary) AS isInLibrary
            FROM subtitles
            WHERE isInLibrary = 1
              AND (:channelName IS NULL OR channelName = :channelName)
            GROUP BY videoId
        ) agg
        ORDER BY
            CASE WHEN :sortOption = 'TITLE' AND :isAscending = 1 THEN title END COLLATE NOCASE ASC,
            CASE WHEN :sortOption = 'TITLE' AND :isAscending = 0 THEN title END COLLATE NOCASE DESC,
            CASE WHEN :sortOption = 'CHANNEL_NAME' AND :isAscending = 1 THEN channelName END COLLATE NOCASE ASC,
            CASE WHEN :sortOption = 'CHANNEL_NAME' AND :isAscending = 0 THEN channelName END COLLATE NOCASE DESC,
            CASE WHEN :sortOption = 'DATE_PUBLISHED' AND :isAscending = 1 THEN uploadDate END ASC,
            CASE WHEN :sortOption = 'DATE_PUBLISHED' AND :isAscending = 0 THEN uploadDate END DESC,
            CASE WHEN :sortOption = 'DOWNLOADED' AND :isAscending = 1 THEN lastDownloaded END ASC,
            CASE WHEN :sortOption = 'DOWNLOADED' AND :isAscending = 0 THEN lastDownloaded END DESC,
            CASE WHEN :sortOption = 'LAST_OPENED' AND :isAscending = 1 THEN lastOpenedAt END ASC,
            CASE WHEN :sortOption = 'LAST_OPENED' AND :isAscending = 0 THEN lastOpenedAt END DESC,
            videoId ASC
        """
    )
    fun observeLibraryVideoRows(
        channelName: String?,
        sortOption: String,
        isAscending: Boolean
    ): Flow<List<LibraryVideoRow>>

    @Query(
        """
        SELECT DISTINCT channelName
        FROM subtitles
        WHERE channelName != ''
          AND videoId IN (:videoIds)
        ORDER BY channelName COLLATE NOCASE ASC
        """
    )
    fun observeCollectionChannels(videoIds: List<String>): Flow<List<String>>

    @Query(
        """
        SELECT
            agg.videoId AS videoId,
            COALESCE((
                SELECT s.videoUrl
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND s.videoId IN (:videoIds)
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS videoUrl,
            COALESCE((
                SELECT s.title
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND s.videoId IN (:videoIds)
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS title,
            COALESCE((
                SELECT s.channelName
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND s.videoId IN (:videoIds)
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS channelName,
            COALESCE((
                SELECT s.uploadDate
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND s.videoId IN (:videoIds)
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), 0) AS uploadDate,
            agg.lastDownloaded AS lastDownloaded,
            agg.lastOpenedAt AS lastOpenedAt,
            agg.isInLibrary AS isInLibrary
        FROM (
            SELECT
                videoId,
                MAX(createdAt) AS lastDownloaded,
                MAX(lastOpenedAt) AS lastOpenedAt,
                MAX(isInLibrary) AS isInLibrary
            FROM subtitles
            WHERE videoId IN (:videoIds)
              AND (:channelName IS NULL OR channelName = :channelName)
            GROUP BY videoId
        ) agg
        ORDER BY
            CASE WHEN :sortOption = 'TITLE' AND :isAscending = 1 THEN title END COLLATE NOCASE ASC,
            CASE WHEN :sortOption = 'TITLE' AND :isAscending = 0 THEN title END COLLATE NOCASE DESC,
            CASE WHEN :sortOption = 'CHANNEL_NAME' AND :isAscending = 1 THEN channelName END COLLATE NOCASE ASC,
            CASE WHEN :sortOption = 'CHANNEL_NAME' AND :isAscending = 0 THEN channelName END COLLATE NOCASE DESC,
            CASE WHEN :sortOption = 'DATE_PUBLISHED' AND :isAscending = 1 THEN uploadDate END ASC,
            CASE WHEN :sortOption = 'DATE_PUBLISHED' AND :isAscending = 0 THEN uploadDate END DESC,
            CASE WHEN :sortOption = 'DOWNLOADED' AND :isAscending = 1 THEN lastDownloaded END ASC,
            CASE WHEN :sortOption = 'DOWNLOADED' AND :isAscending = 0 THEN lastDownloaded END DESC,
            CASE WHEN :sortOption = 'LAST_OPENED' AND :isAscending = 1 THEN lastOpenedAt END ASC,
            CASE WHEN :sortOption = 'LAST_OPENED' AND :isAscending = 0 THEN lastOpenedAt END DESC,
            videoId ASC
        """
    )
    fun observeCollectionVideoRows(
        videoIds: List<String>,
        channelName: String?,
        sortOption: String,
        isAscending: Boolean
    ): Flow<List<LibraryVideoRow>>

    @Query(
        """
        SELECT *
        FROM subtitles
        WHERE videoId IN (:videoIds)
        ORDER BY videoId ASC, languageCode ASC
        """
    )
    fun observeSubtitleTracksForVideos(videoIds: List<String>): Flow<List<SubtitleEntity>>

    @Query("SELECT COUNT(DISTINCT videoId) FROM subtitles WHERE videoId IN (:videoIds)")
    fun observeCollectionVideoCount(videoIds: List<String>): Flow<Int>

    @Query("SELECT * FROM subtitles WHERE id = :id")
    fun observeById(id: Long): Flow<SubtitleEntity?>

    @Query("SELECT * FROM subtitles WHERE id = :id")
    suspend fun getById(id: Long): SubtitleEntity?

    @Query("SELECT COUNT(*) FROM subtitles WHERE videoId = :videoId")
    suspend fun countByVideoId(videoId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(subtitle: SubtitleEntity): Long

    @Transaction
    suspend fun upsertByIdentity(subtitle: SubtitleEntity): Long {
        val insertedId = insertIgnore(subtitle)
        if (insertedId != -1L) {
            return insertedId
        }

        val existing = getByIdentity(subtitle.videoId, subtitle.trackIdentity)
            ?: return insertIgnore(subtitle)

        updateDownloadedSubtitle(
            id = existing.id,
            videoUrl = subtitle.videoUrl,
            title = subtitle.title,
            channelName = subtitle.channelName,
            languageCode = subtitle.languageCode,
            subtitleTrackId = subtitle.subtitleTrackId,
            trackIdentity = subtitle.trackIdentity,
            isAutoGenerated = subtitle.isAutoGenerated,
            content = subtitle.content,
            createdAt = subtitle.createdAt,
            uploadDate = subtitle.uploadDate,
            fontSize = subtitle.fontSize,
            fontFamily = subtitle.fontFamily
        )
        return existing.id
    }

    @Query(
        "SELECT * FROM subtitles WHERE videoId = :videoId AND trackIdentity = :trackIdentity LIMIT 1"
    )
    suspend fun getByIdentity(videoId: String, trackIdentity: String): SubtitleEntity?

    @Delete
    suspend fun delete(subtitle: SubtitleEntity)

    @Query(
        """
        UPDATE subtitles
        SET videoUrl = :videoUrl,
            title = :title,
            channelName = :channelName,
            languageCode = :languageCode,
            subtitleTrackId = :subtitleTrackId,
            trackIdentity = :trackIdentity,
            isAutoGenerated = :isAutoGenerated,
            content = :content,
            createdAt = :createdAt,
            uploadDate = :uploadDate,
            fontSize = :fontSize,
            fontFamily = :fontFamily
        WHERE id = :id
        """
    )
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
    )

    @Query("UPDATE subtitles SET lastTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLastTimestamp(id: Long, timestamp: Long)

    @Query("UPDATE subtitles SET lastOpenedAt = :openedAt WHERE id = :id")
    suspend fun updateLastOpenedAt(id: Long, openedAt: Long)

    @Query("UPDATE subtitles SET isInLibrary = :isInLibrary WHERE videoId = :videoId")
    suspend fun updateLibraryVisibility(videoId: String, isInLibrary: Boolean)

    @Query("SELECT COUNT(*) FROM subtitles WHERE videoId = :videoId AND isInLibrary = 1")
    suspend fun countLibraryEntriesByVideoId(videoId: String): Int

    @Query("UPDATE subtitles SET lastStudyScroll = :scroll WHERE id = :id")
    suspend fun updateLastStudyScroll(id: Long, scroll: Int)

    @Query("UPDATE subtitles SET fontSize = :fontSize WHERE id = :id")
    suspend fun updateFontSize(id: Long, fontSize: Float)

    @Query("UPDATE subtitles SET fontFamily = :fontFamily WHERE id = :id")
    suspend fun updateFontFamily(id: Long, fontFamily: String)

    @Query("UPDATE subtitles SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("UPDATE subtitles SET studyContent = :studyContent WHERE id = :id")
    suspend fun updateStudyContent(id: Long, studyContent: String)

    @Query("UPDATE subtitles SET highlights = :highlights WHERE id = :id")
    suspend fun updateHighlights(id: Long, highlights: String)

    @Query(
        """
        UPDATE subtitles
        SET aiCleaningInProgress = 1,
            aiCleaningSourceText = :sourceText,
            aiCleaningPendingResult = NULL,
            aiCleaningErrorSummary = NULL,
            aiCleaningErrorLog = NULL,
            aiCleaningUpdatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun markAiCleaningQueued(id: Long, sourceText: String, updatedAt: Long)

    @Query(
        """
        UPDATE subtitles
        SET aiCleaningInProgress = 0,
            aiCleaningSourceText = NULL,
            aiCleaningPendingResult = :cleanedText,
            aiCleaningErrorSummary = NULL,
            aiCleaningErrorLog = NULL,
            aiCleaningUpdatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun storeAiCleaningResult(id: Long, cleanedText: String, updatedAt: Long)

    @Query(
        """
        UPDATE subtitles
        SET aiCleaningInProgress = 0,
            aiCleaningSourceText = NULL,
            aiCleaningPendingResult = NULL,
            aiCleaningErrorSummary = :summary,
            aiCleaningErrorLog = :log,
            aiCleaningUpdatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun storeAiCleaningFailure(id: Long, summary: String, log: String, updatedAt: Long)

    @Query(
        """
        UPDATE subtitles
        SET aiCleaningInProgress = 0,
            aiCleaningSourceText = NULL,
            aiCleaningPendingResult = NULL,
            aiCleaningErrorSummary = NULL,
            aiCleaningErrorLog = NULL,
            aiCleaningUpdatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun cancelAiCleaning(id: Long, updatedAt: Long)

    @Query(
        """
        UPDATE subtitles
        SET aiCleaningPendingResult = NULL,
            aiCleaningUpdatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun clearAiCleaningResult(id: Long, updatedAt: Long)

    @Query(
        """
        UPDATE subtitles
        SET aiCleaningErrorSummary = NULL,
            aiCleaningErrorLog = NULL,
            aiCleaningUpdatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun clearAiCleaningError(id: Long, updatedAt: Long)

    @Query(
        """
        UPDATE subtitles
        SET content = :content,
            createdAt = :createdAt,
            studyContent = NULL,
            highlights = '',
            lastTimestamp = 0,
            lastStudyScroll = 0
        WHERE id = :id
        """
    )
    suspend fun replaceContentForRedownload(id: Long, content: String, createdAt: Long)

    @Query("DELETE FROM subtitles WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)
}
