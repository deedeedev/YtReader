package com.deedeedev.ytreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SubtitleEntity::class, HighlightNoteEntity::class], version = 16, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subtitleDao(): SubtitleDao
    abstract fun highlightNoteDao(): HighlightNoteDao

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

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `subtitles` ADD COLUMN `isInLibrary` INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subtitles_isInLibrary` ON `subtitles` (`isInLibrary`)"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `subtitles` ADD COLUMN `readingProgressPercent` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `highlight_notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `subtitleId` INTEGER NOT NULL,
                        `highlightStart` INTEGER NOT NULL,
                        `highlightEnd` INTEGER NOT NULL,
                        `noteText` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`subtitleId`) REFERENCES `subtitles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_highlight_notes_subtitleId` ON `highlight_notes` (`subtitleId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_highlight_notes_subtitleId_highlightStart_highlightEnd` ON `highlight_notes` (`subtitleId`, `highlightStart`, `highlightEnd`)"
                )
            }
        }
    }
}
