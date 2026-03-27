package com.deedeedev.ytreader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC")
    fun getAll(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id IN (SELECT id FROM search_history ORDER BY searchedAt ASC LIMIT :limit)")
    suspend fun deleteOldest(limit: Int)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Long)
}
