package com.deedeedev.ytreader.data

import android.content.Context
import com.deedeedev.ytreader.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.io.IOException

class YoutubeRepository(
    private val context: Context,
    private val client: OkHttpClient
) {
    suspend fun getStreamInfo(url: String): StreamInfo = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        StreamInfo.getInfo(service, url)
    }

    suspend fun downloadSubtitle(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    context.getString(R.string.youtube_unexpected_code, response.toString())
                )
            }
            response.body?.string() ?: ""
        }
    }

    suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    context.getString(R.string.youtube_unexpected_code, response.toString())
                )
            }
            response.body?.bytes() ?: ByteArray(0)
        }
    }
}
