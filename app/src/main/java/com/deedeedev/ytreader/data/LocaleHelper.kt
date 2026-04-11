package com.deedeedev.ytreader.data

import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.deedeedev.ytreader.ui.AppLanguage
import java.util.Locale

object LocaleHelper {

    fun init(context: Context): Pair<Context, AppLanguage> {
        val prefs = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        val languageValue = prefs.getString(
            "app_language",
            AppLanguage.SYSTEM.storageValue
        ) ?: AppLanguage.SYSTEM.storageValue
        val appLanguage = AppLanguage.fromStorageValue(languageValue)
        val wrappedContext = applyLocale(context, appLanguage)
        return wrappedContext to appLanguage
    }

    fun applyLocale(context: Context, appLanguage: AppLanguage): Context {
        val locale = when (appLanguage) {
            AppLanguage.SYSTEM -> Locale.getDefault()
            AppLanguage.ENGLISH -> Locale.ENGLISH
            AppLanguage.ITALIAN -> Locale.ITALIAN
        }

        Locale.setDefault(locale)

        val config = context.resources.configuration

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return context.createConfigurationContext(config)
    }

    fun wrap(context: Context, appLanguage: AppLanguage): Context {
        return applyLocale(context, appLanguage)
    }
}
