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
    suspend fun getById(id: Long): SubtitleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subtitle: SubtitleEntity)

    @Delete
    suspend fun delete(subtitle: SubtitleEntity)

    @Query("UPDATE subtitles SET lastTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLastTimestamp(id: Long, timestamp: Long)

    @Query("UPDATE subtitles SET fontSize = :fontSize WHERE id = :id")
    suspend fun updateFontSize(id: Long, fontSize: Float)

    @Query("UPDATE subtitles SET fontFamily = :fontFamily WHERE id = :id")
    suspend fun updateFontFamily(id: Long, fontFamily: String)

    @Query("UPDATE subtitles SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("UPDATE subtitles SET content = :content, createdAt = :createdAt WHERE id = :id")
    suspend fun updateContentAndCreatedAt(id: Long, content: String, createdAt: Long)

    @Query("DELETE FROM subtitles WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)
}
