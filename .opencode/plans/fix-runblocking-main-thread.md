# Plan: Fix `runBlocking` on Main Thread (ANR Risk)

## Problem

Two locations call `runBlocking` on the main thread, which can cause ANRs if the underlying work takes more than a few seconds:

### Location 1: `AppContainer.kt:113-118`
```kotlin
override val collectionRepository: CollectionRepository by lazy {
    CollectionRepository(collectionDao, userPreferencesRepository).also { repository ->
        runBlocking {
            repository.migrateLegacyCollectionsIfNeeded()
        }
    }
}
```
Triggered when `collectionRepository` is first accessed. This happens during `HomeViewModel` construction in `MainActivity.kt:74`, which runs on the main thread. `migrateLegacyCollectionsIfNeeded()` does a full legacy JSON parse + batch DB writes.

### Location 2: `ReaderWidgetProvider.kt:44`
```kotlin
val recentSubtitle = runBlocking { subtitleDao.getMostRecentlyOpened() }
```
Triggered during every widget update (system-initiated `onUpdate()`, plus manual `notifyWidgetChanged()` calls from `ReaderViewModel.kt:51`). `AppWidgetProvider.onUpdate()` runs on the main thread.

---

## Fix Strategy

| Location | Approach | Rationale |
|---|---|---|
| AppContainer migration | Make migration idempotent, run async in `Application.onCreate()` via a coroutine | Lazy init is too late; `onCreate()` is the right place for one-time setup |
| Widget provider | Use `CoroutineScope(Dispatchers.IO)` with `goAsync()` to allow async work in BroadcastReceiver | `goAsync()` is the Android-sanctioned way to do async work in `AppWidgetProvider` |

---

## Phase 1: Fix `runBlocking` in AppContainer

### 1.1 Modify `CollectionRepository` — expose migration as a public idempotent method (already is)

`migrateLegacyCollectionsIfNeeded()` is already idempotent — it checks `areCollectionsMigratedToDatabase()` first and returns immediately if already migrated. No changes needed to `CollectionRepository.kt`.

### 1.2 Remove `runBlocking` from the lazy initializer

**File: `AppContainer.kt`**

**Before (lines 113-118):**
```kotlin
override val collectionRepository: CollectionRepository by lazy {
    CollectionRepository(collectionDao, userPreferencesRepository).also { repository ->
        runBlocking {
            repository.migrateLegacyCollectionsIfNeeded()
        }
    }
}
```

**After:**
```kotlin
override val collectionRepository: CollectionRepository by lazy {
    CollectionRepository(collectionDao, userPreferencesRepository)
}
```

The `CollectionRepository` is constructed lazily but the migration is no longer triggered at construction time.

### 1.3 Add a `runMigrations()` method to `AppContainer`

**File: `AppContainer.kt` — interface**

Add to the interface:
```kotlin
interface AppContainer {
    // ... existing properties ...
    suspend fun runMigrations()
    fun closeDatabase()
}
```

**File: `AppContainer.kt` — `DefaultAppContainer`**

Add the implementation:
```kotlin
override suspend fun runMigrations() {
    collectionRepository.migrateLegacyCollectionsIfNeeded()
}
```

Note: Accessing `collectionRepository` here triggers the lazy init (constructs `CollectionRepository`). The actual DAO/DB is also lazy, so this only opens the database if it hasn't been opened yet. If the DB is already open (e.g., user opened the library screen first), the lazy is already resolved and this is essentially free.

### 1.4 Call `runMigrations()` from `YtReaderApplication.onCreate()`

**File: `YtReaderApplication.kt`**

**Before (lines 22-28):**
```kotlin
override fun onCreate() {
    super.onCreate()
    AndroidThreeTen.init(this)
    container = DefaultAppContainer(this)
    createAiCleaningNotificationChannel(this)
    createAutoBackupNotificationChannel(this)
}
```

**After:**
```kotlin
override fun onCreate() {
    super.onCreate()
    AndroidThreeTen.init(this)
    container = DefaultAppContainer(this)
    createAiCleaningNotificationChannel(this)
    createAutoBackupNotificationChannel(this)

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    appScope.launch {
        container.runMigrations()
    }
}
```

Add imports:
```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
```

**Why this is safe:**
- `Application.onCreate()` runs once per process, before any Activity is created
- The migration is idempotent — if the app crashes during migration, it re-runs on next launch
- All code paths that access `collectionRepository` (via `HomeViewModel`) are in `MainActivity.setContent {}`, which runs well after `onCreate()` completes, so the migration coroutine has time to finish
- Even if the migration hasn't completed by the time the user navigates to collections, the `collectionRepository` lazy itself just constructs the object; the migration is a separate concern that was already a no-op for returning users

**Edge case — first launch with legacy data:** On first launch after update, `collectionRepository` might be accessed by the UI before `runMigrations()` completes. This is safe because:
1. The migration writes legacy data into the DB
2. The UI observes `collectionRepository.collections` which is a `Flow` from Room
3. When the migration inserts data, Room emits a new value through the Flow
4. The UI automatically picks up the new collections

### 1.5 Remove unused import

**File: `AppContainer.kt`**

Remove line 19:
```kotlin
import kotlinx.coroutines.runBlocking
```

---

## Phase 2: Fix `runBlocking` in ReaderWidgetProvider

### 2.1 Replace `runBlocking` with `goAsync()` + coroutine

**File: `ReaderWidgetProvider.kt`**

**Before (lines 36-75):**
```kotlin
fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val application = context.applicationContext as YtReaderApplication
    val subtitleDao = application.container.subtitleDao

    val recentSubtitle = runBlocking { subtitleDao.getMostRecentlyOpened() }
    val hasRecentSubtitle = recentSubtitle != null && recentSubtitle.lastOpenedAt > 0

    // ... build views and update ...
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
```

**After:**
```kotlin
private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val result = goAsync()
    widgetScope.launch {
        try {
            val application = context.applicationContext as YtReaderApplication
            val subtitleDao = application.container.subtitleDao

            val recentSubtitle = subtitleDao.getMostRecentlyOpened()
            val hasRecentSubtitle = recentSubtitle != null && recentSubtitle.lastOpenedAt > 0

            val layoutId = if (hasRecentSubtitle) R.layout.widget_reader else R.layout.widget_reader_icon
            val views = RemoteViews(context.packageName, layoutId)

            val pendingIntent = if (hasRecentSubtitle) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_READER
                    putExtra(MainActivity.EXTRA_SUBTITLE_ID, recentSubtitle.id)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                null
            }

            if (pendingIntent != null) {
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
                views.setInt(R.id.widget_container, "setBackgroundColor", 0xFF1A1A1A.toInt())
            } else {
                views.setOnClickPendingIntent(R.id.widget_container, null)
                views.setInt(R.id.widget_container, "setBackgroundColor", 0xFF2A2A2A.toInt())
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } finally {
            result.finish()
        }
    }
}
```

**Why `goAsync()` is the right approach here:**
- `AppWidgetProvider.onUpdate()` is called on the main thread by the system
- `goAsync()` extends the BroadcastReceiver's lifecycle to allow async work (up to 10 seconds)
- The DB query (`getMostRecentlyOpened`) is a simple indexed query that completes in <1ms, so the 10-second limit is never an issue
- `result.finish()` is called in `finally` to ensure the broadcast is always completed, even on error
- `updateAppWidget()` is also called from `notifyWidgetChanged()`, which is called from `ReaderViewModel` on a coroutine — but `goAsync()` is harmless when not in a BroadcastReceiver context (it's a no-op if called outside `onReceive`)

### 2.2 Update imports

**File: `ReaderWidgetProvider.kt`**

Remove:
```kotlin
import kotlinx.coroutines.runBlocking
```

Add:
```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
```

### 2.3 Alternative considered: Synchronous query on background thread

An alternative would be to make `getMostRecentlyOpened()` a blocking `@Query` by using Room's `executeTransaction` or a synchronous DAO method. However:
- Room DAOs with `suspend` are the established pattern in this codebase
- `goAsync()` + coroutine is the standard Android pattern for widget async work
- Keeping the DAO as `suspend` maintains testability

---

## Phase 3: Update Tests

### 3.1 Update `AppContainerTest.kt`

The existing test constructs `DefaultAppContainer(mockContext)` and checks `assertNotNull`. After the changes, it should also verify that `runMigrations()` is a valid suspend function that can be called.

**File: `app/src/test/.../AppContainerTest.kt`**

Add a test:
```kotlin
@Test
fun testRunMigrationsDoesNotThrow() = runTest {
    val mockContext = mock(Context::class.java)
    `when`(mockContext.applicationContext).thenReturn(mockContext)
    // Need to mock SharedPreferences for UserPreferencesRepository
    val container = DefaultAppContainer(mockContext)
    // Should not throw — migration is idempotent
    container.runMigrations()
}
```

Note: This test may need Robolectric or more mocking depending on how `UserPreferencesRepository` accesses SharedPreferences. The existing test already uses a bare mock Context, so the same approach applies.

### 3.2 Add `ReaderWidgetProviderTest.kt` (optional, new file)

Test that `updateAppWidget()` correctly handles both cases:
- Recent subtitle exists → shows widget with pending intent
- No recent subtitle → shows icon-only widget

This would require Robolectric or an instrumented test due to `RemoteViews` and `PendingIntent` usage. Given the widget code is simple, this is lower priority than the AppContainer test.

---

## File Change Summary

| File | Change | Lines Changed |
|---|---|---|
| `AppContainer.kt` | Remove `runBlocking`, add `runMigrations()`, remove unused import | ~5 lines |
| `YtReaderApplication.kt` | Add coroutine launch for `runMigrations()` | ~5 lines added |
| `ReaderWidgetProvider.kt` | Replace `runBlocking` with `goAsync()` + coroutine scope | ~15 lines changed |
| `AppContainerTest.kt` | Add `runMigrations` test | ~10 lines added |

---

## Verification

After all changes:
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] Fresh install: verify collections screen works (no legacy data to migrate)
- [ ] Upgrade scenario: add legacy collections JSON to SharedPreferences, launch app, verify collections appear
- [ ] Widget: open a subtitle in reader → check widget updates to show the recent subtitle
- [ ] Widget: clear all subtitles → check widget shows icon-only layout
- [ ] No `runBlocking` import remains in either file
- [ ] No ANR during app startup or widget updates
