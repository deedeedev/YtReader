package com.deedeedev.ytreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Transaction
    @Query("SELECT * FROM collections ORDER BY sortOrder ASC, createdAt DESC, name COLLATE NOCASE ASC")
    fun observeCollections(): Flow<List<CollectionWithVideos>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollectionVideos(entries: List<CollectionVideoEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollectionVideo(entry: CollectionVideoEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :collectionId")
    suspend fun renameCollection(collectionId: String, name: String): Int

    @Query("UPDATE collections SET sortOrder = :sortOrder WHERE id = :collectionId")
    suspend fun updateCollectionSortOrder(collectionId: String, sortOrder: Int)

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: String)

    @Query("DELETE FROM collection_videos WHERE collectionId = :collectionId AND videoId = :videoId")
    suspend fun removeVideoFromCollection(collectionId: String, videoId: String)

    @Query("DELETE FROM collection_videos WHERE videoId = :videoId")
    suspend fun removeVideoFromAllCollections(videoId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM collection_videos WHERE videoId = :videoId)")
    suspend fun isVideoInAnyCollection(videoId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM collections WHERE id = :collectionId)")
    suspend fun collectionExists(collectionId: String): Boolean

    @Query("SELECT COUNT(*) FROM collections WHERE LOWER(name) = LOWER(:name)")
    suspend fun countCollectionsByName(name: String): Int

    @Query("SELECT COUNT(*) FROM collections WHERE id != :collectionId AND LOWER(name) = LOWER(:name)")
    suspend fun countCollectionsByNameExcludingId(collectionId: String, name: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM collections")
    suspend fun nextCollectionSortOrder(): Int

    @Transaction
    suspend fun updateCollectionSortOrders(collectionIds: List<String>) {
        collectionIds.forEachIndexed { index, collectionId ->
            updateCollectionSortOrder(collectionId, index)
        }
    }

    @Query("DELETE FROM collection_videos")
    suspend fun clearCollectionVideos()

    @Query("DELETE FROM collections")
    suspend fun clearCollections()
}
