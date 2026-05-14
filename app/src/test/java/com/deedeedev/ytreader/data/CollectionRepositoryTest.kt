package com.deedeedev.ytreader.data

import com.deedeedev.ytreader.data.local.CollectionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CollectionRepositoryTest {
    private val collectionDao = mock<CollectionDao>()
    private val userPreferencesRepository = mock<UserPreferencesRepository>()
    private val subtitleRepository = mock<SubtitleRepository>()
    private val repository = CollectionRepository(collectionDao, userPreferencesRepository, subtitleRepository)

    @Test
    fun createCollection_assignsNextSortOrder() = runTest {
        whenever(collectionDao.countCollectionsByName("Watch later")).thenReturn(0)
        whenever(collectionDao.nextCollectionSortOrder()).thenReturn(7)

        repository.createCollection("Watch later")

        verify(collectionDao).insertCollection(
            org.mockito.kotlin.check { collection ->
                assertEquals("Watch later", collection.name)
                assertEquals(7, collection.sortOrder)
            }
        )
    }

    @Test
    fun createCollection_skipsBlankNames() = runTest {
        repository.createCollection("   ")

        verify(collectionDao, never()).insertCollection(any())
    }

    @Test
    fun reorderCollections_updatesPersistedOrder() = runTest {
        val orderedIds = listOf("c3", "c1", "c2")

        repository.reorderCollections(orderedIds)

        verify(collectionDao).updateCollectionSortOrders(eq(orderedIds))
    }
}
