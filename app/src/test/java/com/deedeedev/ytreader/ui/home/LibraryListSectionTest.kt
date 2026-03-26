package com.deedeedev.ytreader.ui.home

import android.content.res.Resources
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.local.SubtitleEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LibraryListSectionTest {

    private val resources = mockResources()

    @Test
    fun collectionEmptyText_returnsEmptyCollectionMessage_whenCollectionHasNoVideos() {
        val text = collectionEmptyText(resources, totalCollectionVideoCount = 0, selectedChannelFilter = null)

        assertEquals("No videos in this collection.", text)
    }

    @Test
    fun collectionEmptyText_returnsFilteredMessage_whenFilterIsSelected() {
        val text = collectionEmptyText(resources, totalCollectionVideoCount = 5, selectedChannelFilter = "Channel A")

        assertEquals("No videos for this channel.", text)
    }

    @Test
    fun collectionEmptyText_returnsDefaultCollectionMessage_whenUnfilteredAndNonEmpty() {
        val text = collectionEmptyText(resources, totalCollectionVideoCount = 5, selectedChannelFilter = null)

        assertEquals("No videos found in this collection.", text)
    }

    @Test
    fun sortLabel_returnsExpectedLabels_forEverySortOption() {
        assertEquals("Title", sortLabel(resources, SortOption.TITLE))
        assertEquals("Channel Name", sortLabel(resources, SortOption.CHANNEL_NAME))
        assertEquals("Date Published", sortLabel(resources, SortOption.DATE_PUBLISHED))
        assertEquals("Downloaded", sortLabel(resources, SortOption.DOWNLOADED))
        assertEquals("Last opened", sortLabel(resources, SortOption.LAST_OPENED))
    }

    @Test
    fun visibilityLabel_returnsExpectedLabels_forEveryFilter() {
        assertEquals("All", visibilityLabel(resources, LibraryVisibilityFilter.ALL))
        assertEquals(
            "Only not in collections",
            visibilityLabel(resources, LibraryVisibilityFilter.NOT_IN_COLLECTIONS)
        )
        assertEquals(
            "Only in collections",
            visibilityLabel(resources, LibraryVisibilityFilter.IN_COLLECTIONS)
        )
    }

    @Test
    fun filterByVisibility_filtersCollectionMembershipAsExpected() {
        val notInCollection = libraryItem(videoId = "video-1", collectionCount = 0)
        val inCollection = libraryItem(videoId = "video-2", collectionCount = 2)
        val items = listOf(notInCollection, inCollection)

        assertEquals(items, items.filterByVisibility(LibraryVisibilityFilter.ALL))
        assertEquals(
            listOf(notInCollection),
            items.filterByVisibility(LibraryVisibilityFilter.NOT_IN_COLLECTIONS)
        )
        assertEquals(
            listOf(inCollection),
            items.filterByVisibility(LibraryVisibilityFilter.IN_COLLECTIONS)
        )
    }

    private fun libraryItem(videoId: String, collectionCount: Int): LibraryItem {
        return LibraryItem(
            videoId = videoId,
            videoUrl = "https://www.youtube.com/watch?v=$videoId",
            title = "Video $videoId",
            channelName = "Channel",
            subtitles = listOf(
                SubtitleEntity(
                    id = 1,
                    videoId = videoId,
                    title = "Video $videoId",
                    languageCode = "en",
                    content = "subtitle"
                )
            ),
            uploadDate = 0L,
            lastDownloaded = 0L,
            lastOpenedAt = 0L,
            readingProgressPercent = 0,
            isInLibrary = true,
            collectionCount = collectionCount
        )
    }

    private fun mockResources(): Resources {
        val resources = mock<Resources>()
        whenever(resources.getString(R.string.library_empty_collection))
            .thenReturn("No videos in this collection.")
        whenever(resources.getString(R.string.library_empty_collection_channel))
            .thenReturn("No videos for this channel.")
        whenever(resources.getString(R.string.library_empty_collection_filtered))
            .thenReturn("No videos found in this collection.")
        whenever(resources.getString(R.string.library_sort_title)).thenReturn("Title")
        whenever(resources.getString(R.string.library_sort_channel_name)).thenReturn("Channel Name")
        whenever(resources.getString(R.string.library_sort_date_published)).thenReturn("Date Published")
        whenever(resources.getString(R.string.library_sort_downloaded)).thenReturn("Downloaded")
        whenever(resources.getString(R.string.library_sort_last_opened)).thenReturn("Last opened")
        whenever(resources.getString(R.string.library_visibility_all)).thenReturn("All")
        whenever(resources.getString(R.string.library_visibility_not_in_collections))
            .thenReturn("Only not in collections")
        whenever(resources.getString(R.string.library_visibility_in_collections))
            .thenReturn("Only in collections")
        return resources
    }
}
