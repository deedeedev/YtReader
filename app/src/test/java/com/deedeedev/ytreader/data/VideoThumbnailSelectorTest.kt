package com.deedeedev.ytreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.Image

class VideoThumbnailSelectorTest {
    @Test
    fun preferredThumbnailUrl_prefersMediumResolutionForCards() {
        val thumbnails = listOf(
            Image("https://example.com/high.jpg", 1080, 1920, Image.ResolutionLevel.HIGH),
            Image("https://example.com/medium.jpg", 360, 640, Image.ResolutionLevel.MEDIUM),
            Image("https://example.com/low.jpg", 90, 160, Image.ResolutionLevel.LOW)
        )

        assertEquals("https://example.com/medium.jpg", preferredThumbnailUrl(thumbnails))
    }

    @Test
    fun preferredThumbnailUrl_returnsNullWhenNoUsableUrlsExist() {
        val thumbnails = listOf(
            Image("", 360, 640, Image.ResolutionLevel.MEDIUM)
        )

        assertNull(preferredThumbnailUrl(thumbnails))
    }
}
