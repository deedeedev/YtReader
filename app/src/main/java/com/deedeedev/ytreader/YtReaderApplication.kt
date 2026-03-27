package com.deedeedev.ytreader

import android.app.Application
import com.deedeedev.ytreader.data.createAiCleaningNotificationChannel
import com.deedeedev.ytreader.data.createAutoBackupNotificationChannel
import com.jakewharton.threetenabp.AndroidThreeTen

class YtReaderApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        AndroidThreeTen.init(this)

        container = DefaultAppContainer(this)
        createAiCleaningNotificationChannel(this)
        createAutoBackupNotificationChannel(this)
    }
}
