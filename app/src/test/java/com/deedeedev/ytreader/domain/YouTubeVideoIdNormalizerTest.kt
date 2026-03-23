package com.deedeedev.ytreader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeVideoIdNormalizerTest {

    @Test
    fun extractVideoId_supportsCommonYoutubeUrlVariants() {
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeVideoIdNormalizer.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeVideoIdNormalizer.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=43")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeVideoIdNormalizer.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ?feature=share")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeVideoIdNormalizer.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ&si=abc")
        )
    }

    @Test
    fun canonicalize_returnsCanonicalWatchUrlWhenVideoIdIsParseable() {
        val canonical = YouTubeVideoIdNormalizer.canonicalize("https://youtu.be/dQw4w9WgXcQ?t=1")

        assertEquals("dQw4w9WgXcQ", canonical.videoId)
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            canonical.videoUrl
        )
    }

    @Test
    fun extractVideoId_returnsNullForInvalidInput() {
        assertNull(YouTubeVideoIdNormalizer.extractVideoId(""))
        assertNull(YouTubeVideoIdNormalizer.extractVideoId("https://youtube.com/playlist?list=abc"))
        assertNull(YouTubeVideoIdNormalizer.extractVideoId("not a url"))
    }
}
