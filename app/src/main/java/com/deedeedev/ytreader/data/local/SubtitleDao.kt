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

    @Query("DELETE FROM subtitles WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)
}
