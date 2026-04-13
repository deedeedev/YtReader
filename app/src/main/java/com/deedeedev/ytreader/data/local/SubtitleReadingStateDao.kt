package com.deedeedev.ytreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleReadingStateDao {
    @Query("SELECT * FROM subtitle_reading_states WHERE subtitleId = :subtitleId")
    fun observeBySubtitleId(subtitleId: Long): Flow<SubtitleReadingStateEntity?>

    @Query("SELECT * FROM subtitle_reading_states WHERE subtitleId = :subtitleId")
    suspend fun getBySubtitleId(subtitleId: Long): SubtitleReadingStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: SubtitleReadingStateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(state: SubtitleReadingStateEntity)

    @Update
    suspend fun update(state: SubtitleReadingStateEntity)

    @Query("UPDATE subtitle_reading_states SET lastTimestamp = :timestamp WHERE subtitleId = :subtitleId")
    suspend fun updateLastTimestamp(subtitleId: Long, timestamp: Long)

    @Query("UPDATE subtitle_reading_states SET lastOpenedAt = :openedAt WHERE subtitleId = :subtitleId")
    suspend fun updateLastOpenedAt(subtitleId: Long, openedAt: Long)

    @Query("UPDATE subtitle_reading_states SET lastStudyScroll = :scrollPosition WHERE subtitleId = :subtitleId")
    suspend fun updateLastStudyScroll(subtitleId: Long, scrollPosition: Int)

    @Query("UPDATE subtitle_reading_states SET readingProgressPercent = :percent, currentPage = :currentPage, totalPages = :totalPages WHERE subtitleId = :subtitleId")
    suspend fun updateReadingProgress(subtitleId: Long, percent: Int, currentPage: Int, totalPages: Int)

    @Query("""
        UPDATE subtitle_reading_states
        SET readingProgressPercent = 0, currentPage = 0
        WHERE subtitleId IN (SELECT id FROM subtitles WHERE videoId = :videoId)
    """)
    suspend fun resetReadingProgressForVideo(videoId: String)

    @Query("""
        UPDATE subtitle_reading_states
        SET isRead = 1, readingProgressPercent = 100
        WHERE subtitleId IN (SELECT id FROM subtitles WHERE videoId = :videoId)
    """)
    suspend fun markVideoAsRead(videoId: String)

    @Query("SELECT * FROM subtitle_reading_states WHERE lastOpenedAt > 0 ORDER BY lastOpenedAt DESC LIMIT 1")
    suspend fun getMostRecentlyOpened(): SubtitleReadingStateEntity?

    @Query("DELETE FROM subtitle_reading_states WHERE subtitleId = :subtitleId")
    suspend fun delete(subtitleId: Long)
}