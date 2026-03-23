package com.deedeedev.ytreader.ui.reader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.deedeedev.ytreader.data.UserPreferencesRepository

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true
    }
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

internal fun openAppNotificationSettings(context: Context) {
    val notificationSettingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val appDetailsIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(notificationSettingsIntent)
    }.recoverCatching {
        context.startActivity(appDetailsIntent)
    }
}

internal fun applyReaderBrightness(activity: Activity?, brightness: Float) {
    val window = activity?.window ?: return
    val params = window.attributes
    params.screenBrightness = brightness.coerceIn(MIN_READER_BRIGHTNESS, 1f)
    window.attributes = params
}

internal fun currentEffectiveBrightness(
    activity: Activity?,
    appBrightnessPreference: Float
): Float {
    if (appBrightnessPreference != UserPreferencesRepository.BRIGHTNESS_FOLLOW_SYSTEM) {
        return appBrightnessPreference.coerceIn(MIN_READER_BRIGHTNESS, 1f)
    }
    val windowBrightness = activity?.window?.attributes?.screenBrightness
        ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    return if (windowBrightness >= 0f) {
        windowBrightness.coerceIn(MIN_READER_BRIGHTNESS, 1f)
    } else {
        DEFAULT_GESTURE_BRIGHTNESS
    }
}
