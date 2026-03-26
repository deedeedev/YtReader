package com.deedeedev.ytreader.ui

import android.content.Context
import androidx.annotation.StringRes
import com.deedeedev.ytreader.R

enum class FontOption(val storageValue: String, @StringRes val labelRes: Int) {
    DEFAULT("Default", R.string.font_family_default),
    SERIF("Serif", R.string.font_family_serif),
    SANS_SERIF("SansSerif", R.string.font_family_sans_serif),
    MONOSPACE("Monospace", R.string.font_family_monospace),
    CURSIVE("Cursive", R.string.font_family_cursive);

    companion object {
        fun fromStorageValue(value: String): FontOption {
            return entries.firstOrNull { it.storageValue == value } ?: DEFAULT
        }

        fun labels(context: Context, includeCursive: Boolean = true): List<Pair<String, String>> {
            val options = if (includeCursive) entries else entries.filterNot { it == CURSIVE }
            return options.map { it.storageValue to context.getString(it.labelRes) }
        }
    }
}
