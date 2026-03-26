package com.deedeedev.ytreader.ui.theme

import androidx.annotation.StringRes
import com.deedeedev.ytreader.R

enum class AppTheme(val storageValue: String, @StringRes val labelRes: Int) {
    SYSTEM("system", R.string.theme_system),
    LIGHT("light", R.string.theme_light),
    DARK("dark", R.string.theme_dark),
    DARK_AMOLED("dark_amoled", R.string.theme_amoled_dark);

    companion object {
        fun fromStorageValue(value: String?): AppTheme {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}
