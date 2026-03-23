package com.deedeedev.ytreader

import android.content.Context
import androidx.room.Room
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.AiCleaningRepository
import com.deedeedev.ytreader.data.local.AppDatabase
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.remote.NewPipeDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.TimeUnit

interface AppContainer {
    val subtitleDao: SubtitleDao
    val youtubeRepository: YoutubeRepository
    val userPreferencesRepository: UserPreferencesRepository
    val aiCleaningRepository: AiCleaningRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "ytreader.db"
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    private val aiOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build()
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

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    override val aiCleaningRepository: AiCleaningRepository by lazy {
        AiCleaningRepository(aiOkHttpClient)
    }
}
