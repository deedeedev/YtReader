package com.deedeedev.ytreader

import android.app.Application
import android.content.Context
import com.deedeedev.ytreader.data.LocaleHelper
import com.deedeedev.ytreader.data.createAiCleaningNotificationChannel
import com.deedeedev.ytreader.data.createAutoBackupNotificationChannel
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YtReaderApplication : Application() {
    lateinit var container: AppContainer

    override fun attachBaseContext(base: Context) {
        val (wrappedContext, _) = LocaleHelper.init(base)
        super.attachBaseContext(wrappedContext)
    }

    override fun onCreate() {
        super.onCreate()
        AndroidThreeTen.init(this)

        container = DefaultAppContainer(this)
        createAiCleaningNotificationChannel(this)
        createAutoBackupNotificationChannel(this)

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        appScope.launch {
            container.runMigrations()
        }
    }
}
