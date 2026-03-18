package com.deedeedev.ytreader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleDao {
    @Query("SELECT * FROM subtitles ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SubtitleEntity>>

    @Query("SELECT * FROM subtitles WHERE id = :id")
    fun observeById(id: Long): Flow<SubtitleEntity?>

    @Query("SELECT * FROM subtitles WHERE id = :id")
    suspend fun getById(id: Long): SubtitleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subtitle: SubtitleEntity)

    @Delete
    suspend fun delete(subtitle: SubtitleEntity)

    @Query("UPDATE subtitles SET lastTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLastTimestamp(id: Long, timestamp: Long)

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
