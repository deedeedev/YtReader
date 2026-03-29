package com.deedeedev.ytreader.ui

import androidx.annotation.StringRes
import com.deedeedev.ytreader.R

enum class AppLanguage(val languageCode: String, val storageValue: String, @param:StringRes val labelRes: Int) {
    SYSTEM("", "system", R.string.language_system),
    ENGLISH("en", "en", R.string.language_english),
    ITALIAN("it", "it", R.string.language_italian);

    companion object {
        fun fromStorageValue(value: String?): AppLanguage {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}
