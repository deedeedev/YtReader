package com.deedeedev.ytreader

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen

class YtReaderApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        AndroidThreeTen.init(this)
        container = DefaultAppContainer(this)
    }
}
