package com.deedeedev.ytreader.ui.theme

enum class AppTheme(val storageValue: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    DARK_AMOLED("dark_amoled", "Dark (AMOLED)");

    companion object {
        fun fromStorageValue(value: String?): AppTheme {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}
