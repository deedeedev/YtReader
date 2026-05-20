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

    @Query("SELECT * FROM subtitles ORDER BY createdAt DESC")
    suspend fun getAllSync(): List<SubtitleEntity>

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
            COALESCE(v.videoUrl, (
                SELECT s.videoUrl
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND (:isCollection = 0 OR s.videoId IN (:videoIds))
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS videoUrl,
            COALESCE(v.title, (
                SELECT s.title
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND (:isCollection = 0 OR s.videoId IN (:videoIds))
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS title,
            COALESCE(v.channelName, (
                SELECT s.channelName
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND (:isCollection = 0 OR s.videoId IN (:videoIds))
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS channelName,
            COALESCE(v.uploadDate, (
                SELECT s.uploadDate
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                  AND (:isCollection = 0 OR s.videoId IN (:videoIds))
                  AND (:channelName IS NULL OR s.channelName = :channelName)
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), 0) AS uploadDate,
            v.thumbnailLocalPath AS thumbnailLocalPath,
            agg.lastDownloaded AS lastDownloaded,
            COALESCE(agg.lastOpenedAt, 0) AS lastOpenedAt,
            COALESCE((
                SELECT rs.readingProgressPercent
                FROM subtitle_reading_states rs
                INNER JOIN subtitles s2 ON s2.id = rs.subtitleId
                WHERE s2.videoId = agg.videoId
                  AND (:isCollection = 0 OR s2.videoId IN (:videoIds))
                  AND (:channelName IS NULL OR s2.channelName = :channelName)
                ORDER BY rs.lastOpenedAt DESC, s2.createdAt DESC, s2.id DESC
                LIMIT 1
            ), 0) AS readingProgressPercent,
            COALESCE((
                SELECT rs.currentPage
                FROM subtitle_reading_states rs
                INNER JOIN subtitles s2 ON s2.id = rs.subtitleId
                WHERE s2.videoId = agg.videoId
                  AND (:isCollection = 0 OR s2.videoId IN (:videoIds))
                  AND (:channelName IS NULL OR s2.channelName = :channelName)
                ORDER BY rs.lastOpenedAt DESC, s2.createdAt DESC, s2.id DESC
                LIMIT 1
            ), 0) AS currentPage,
            COALESCE((
                SELECT rs.totalPages
                FROM subtitle_reading_states rs
                INNER JOIN subtitles s2 ON s2.id = rs.subtitleId
                WHERE s2.videoId = agg.videoId
                  AND (:isCollection = 0 OR s2.videoId IN (:videoIds))
                  AND (:channelName IS NULL OR s2.channelName = :channelName)
                ORDER BY rs.lastOpenedAt DESC, s2.createdAt DESC, s2.id DESC
                LIMIT 1
            ), 0) AS totalPages,
            agg.isInLibrary AS isInLibrary
        FROM (
            SELECT
                s.videoId,
                MAX(s.createdAt) AS lastDownloaded,
                MAX(rs.lastOpenedAt) AS lastOpenedAt,
                MAX(s.isInLibrary) AS isInLibrary
            FROM subtitles s
            LEFT JOIN subtitle_reading_states rs ON rs.subtitleId = s.id
            WHERE (:isCollection = 0 AND s.isInLibrary = 1 OR :isCollection = 1 AND s.videoId IN (:videoIds))
              AND (:channelName IS NULL OR s.channelName = :channelName)
            GROUP BY s.videoId
        ) agg
        LEFT JOIN videos v ON v.videoId = agg.videoId
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
    fun observeVideoRows(
        isCollection: Boolean,
        videoIds: List<String>,
        channelName: String?,
        sortOption: String,
        isAscending: Boolean
    ): Flow<List<LibraryVideoRow>>

    fun observeLibraryVideoRows(
        channelName: String?,
        sortOption: String,
        isAscending: Boolean
    ): Flow<List<LibraryVideoRow>> = observeVideoRows(
        isCollection = false,
        videoIds = listOf(""),
        channelName = channelName,
        sortOption = sortOption,
        isAscending = isAscending
    )

    fun observeCollectionVideoRows(
        videoIds: List<String>,
        channelName: String?,
        sortOption: String,
        isAscending: Boolean
    ): Flow<List<LibraryVideoRow>> = observeVideoRows(
        isCollection = true,
        videoIds = videoIds,
        channelName = channelName,
        sortOption = sortOption,
        isAscending = isAscending
    )

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

    @Query(
        """
        SELECT *
        FROM subtitles
        WHERE videoId = :videoId
        ORDER BY createdAt DESC, id DESC
        """
    )
    fun observeByVideoId(videoId: String): Flow<List<SubtitleEntity>>

    @Query("SELECT * FROM subtitles WHERE id = :id")
    suspend fun getById(id: Long): SubtitleEntity?

    @Query(
        """
        SELECT *
        FROM subtitles
        WHERE videoId = :videoId
        ORDER BY createdAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getPreferredSubtitleForVideo(videoId: String): SubtitleEntity?

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

    @Query("DELETE FROM subtitles WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("UPDATE subtitles SET isInLibrary = :isInLibrary WHERE videoId = :videoId")
    suspend fun updateLibraryVisibility(videoId: String, isInLibrary: Boolean)

    @Query("UPDATE subtitles SET highlights = :highlights WHERE id = :subtitleId")
    suspend fun updateHighlights(subtitleId: Long, highlights: String)

    @Query("UPDATE subtitles SET fontSize = :fontSize WHERE id = :subtitleId")
    suspend fun updateFontSize(subtitleId: Long, fontSize: Float)

    @Query("UPDATE subtitles SET fontFamily = :fontFamily WHERE id = :subtitleId")
    suspend fun updateFontFamily(subtitleId: Long, fontFamily: String)

    @Query("UPDATE subtitles SET studyContent = :studyContent WHERE id = :subtitleId")
    suspend fun updateStudyContent(subtitleId: Long, studyContent: String)

    @Query(
        """
        UPDATE subtitles SET
            content = :content,
            createdAt = :createdAt,
            studyContent = NULL,
            highlights = ''
        WHERE id = :id
        """
    )
    suspend fun replaceContentForRedownload(id: Long, content: String, createdAt: Long)

    @Query("SELECT COUNT(*) FROM subtitles WHERE videoId = :videoId AND isInLibrary = 1")
    suspend fun countLibraryEntriesByVideoId(videoId: String): Int

    @Query("SELECT DISTINCT videoId FROM subtitles WHERE isInLibrary = 1")
    suspend fun getLibraryVideoIds(): List<String>

    @Query("""
        SELECT DISTINCT s.* FROM subtitles s
        LEFT JOIN collection_videos cv ON s.videoId = cv.videoId
        LEFT JOIN subtitle_reading_states rs ON rs.subtitleId = s.id
        WHERE s.isInLibrary = 1 OR cv.videoId IS NOT NULL
        ORDER BY COALESCE(rs.lastOpenedAt, 0) DESC
    """)
    fun observeAllAccessibleSubtitles(): Flow<List<SubtitleEntity>>

    @Query(
        """
        SELECT
            agg.videoId AS videoId,
            COALESCE(v.videoUrl, (
                SELECT s.videoUrl
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS videoUrl,
            COALESCE(v.title, (
                SELECT s.title
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS title,
            COALESCE(v.channelName, (
                SELECT s.channelName
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), '') AS channelName,
            COALESCE(v.uploadDate, (
                SELECT s.uploadDate
                FROM subtitles s
                WHERE s.videoId = agg.videoId
                ORDER BY s.createdAt DESC, s.id DESC
                LIMIT 1
            ), 0) AS uploadDate,
            v.thumbnailLocalPath AS thumbnailLocalPath,
            agg.lastDownloaded AS lastDownloaded,
            COALESCE(agg.lastOpenedAt, 0) AS lastOpenedAt,
            COALESCE((
                SELECT rs.readingProgressPercent
                FROM subtitle_reading_states rs
                INNER JOIN subtitles s2 ON s2.id = rs.subtitleId
                WHERE s2.videoId = agg.videoId
                ORDER BY rs.lastOpenedAt DESC, s2.createdAt DESC, s2.id DESC
                LIMIT 1
            ), 0) AS readingProgressPercent,
            COALESCE((
                SELECT rs.currentPage
                FROM subtitle_reading_states rs
                INNER JOIN subtitles s2 ON s2.id = rs.subtitleId
                WHERE s2.videoId = agg.videoId
                ORDER BY rs.lastOpenedAt DESC, s2.createdAt DESC, s2.id DESC
                LIMIT 1
            ), 0) AS currentPage,
            COALESCE((
                SELECT rs.totalPages
                FROM subtitle_reading_states rs
                INNER JOIN subtitles s2 ON s2.id = rs.subtitleId
                WHERE s2.videoId = agg.videoId
                ORDER BY rs.lastOpenedAt DESC, s2.createdAt DESC, s2.id DESC
                LIMIT 1
            ), 0) AS totalPages,
            agg.isInLibrary AS isInLibrary
        FROM (
            SELECT
                s.videoId,
                MAX(s.createdAt) AS lastDownloaded,
                MAX(rs.lastOpenedAt) AS lastOpenedAt,
                MAX(s.isInLibrary) AS isInLibrary
            FROM subtitles s
            LEFT JOIN subtitle_reading_states rs ON rs.subtitleId = s.id
            LEFT JOIN collection_videos cv ON cv.videoId = s.videoId
            WHERE rs.lastOpenedAt > 0
              AND (s.isInLibrary = 1 OR cv.videoId IS NOT NULL)
            GROUP BY s.videoId
            ORDER BY MAX(rs.lastOpenedAt) DESC
            LIMIT 100
        ) agg
        LEFT JOIN videos v ON v.videoId = agg.videoId
        ORDER BY agg.lastOpenedAt DESC
        """
    )
    fun observeHistoryVideoRows(): Flow<List<LibraryVideoRow>>
}