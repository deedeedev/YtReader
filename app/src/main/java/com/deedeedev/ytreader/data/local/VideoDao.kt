package com.deedeedev.ytreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(video: VideoEntity)

    @Query("SELECT * FROM videos WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): VideoEntity?

    @Query("SELECT * FROM videos ORDER BY updatedAt DESC")
    suspend fun getAll(): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE thumbnailLocalPath IS NULL OR TRIM(thumbnailLocalPath) = '' ORDER BY updatedAt DESC")
    suspend fun getAllMissingThumbnailPath(): List<VideoEntity>

    @Query("SELECT thumbnailLocalPath FROM videos WHERE thumbnailLocalPath IS NOT NULL AND TRIM(thumbnailLocalPath) != ''")
    suspend fun getAllReferencedThumbnailPaths(): List<String>

    @Query("DELETE FROM videos WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)
}
