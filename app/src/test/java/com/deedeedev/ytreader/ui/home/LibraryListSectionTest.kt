package com.deedeedev.ytreader.ui.home

import com.deedeedev.ytreader.data.local.SubtitleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryListSectionTest {

    @Test
    fun collectionEmptyText_returnsEmptyCollectionMessage_whenCollectionHasNoVideos() {
        val text = collectionEmptyText(totalCollectionVideoCount = 0, selectedChannelFilter = null)

        assertEquals("No videos in this collection.", text)
    }

    @Test
    fun collectionEmptyText_returnsFilteredMessage_whenFilterIsSelected() {
        val text = collectionEmptyText(totalCollectionVideoCount = 5, selectedChannelFilter = "Channel A")

        assertEquals("No videos for this channel.", text)
    }

    @Test
    fun collectionEmptyText_returnsDefaultCollectionMessage_whenUnfilteredAndNonEmpty() {
        val text = collectionEmptyText(totalCollectionVideoCount = 5, selectedChannelFilter = null)

        assertEquals("No videos found in this collection.", text)
    }

    @Test
    fun sortLabel_returnsExpectedLabels_forEverySortOption() {
        assertEquals("Title", sortLabel(SortOption.TITLE))
        assertEquals("Channel Name", sortLabel(SortOption.CHANNEL_NAME))
        assertEquals("Date Published", sortLabel(SortOption.DATE_PUBLISHED))
        assertEquals("Downloaded", sortLabel(SortOption.DOWNLOADED))
        assertEquals("Last opened", sortLabel(SortOption.LAST_OPENED))
    }

    @Test
    fun visibilityLabel_returnsExpectedLabels_forEveryFilter() {
        assertEquals("All", visibilityLabel(LibraryVisibilityFilter.ALL))
        assertEquals(
            "Only not in collections",
            visibilityLabel(LibraryVisibilityFilter.NOT_IN_COLLECTIONS)
        )
        assertEquals(
            "Only in collections",
            visibilityLabel(LibraryVisibilityFilter.IN_COLLECTIONS)
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
}
