package com.deedeedev.ytreader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.io.IOException

class YoutubeRepository(private val client: OkHttpClient) {
    suspend fun getStreamInfo(url: String): StreamInfo = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        StreamInfo.getInfo(service, url)
    }

    suspend fun downloadSubtitle(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            response.body?.string() ?: ""
        }
    }
}
