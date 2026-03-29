package com.deedeedev.ytreader

import android.app.Application
import android.content.Context
import com.deedeedev.ytreader.data.LocaleHelper
import com.deedeedev.ytreader.data.createAiCleaningNotificationChannel
import com.deedeedev.ytreader.data.createAutoBackupNotificationChannel
import com.deedeedev.ytreader.ui.AppLanguage
import com.jakewharton.threetenabp.AndroidThreeTen

class YtReaderApplication : Application() {
    lateinit var container: AppContainer

    override fun attachBaseContext(base: Context) {
        val prefs = base.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        val languageValue = prefs.getString("app_language", AppLanguage.SYSTEM.storageValue) ?: AppLanguage.SYSTEM.storageValue
        val appLanguage = AppLanguage.fromStorageValue(languageValue)
        val wrappedContext = LocaleHelper.wrap(base, appLanguage)
        super.attachBaseContext(wrappedContext)
    }

    override fun onCreate() {
        super.onCreate()
        AndroidThreeTen.init(this)

        container = DefaultAppContainer(this)
        createAiCleaningNotificationChannel(this)
        createAutoBackupNotificationChannel(this)
    }
}
