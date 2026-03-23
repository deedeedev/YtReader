package com.deedeedev.ytreader.ui.home

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
}
