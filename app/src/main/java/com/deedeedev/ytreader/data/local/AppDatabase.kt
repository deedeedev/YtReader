package com.deedeedev.ytreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SubtitleEntity::class], version = 13, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subtitleDao(): SubtitleDao

    companion object {
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subtitles_createdAt` ON `subtitles` (`createdAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subtitles_lastOpenedAt` ON `subtitles` (`lastOpenedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subtitles_channelName` ON `subtitles` (`channelName`)"
                )
            }
        }
    }
}
