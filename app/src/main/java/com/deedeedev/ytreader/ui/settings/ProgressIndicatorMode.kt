package com.deedeedev.ytreader.ui.settings

import androidx.annotation.StringRes
import com.deedeedev.ytreader.R

enum class ProgressIndicatorMode(val storageValue: String, @get:StringRes val labelRes: Int) {
    ALWAYS_VISIBLE("always_visible", R.string.progress_indicator_always_visible),
    FADE_OUT("fade_out", R.string.progress_indicator_fade_out);

    companion object {
        fun fromStorageValue(value: String): ProgressIndicatorMode {
            return entries.firstOrNull { it.storageValue == value } ?: FADE_OUT
        }
    }
}