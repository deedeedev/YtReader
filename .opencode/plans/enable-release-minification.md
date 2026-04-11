# Plan: Enable R8 Minification for Release Builds

## Problem

Release APKs are built with `isMinifyEnabled = false` (`app/build.gradle.kts:27`), shipping full unoptimized bytecode. This results in larger APK size and forgoes runtime performance benefits from code shrinking and optimization.

## Current State

- **`app/build.gradle.kts`**: `isMinifyEnabled = false`, no `shrinkResources` set
- **`app/proguard-rules.pro`**: Default template — all rules are commented out
- **Extractor module**: No `consumerProguardFiles` (it's a plain `java-library`, not an Android library, so it can't ship consumer rules)
- **Room**: 7 entities, 6 DAOs, 2 query result classes (`LibraryVideoRow`, `CollectionWithVideos`), `AppDatabase`
- **Gson**: Used in `UserPreferencesRepository`, `AiCleaningRepository`, `SettingsBackupManager`, `VideoNotesExport`
- **NewPipe Extractor**: Heavy use of reflection; 9+ classes imported from `org.schabi.newpipe.extractor.*`

## Plan

### Step 1: Enable minification in release build type

**File:** `app/build.gradle.kts`

- Set `isMinifyEnabled = true`
- Add `isShrinkResources = true` (removes unused resources — only works when minification is enabled)
- Keep existing `proguardFiles` configuration

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

### Step 2: Add R8 keep rules in `proguard-rules.pro`

**File:** `app/proguard-rules.pro`

The rules file needs keep rules for four categories:

#### 2a. Debug crash logs — preserve source file names and line numbers

Uncomment the existing template lines:

```
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
```

This keeps line numbers in stack traces while renaming the source file name, providing readable crash logs without exposing internal class names.

#### 2b. Room entities, DAOs, and database classes

Room uses annotation processing and generates code that accesses entities/DAOs by name. Keep all Room-annotated classes and the database class.

```
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**
```

This covers all 7 entities, 6 DAOs, `AppDatabase`, and the two query result classes (`LibraryVideoRow`, `CollectionWithVideos` — these are returned by DAO queries so Room needs their structure preserved).

#### 2c. Gson-serialized model classes

Gson uses reflection to serialize/deserialize by field name. Two approaches, used together:

1. **Generic Gson rules** to handle serialization of generic types and `@SerializedName`:
   ```
   # Gson
   -keepattributes Signature
   -keepattributes *Annotation*
   -keep class com.google.gson.** { *; }
   -dontwarn sun.misc.**
   ```

2. **Specific model classes** that are serialized with Gson (add `@Keep` annotation or explicit `-keep` rules):

   Public models that need explicit keeps:
   - `PersistedLibraryFilters` (in `UserPreferencesRepository.kt`)
   - `PersistedCollectionFilters` (in `UserPreferencesRepository.kt`)
   - `VideoCollection` (in `UserPreferencesRepository.kt`)
   - `PreferencesBackup` (in `UserPreferencesRepository.kt`)
   - `AiCleaningRequest` (in `AiCleaningRepository.kt`)
   - `AiCleaningFailure` (in `AiCleaningFailure.kt`)
   - `DataBackupManifest` (in `SettingsBackupManager.kt`) — already uses `@SerializedName`

   Private inner classes in `AiCleaningRepository`:
   - `ChatCompletionsRequest`
   - `ChatMessage`
   - `ChatCompletionsResponse`
   - `ChatChoice`
   - `ChatMessageContent`

   Recommended approach — add explicit keep rules:
   ```
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
   ```

   Alternatively, annotate each model class with `@androidx.annotation.Keep` instead of listing them in proguard-rules.pro. The `@Keep` approach is more maintainable since the annotation lives next to the code and won't go stale if classes are renamed/moved.

#### 2d. NewPipe Extractor classes

The extractor is a plain Java library that relies heavily on reflection (JSON parsing, service instantiation, Jsoup, Rhino JavaScript engine). Since it has no `consumerProguardFiles`, all keep rules must be at the app level.

```
# NewPipe Extractor — keep public API (reflection-heavy)
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Mozilla Rhino (JS engine used by extractor)
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# nanojson (used by extractor)
-keep class com.grack.nanojson.** { *; }
```

### Step 3: Build and verify

1. **Build release APK**: `./gradlew :app:assembleRelease`
2. **Check for R8 warnings/errors**: Review build output for any missing keep rules
3. **Verify APK size reduction**: Compare release APK size before and after
4. **Test critical paths**:
   - Launch app and navigate through all screens
   - Search for a video and download subtitles
   - Open reader and verify subtitle display
   - Test backup/restore in settings (Gson serialization)
   - Test AI cleaning feature (Gson API payloads)
   - Verify collections functionality (Room queries)

## Risk Assessment

- **Medium risk**: NewPipe Extractor is the most sensitive area. Its heavy use of reflection and dynamic class loading means overly aggressive shrinking could cause runtime crashes that aren't caught at build time. The broad `-keep class org.schabi.newpipe.extractor.** { *; }` rule is intentionally conservative.
- **Low risk**: Room and Gson keep rules are well-established patterns. R8 handles annotation-based keep rules natively.
- **Reversible**: If issues arise, `isMinifyEnabled = false` can be restored immediately.

## Files to Modify

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Set `isMinifyEnabled = true`, add `isShrinkResources = true` |
| `app/proguard-rules.pro` | Add all R8 keep rules (Room, Gson, NewPipe, debug info) |
