package com.deedeedev.ytreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubtitleEntity::class,
        VideoEntity::class,
        HighlightNoteEntity::class,
        BookmarkEntity::class,
        CollectionEntity::class,
        CollectionVideoEntity::class
    ],
    version = 21,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subtitleDao(): SubtitleDao
    abstract fun videoDao(): VideoDao
    abstract fun highlightNoteDao(): HighlightNoteDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun collectionDao(): CollectionDao

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
    }
}
