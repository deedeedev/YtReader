# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-dontwarn sun.misc.**

# Gson models — UserPreferencesRepository
-keep class com.deedeedev.ytreader.data.PersistedLibraryFilters { *; }
-keep class com.deedeedev.ytreader.data.PersistedCollectionFilters { *; }
-keep class com.deedeedev.ytreader.data.VideoCollection { *; }
-keep class com.deedeedev.ytreader.data.PreferencesBackup { *; }

# Gson models — AiCleaningRepository (private inner classes)
-keep class com.deedeedev.ytreader.data.AiCleaningRepository$* { *; }
-keep class com.deedeedev.ytreader.data.AiCleaningRequest { *; }
-keep class com.deedeedev.ytreader.data.AiCleaningFailure { *; }

# Gson models — SettingsBackupManager
-keep class com.deedeedev.ytreader.ui.settings.DataBackupManifest { *; }

# NewPipe Extractor — keep public API (reflection-heavy)
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Mozilla Rhino (JS engine used by extractor)
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# nanojson (used by extractor)
-keep class com.grack.nanojson.** { *; }
