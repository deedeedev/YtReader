package com.deedeedev.ytreader

import android.content.Context
import androidx.room.Room
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.local.AppDatabase
import com.deedeedev.ytreader.data.local.SubtitleDao

import com.deedeedev.ytreader.data.remote.NewPipeDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe

interface AppContainer {
    val subtitleDao: SubtitleDao
    val youtubeRepository: YoutubeRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "ytreader.db"
        ).build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    init {
        NewPipe.init(NewPipeDownloader(okHttpClient))
    }

    override val subtitleDao: SubtitleDao by lazy {
        database.subtitleDao()
    }

    override val youtubeRepository: YoutubeRepository by lazy {
        YoutubeRepository(okHttpClient)
    }
}
