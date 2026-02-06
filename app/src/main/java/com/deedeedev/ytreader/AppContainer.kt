package com.deedeedev.ytreader

import android.content.Context

interface AppContainer {
    // Dependencies will be added here
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    // Implementation of dependencies
}
