package com.deedeedev.ytreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoThumbnailStoreTest {
    @Test
    fun buildFileName_sanitizesVideoIdAndPreservesKnownExtension() {
        assertEquals("video_id_1.webp", VideoThumbnailStore.buildFileName("video/id?1", "webp"))
    }

    @Test
    fun extensionForUrl_fallsBackForUnknownExtensions() {
        assertEquals("img", VideoThumbnailStore.extensionForUrl("https://example.com/thumb.bin?x=1"))
    }
}
