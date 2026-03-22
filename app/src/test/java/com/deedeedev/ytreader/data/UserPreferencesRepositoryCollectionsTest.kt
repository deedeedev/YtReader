package com.deedeedev.ytreader.data

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserPreferencesRepositoryCollectionsTest {

    @Test
    fun createCollection_trimsNameAndRejectsBlank() {
        val repository = createRepository()

        assertFalse(repository.createCollection("   "))
        assertTrue(repository.createCollection("  Watch Later  "))

        val collections = repository.videoCollections.value
        assertEquals(1, collections.size)
        assertEquals("Watch Later", collections.first().name)
    }

    @Test
    fun createCollection_rejectsDuplicateNameIgnoringCase() {
        val repository = createRepository()

        assertTrue(repository.createCollection("Favorites"))
        assertFalse(repository.createCollection("favorites"))

        assertEquals(1, repository.videoCollections.value.size)
    }

    @Test
    fun renameCollection_rejectsNameUsedByAnotherCollection() {
        val repository = createRepository()

        assertTrue(repository.createCollection("Favorites"))
        assertTrue(repository.createCollection("Learning"))

        val collections = repository.videoCollections.value
        val learningId = collections.first { it.name == "Learning" }.id

        assertFalse(repository.renameCollection(learningId, "favorites"))

        val renamed = repository.videoCollections.value.first { it.id == learningId }
        assertEquals("Learning", renamed.name)
    }

    @Test
    fun addVideoToCollection_doesNotDuplicateVideoId() {
        val repository = createRepository()
        assertTrue(repository.createCollection("Favorites"))

        val collectionId = repository.videoCollections.value.first().id

        assertTrue(repository.addVideoToCollection(collectionId, "video-1"))
        assertTrue(repository.addVideoToCollection(collectionId, "video-1"))

        val videos = repository.videoCollections.value.first().videoIds
        assertEquals(listOf("video-1"), videos)
    }

    private fun createRepository(initialCollectionsJson: String? = null): UserPreferencesRepository {
        val context = mock<Context>()
        val sharedPreferences = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()
        var storedCollectionsJson = initialCollectionsJson

        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.getStringSet(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            invocation.arguments[1] as Set<String>?
        }
        whenever(sharedPreferences.getFloat(any(), any())).thenAnswer { invocation ->
            invocation.arguments[1] as Float
        }
        whenever(sharedPreferences.getString(any(), anyOrNull())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            val defaultValue = invocation.arguments[1] as String?
            if (key == VIDEO_COLLECTIONS_KEY) {
                storedCollectionsJson
            } else {
                defaultValue
            }
        }

        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putString(any(), anyOrNull())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            val value = invocation.arguments[1] as String?
            if (key == VIDEO_COLLECTIONS_KEY) {
                storedCollectionsJson = value
            }
            editor
        }

        return UserPreferencesRepository(context)
    }

    companion object {
        private const val VIDEO_COLLECTIONS_KEY = "video_collections"
    }
}
