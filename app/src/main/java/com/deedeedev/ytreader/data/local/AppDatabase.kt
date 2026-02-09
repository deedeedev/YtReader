package com.deedeedev.ytreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SubtitleEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subtitleDao(): SubtitleDao
}
