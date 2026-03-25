package com.deedeedev.ytreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightNoteDao {
    @Query(
        """
        SELECT *
        FROM highlight_notes
        WHERE subtitleId = :subtitleId
        ORDER BY highlightStart ASC, highlightEnd ASC, id ASC
        """
    )
    fun observeBySubtitleId(subtitleId: Long): Flow<List<HighlightNoteEntity>>

    @Query(
        """
        SELECT *
        FROM highlight_notes
        WHERE subtitleId IN (:subtitleIds)
        ORDER BY updatedAt DESC, createdAt DESC, id DESC
        """
    )
    fun observeBySubtitleIds(subtitleIds: List<Long>): Flow<List<HighlightNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: HighlightNoteEntity): Long

    @Query(
        """
        DELETE FROM highlight_notes
        WHERE subtitleId = :subtitleId
          AND highlightStart = :highlightStart
          AND highlightEnd = :highlightEnd
        """
    )
    suspend fun deleteByRange(subtitleId: Long, highlightStart: Int, highlightEnd: Int)

    @Query("DELETE FROM highlight_notes WHERE subtitleId = :subtitleId")
    suspend fun deleteBySubtitleId(subtitleId: Long)
}
