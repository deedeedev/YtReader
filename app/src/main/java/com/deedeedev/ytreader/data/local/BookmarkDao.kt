package com.deedeedev.ytreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query(
        """
        SELECT *
        FROM bookmarks
        WHERE subtitleId = :subtitleId
        ORDER BY anchorStart ASC, id ASC
        """
    )
    fun observeBySubtitleId(subtitleId: Long): Flow<List<BookmarkEntity>>

    @Query(
        """
        SELECT *
        FROM bookmarks
        WHERE subtitleId IN (:subtitleIds)
        ORDER BY updatedAt DESC, createdAt DESC, id DESC
        """
    )
    fun observeBySubtitleIds(subtitleIds: List<Long>): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity): Long

    @Query(
        """
        DELETE FROM bookmarks
        WHERE subtitleId = :subtitleId
          AND anchorStart = :anchorStart
        """
    )
    suspend fun deleteByAnchor(subtitleId: Long, anchorStart: Int)

    @Query("DELETE FROM bookmarks WHERE subtitleId = :subtitleId")
    suspend fun deleteBySubtitleId(subtitleId: Long)
}
