package com.deedeedev.ytreader

import android.content.Context
import androidx.room.Room
import com.deedeedev.ytreader.data.CollectionRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.YoutubeRepository
import com.deedeedev.ytreader.data.AiCleaningRepository
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.AppDatabase
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.CollectionDao
import com.deedeedev.ytreader.data.local.SearchHistoryDao
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.VideoDao
import com.deedeedev.ytreader.data.remote.NewPipeDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

interface AppContainer {
    val appContext: Context
    val subtitleDao: SubtitleDao
    val videoDao: VideoDao
    val highlightNoteDao: HighlightNoteDao
    val bookmarkDao: BookmarkDao
    val collectionDao: CollectionDao
    val searchHistoryDao: SearchHistoryDao
    val youtubeRepository: YoutubeRepository
    val userPreferencesRepository: UserPreferencesRepository
    val collectionRepository: CollectionRepository
    val aiCleaningRepository: AiCleaningRepository
    fun closeDatabase()
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val appContext: Context
        get() = context.applicationContext

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "ytreader.db"
        )
            .addMigrations(AppDatabase.MIGRATION_12_13)
            .addMigrations(AppDatabase.MIGRATION_13_14)
            .addMigrations(AppDatabase.MIGRATION_14_15)
            .addMigrations(AppDatabase.MIGRATION_15_16)
            .addMigrations(AppDatabase.MIGRATION_16_17)
            .addMigrations(AppDatabase.MIGRATION_17_18)
            .addMigrations(AppDatabase.MIGRATION_18_19)
            .addMigrations(AppDatabase.MIGRATION_19_20)
            .addMigrations(AppDatabase.MIGRATION_20_21)
            .addMigrations(AppDatabase.MIGRATION_21_22)
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
        NewPipe.init(NewPipeDownloader(context.applicationContext, okHttpClient))
    }

    override val subtitleDao: SubtitleDao by lazy {
        database.subtitleDao()
    }

    override val highlightNoteDao: HighlightNoteDao by lazy {
        database.highlightNoteDao()
    }

    override val videoDao: VideoDao by lazy {
        database.videoDao()
    }

    override val bookmarkDao: BookmarkDao by lazy {
        database.bookmarkDao()
    }

    override val collectionDao: CollectionDao by lazy {
        database.collectionDao()
    }

    override val searchHistoryDao: SearchHistoryDao by lazy {
        database.searchHistoryDao()
    }

    override val youtubeRepository: YoutubeRepository by lazy {
        YoutubeRepository(context.applicationContext, okHttpClient)
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    override val collectionRepository: CollectionRepository by lazy {
        CollectionRepository(collectionDao, userPreferencesRepository).also { repository ->
            runBlocking {
                repository.migrateLegacyCollectionsIfNeeded()
            }
        }
    }

    override val aiCleaningRepository: AiCleaningRepository by lazy {
        AiCleaningRepository(context.applicationContext, aiOkHttpClient)
    }

    override fun closeDatabase() {
        database.close()
    }
}
