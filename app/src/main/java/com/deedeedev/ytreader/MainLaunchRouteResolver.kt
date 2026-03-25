package com.deedeedev.ytreader

import android.content.Intent
import com.deedeedev.ytreader.ui.Screen

internal fun requestedHomeRouteForIntent(intent: Intent?): String? {
    return if (isSharedTextIntent(intent)) {
        Screen.Search.route
    } else {
        null
    }
}

internal fun isSharedTextIntent(intent: Intent?): Boolean {
    return intent?.action == Intent.ACTION_SEND &&
        intent.type == "text/plain" &&
        !intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank()
}
