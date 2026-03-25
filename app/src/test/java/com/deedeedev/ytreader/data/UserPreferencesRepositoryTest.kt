package com.deedeedev.ytreader.data

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserPreferencesRepositoryTest {

    @Test
    fun exportAndImportPreferences_roundTripsCurrentValues() {
        val repository = createRepository()

        repository.toggleFavoriteLanguage("en")
        repository.setDefaultFontSize(22f)
        repository.setFontFamily("Serif")
        repository.setLineHeightMultiplier(1.8f)
        repository.setAppBrightness(0.7f)
        repository.setAiEndpoint("https://example.com")
        repository.setAiApiKey("secret")
        repository.setAiModel("gpt-test")
        repository.setAiPrompt("Clean this")

        val json = repository.exportPreferencesJson()

        val restoredRepository = createRepository()
        assertTrue(restoredRepository.importPreferencesJson(json))
        assertEquals(setOf("en"), restoredRepository.favoriteLanguages.value)
        assertEquals(22f, restoredRepository.defaultFontSize.value)
        assertEquals("Serif", restoredRepository.fontFamily.value)
        assertEquals(1.8f, restoredRepository.lineHeightMultiplier.value)
        assertEquals(0.7f, restoredRepository.appBrightness.value)
        assertEquals("https://example.com", restoredRepository.aiEndpoint.value)
        assertEquals("secret", restoredRepository.aiApiKey.value)
        assertEquals("gpt-test", restoredRepository.aiModel.value)
        assertEquals("Clean this", restoredRepository.aiPrompt.value)
    }

    @Test
    fun importPreferencesJson_rejectsInvalidJson() {
        val repository = createRepository()

        assertFalse(repository.importPreferencesJson("not-json"))
    }

    @Test
    fun parseLegacyCollectionsJson_normalizesVideoIds() {
        val collections = UserPreferencesRepository.parseLegacyCollectionsJson(
            """
            [
              {
                "id": "collection-1",
                "name": " Favorites ",
                "videoIds": [
                  "https://youtu.be/dQw4w9WgXcQ?t=1",
                  "dQw4w9WgXcQ",
                  "https://www.youtube.com/watch?v=9bZkp7q19f0"
                ],
                "createdAt": 1
              }
            ]
            """.trimIndent()
        )

        assertEquals(1, collections.size)
        assertEquals("Favorites", collections.first().name)
        assertEquals(listOf("dQw4w9WgXcQ", "9bZkp7q19f0"), collections.first().videoIds)
    }

    private fun createRepository(): UserPreferencesRepository {
        val context = mock<Context>()
        val sharedPreferences = FakeSharedPreferences()

        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)

        return UserPreferencesRepository(context)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values

        override fun getString(key: String?, defValue: String?): String? {
            return values[key] as? String ?: defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return ((values[key] as? Set<String>)?.toMutableSet() ?: defValues)
        }

        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor(values)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(
            private val values: MutableMap<String, Any?>
        ) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = values?.toSet()
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                removals += key.orEmpty()
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearRequested = true
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearRequested) {
                    values.clear()
                }
                removals.forEach(values::remove)
                values.putAll(pending)
            }
        }
    }
}
