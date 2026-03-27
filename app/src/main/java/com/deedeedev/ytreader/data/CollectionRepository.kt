package com.deedeedev.ytreader.data

import com.deedeedev.ytreader.data.local.CollectionDao
import com.deedeedev.ytreader.data.local.CollectionEntity
import com.deedeedev.ytreader.data.local.CollectionVideoEntity
import com.deedeedev.ytreader.domain.YouTubeVideoIdNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class CollectionRepository(
    private val collectionDao: CollectionDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    val collections: Flow<List<VideoCollection>> = collectionDao.observeCollections().map { collections ->
        collections.map { item ->
            VideoCollection(
                id = item.collection.id,
                name = item.collection.name,
                videoIds = item.videoIds,
                sortOrder = item.collection.sortOrder,
                createdAt = item.collection.createdAt
            )
        }
    }

    suspend fun migrateLegacyCollectionsIfNeeded() {
        if (userPreferencesRepository.areCollectionsMigratedToDatabase()) {
            return
        }

        val legacyCollections = UserPreferencesRepository.parseLegacyCollectionsJson(
            userPreferencesRepository.getLegacyCollectionsJson()
        )

        if (legacyCollections.isNotEmpty()) {
            collectionDao.clearCollectionVideos()
            collectionDao.clearCollections()
            legacyCollections.forEachIndexed { index, collection ->
                val normalizedVideoIds = normalizeVideoIds(collection.videoIds)
                collectionDao.insertCollection(
                    CollectionEntity(
                        id = collection.id,
                        name = collection.name.trim(),
                        sortOrder = index,
                        createdAt = collection.createdAt
                    )
                )
                if (normalizedVideoIds.isNotEmpty()) {
                    collectionDao.insertCollectionVideos(
                        normalizedVideoIds.map { videoId ->
                            CollectionVideoEntity(
                                collectionId = collection.id,
                                videoId = videoId,
                                addedAt = collection.createdAt
                            )
                        }
                    )
                }
            }
        }

        userPreferencesRepository.clearLegacyCollections()
        userPreferencesRepository.markCollectionsMigratedToDatabase()
    }

    suspend fun createCollection(name: String): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return false
        }
        if (collectionDao.countCollectionsByName(trimmedName) > 0) {
            return false
        }

        collectionDao.insertCollection(
            CollectionEntity(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
                sortOrder = collectionDao.nextCollectionSortOrder()
            )
        )
        return true
    }

    suspend fun renameCollection(collectionId: String, newName: String): Boolean {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            return false
        }
        if (!collectionDao.collectionExists(collectionId)) {
            return false
        }
        if (collectionDao.countCollectionsByNameExcludingId(collectionId, trimmedName) > 0) {
            return false
        }
        return collectionDao.renameCollection(collectionId, trimmedName) > 0
    }

    suspend fun deleteCollection(collectionId: String) {
        collectionDao.deleteCollection(collectionId)
    }

    suspend fun reorderCollections(collectionIds: List<String>) {
        collectionDao.updateCollectionSortOrders(collectionIds)
    }

    suspend fun addVideoToCollection(collectionId: String, videoId: String): Boolean {
        if (!collectionDao.collectionExists(collectionId)) {
            return false
        }

        val normalizedVideoId = normalizeVideoId(videoId) ?: return false
        collectionDao.insertCollectionVideo(
            CollectionVideoEntity(
                collectionId = collectionId,
                videoId = normalizedVideoId
            )
        )
        return true
    }

    suspend fun removeVideoFromCollection(collectionId: String, videoId: String) {
        val normalizedVideoId = normalizeVideoId(videoId) ?: return
        collectionDao.removeVideoFromCollection(collectionId, normalizedVideoId)
    }

    suspend fun removeVideoFromAllCollections(videoId: String) {
        val normalizedVideoId = normalizeVideoId(videoId) ?: return
        collectionDao.removeVideoFromAllCollections(normalizedVideoId)
    }

    suspend fun isVideoInAnyCollection(videoId: String): Boolean {
        val normalizedVideoId = normalizeVideoId(videoId) ?: return false
        return collectionDao.isVideoInAnyCollection(normalizedVideoId)
    }

    private fun normalizeVideoIds(videoIds: List<String>): List<String> {
        return videoIds.mapNotNull(::normalizeVideoId).distinct()
    }

    private fun normalizeVideoId(videoId: String): String? {
        val trimmed = videoId.trim()
        if (trimmed.isBlank()) {
            return null
        }
        return YouTubeVideoIdNormalizer.extractVideoId(trimmed) ?: trimmed
    }
}
