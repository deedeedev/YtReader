package com.deedeedev.ytreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubtitleEntity::class,
        SubtitleReadingStateEntity::class,
        AiCleaningStateEntity::class,
        VideoEntity::class,
        HighlightNoteEntity::class,
        BookmarkEntity::class,
        CollectionEntity::class,
        CollectionVideoEntity::class,
        SearchHistoryEntity::class
    ],
    version = 24,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subtitleDao(): SubtitleDao
    abstract fun subtitleReadingStateDao(): SubtitleReadingStateDao
    abstract fun aiCleaningStateDao(): AiCleaningStateDao
    abstract fun videoDao(): VideoDao
    abstract fun highlightNoteDao(): HighlightNoteDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun collectionDao(): CollectionDao
    abstract fun searchHistoryDao(): SearchHistoryDao

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

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bookmarks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `subtitleId` INTEGER NOT NULL,
                        `anchorStart` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`subtitleId`) REFERENCES `subtitles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bookmarks_subtitleId` ON `bookmarks` (`subtitleId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_subtitleId_anchorStart` ON `bookmarks` (`subtitleId`, `anchorStart`)"
                )
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collections` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collection_videos` (
                        `collectionId` TEXT NOT NULL,
                        `videoId` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`collectionId`, `videoId`),
                        FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_videos_collectionId` ON `collection_videos` (`collectionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_videos_videoId` ON `collection_videos` (`videoId`)"
                )
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `subtitles` ADD COLUMN `currentPage` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `subtitles` ADD COLUMN `totalPages` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `videos` (
                        `videoId` TEXT NOT NULL,
                        `videoUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `uploadDate` INTEGER NOT NULL,
                        `thumbnailLocalPath` TEXT,
                        `thumbnailSourceUrl` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `videos` (
                        `videoId`,
                        `videoUrl`,
                        `title`,
                        `channelName`,
                        `uploadDate`,
                        `thumbnailLocalPath`,
                        `thumbnailSourceUrl`,
                        `updatedAt`
                    )
                    SELECT
                        agg.videoId,
                        COALESCE((
                            SELECT s.videoUrl
                            FROM subtitles s
                            WHERE s.videoId = agg.videoId
                            ORDER BY s.lastOpenedAt DESC, s.createdAt DESC, s.id DESC
                            LIMIT 1
                        ), ''),
                        COALESCE((
                            SELECT s.title
                            FROM subtitles s
                            WHERE s.videoId = agg.videoId
                            ORDER BY s.lastOpenedAt DESC, s.createdAt DESC, s.id DESC
                            LIMIT 1
                        ), ''),
                        COALESCE((
                            SELECT s.channelName
                            FROM subtitles s
                            WHERE s.videoId = agg.videoId
                            ORDER BY s.lastOpenedAt DESC, s.createdAt DESC, s.id DESC
                            LIMIT 1
                        ), ''),
                        COALESCE((
                            SELECT s.uploadDate
                            FROM subtitles s
                            WHERE s.videoId = agg.videoId
                            ORDER BY s.lastOpenedAt DESC, s.createdAt DESC, s.id DESC
                            LIMIT 1
                        ), 0),
                        NULL,
                        NULL,
                        agg.updatedAt
                    FROM (
                        SELECT videoId, MAX(createdAt) AS updatedAt
                        FROM subtitles
                        GROUP BY videoId
                    ) agg
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `collections` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    UPDATE `collections`
                    SET `sortOrder` = (
                        SELECT COUNT(*)
                        FROM `collections` AS c2
                        WHERE c2.`createdAt` > `collections`.`createdAt`
                            OR (
                                c2.`createdAt` = `collections`.`createdAt`
                                AND c2.`name` COLLATE NOCASE < `collections`.`name` COLLATE NOCASE
                            )
                            OR (
                                c2.`createdAt` = `collections`.`createdAt`
                                AND c2.`name` = `collections`.`name`
                                AND c2.`id` < `collections`.`id`
                            )
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `search_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `url` TEXT NOT NULL,
                        `videoTitle` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `searchedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_url` ON `search_history` (`url`)"
                )
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE subtitles ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `subtitle_reading_states` (
                        `subtitleId` INTEGER NOT NULL PRIMARY KEY,
                        `lastTimestamp` INTEGER NOT NULL DEFAULT 0,
                        `lastOpenedAt` INTEGER NOT NULL DEFAULT 0,
                        `readingProgressPercent` INTEGER NOT NULL DEFAULT 0,
                        `isRead` INTEGER NOT NULL DEFAULT 0,
                        `currentPage` INTEGER NOT NULL DEFAULT 0,
                        `totalPages` INTEGER NOT NULL DEFAULT 0,
                        `lastStudyScroll` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`subtitleId`) REFERENCES `subtitles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_cleaning_states` (
                        `subtitleId` INTEGER NOT NULL PRIMARY KEY,
                        `aiCleaningInProgress` INTEGER NOT NULL DEFAULT 0,
                        `aiCleaningSourceText` TEXT,
                        `aiCleaningPendingResult` TEXT,
                        `aiCleaningErrorSummary` TEXT,
                        `aiCleaningErrorLog` TEXT,
                        `aiCleaningUpdatedAt` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`subtitleId`) REFERENCES `subtitles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `subtitle_reading_states` (
                        subtitleId, lastTimestamp, lastOpenedAt,
                        readingProgressPercent, isRead, currentPage, totalPages, lastStudyScroll
                    )
                    SELECT id, lastTimestamp, lastOpenedAt,
                        readingProgressPercent, isRead, currentPage, totalPages, lastStudyScroll
                    FROM subtitles
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `ai_cleaning_states` (
                        subtitleId, aiCleaningInProgress, aiCleaningSourceText,
                        aiCleaningPendingResult, aiCleaningErrorSummary,
                        aiCleaningErrorLog, aiCleaningUpdatedAt
                    )
                    SELECT id, aiCleaningInProgress, aiCleaningSourceText,
                        aiCleaningPendingResult, aiCleaningErrorSummary,
                        aiCleaningErrorLog, aiCleaningUpdatedAt
                    FROM subtitles
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subtitle_reading_states_lastOpenedAt` ON `subtitle_reading_states` (`lastOpenedAt`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `subtitles_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `videoId` TEXT NOT NULL,
                        `videoUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `languageCode` TEXT NOT NULL,
                        `subtitleTrackId` TEXT,
                        `isAutoGenerated` INTEGER NOT NULL,
                        `trackIdentity` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `studyContent` TEXT,
                        `isInLibrary` INTEGER NOT NULL,
                        `fontSize` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `uploadDate` INTEGER NOT NULL,
                        `fontFamily` TEXT NOT NULL,
                        `highlights` TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `subtitles_new` (
                        id, videoId, videoUrl, title, channelName, languageCode,
                        subtitleTrackId, isAutoGenerated, trackIdentity, content,
                        studyContent, isInLibrary, fontSize, createdAt, uploadDate,
                        fontFamily, highlights
                    )
                    SELECT
                        id, videoId, videoUrl, title, channelName, languageCode,
                        subtitleTrackId, isAutoGenerated, trackIdentity, content,
                        studyContent, isInLibrary, fontSize, createdAt, uploadDate,
                        fontFamily, highlights
                    FROM subtitles
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE subtitles")
                db.execSQL("ALTER TABLE subtitles_new RENAME TO subtitles")

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subtitles_videoId_trackIdentity` ON `subtitles` (`videoId`, `trackIdentity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitles_createdAt` ON `subtitles` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitles_isInLibrary` ON `subtitles` (`isInLibrary`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitles_channelName` ON `subtitles` (`channelName`)")
            }
        }
    }
}