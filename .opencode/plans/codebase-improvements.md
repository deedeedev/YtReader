# YtReader Codebase Analysis & Improvement Suggestions

## MEDIUM PRIORITY

### 8. `SubtitleEntity` is Bloated (25 Fields)

Mixes multiple concerns in a single entity:
- **Content & metadata** — content, studyContent, languageCode, trackIdentity
- **Reading state** — lastTimestamp, lastOpenedAt, readingProgressPercent, isRead, currentPage, totalPages, lastStudyScroll
- **Display preferences** — fontSize, fontFamily
- **AI cleaning state** — aiCleaningInProgress, aiCleaningSourceText, aiCleaningPendingResult, aiCleaningErrorSummary, aiCleaningErrorLog, aiCleaningUpdatedAt
- **Highlight data** — highlights as serialized String

**Fix:** Normalize into 2-3 focused entities (e.g., `SubtitleContentEntity`, `SubtitleReadingStateEntity`, `AiCleaningStateEntity`).

---

### 9. Notification ID Collisions

**Location — `AiCleaningWorker.kt:363-366`:**
```kotlin
private fun notificationIdFor(subtitleId: Long): Int {
    val normalized = (subtitleId % 10_000).toInt().coerceAtLeast(0)
    return AI_CLEANING_NOTIFICATION_ID_BASE + normalized
}
```

Two different subtitles whose IDs differ by a multiple of 10,000 will get the same notification ID, causing one to overwrite the other.

**Fix:** Use a wider modulo range or a `SparseArray` mapping.

---

### 10. Context Passed Into ViewModels

`HomeViewModel`, `ReaderViewModel`, and `SettingsViewModel` all take `Context` as a constructor parameter. This is used mainly for `appContext.getString(...)`. It creates testing difficulties and potential memory leaks.

**Fix:** Use string resource IDs (`@StringRes`) in UiState or create a `StringProvider` interface.

---

### 11. Outdated User-Agent in `NewPipeDownloader`

**Location — `data/remote/NewPipeDownloader.kt`:**

The Chrome 90 UA string (`Chrome/90.0.4430.212`) is from April 2021. YouTube may flag or throttle requests with an old browser signature.

**Fix:** Update to a current Chrome user-agent string.

---

### 12. Debug Stack Traces in Production Code

**Location — `MainScreen.kt:407`:**
```kotlin
Log.d(TAG, "onInitialNavigationConsumed: clearing ...", Exception("stacktrace"))
```

Creating `Exception("stacktrace")` objects has non-trivial overhead in production.

**Fix:** Remove or gate behind `BuildConfig.DEBUG`.

---

### 13. Race Condition in `updateReadingProgress()`

**Location — `ReaderViewModel.kt:140-153`:**
```kotlin
private var lastSavedPercent = 0

fun updateReadingProgress(percent: Int, currentPage: Int, totalPages: Int) {
    if (percent < lastSavedPercent && percent < 100) return
    lastSavedPercent = percent.coerceIn(0, 100)
    viewModelScope.launch {
        subtitleDao.updateReadingProgress(...)
    }
}
```

`lastSavedPercent` is read/written from the UI thread and from coroutines without synchronization.

**Fix:** Use `MutableStateFlow<Int>` or `AtomicInteger` for thread safety.

---

### 14. Uncancelled CoroutineScope in `AiCleaningCancelReceiver`

**Location — `AiCleaningWorker.kt:314`:**
```kotlin
CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { ... }
```

A new `CoroutineScope` is created on every broadcast receive. If the coroutine hangs, it will leak.

**Fix:** Store the scope and cancel it appropriately, or use `pendingResult` lifecycle properly.

---

## LOW PRIORITY

### 15. Extractor Module Issues

- **Tests completely disabled** (`tasks.test { enabled = false }`) — upstream has 125 test files that could catch regressions
- **Forked source** instead of git submodule — makes upstream sync manual and error-prone
- **Java toolchain conflict**: root `build.gradle.kts` sets Java 11 via `allprojects {}`, subproject overrides to 21
- **Gratuitous `Set`→`List` change** in `StreamingService`/`ServiceInfo` makes merging upstream changes harder

**Fix:** Consider consuming the extractor as a git submodule or published Maven artifact. Re-enable tests. Resolve toolchain conflict at the root level.

---

### 16. Build Performance (`gradle.properties`)

Current config leaves performance on the table:
- **Enable build cache:** Add `org.gradle.caching=true`
- **Enable parallel execution:** Uncomment `org.gradle.parallel=true` (2 modules can build in parallel)
- **Increase JVM heap:** Consider `-Xmx4096m` for KSP + Compose + Protobuf compilation
- **Add `android.enableJetifier=false`:** Project uses AndroidX exclusively; explicit setting speeds builds

---

### 17. Test Coverage Gaps

**Critical untested files:**
- `HomeViewModel.kt` — the primary ViewModel for 4 screens
- `YoutubeRepository.kt` — the main data gateway
- `AiCleaningWorker.kt` — foreground worker for AI cleaning
- `AutoBackupWorker.kt` — background backup worker
- 5 of 6 DAOs (only `SubtitleDao` is tested at the DAO level)
- `EpubExporter.kt` — EPUB generation
- `NewPipeDownloader.kt` — network downloader
- `SettingsViewModel.kt` — settings ViewModel

**Thin coverage needing expansion:**
- `CollectionRepositoryTest` — only 3 tests (missing delete, video management, errors)
- `SubtitleParserTest` — only 3 tests (missing malformed input, encoding, nested tags)
- `VideoNotesViewModelTest` — only 1 test method
- `VideoThumbnailStoreTest` — only 2 tests (no file I/O testing)

**Strengths in existing tests:**
- `ReaderScreenTest` (1001 lines) — outstanding Compose UI test
- `SubtitleDaoTest` (488 lines) — thorough DAO testing with in-memory Room
- `ReaderViewModelTest` — excellent ViewModel testing with proper coroutine dispatchers
- Good edge case coverage in `TextHighlightTest`, `ReaderFindTest`, `AnnotationRemapperTest`

---

### 18. Miscellaneous Cleanup

- **Remove legacy template colors** in `colors.xml` (`purple_200/500/700`, `teal_200/700`, `black`, `white`) — unused with Compose Material3
- **Mark AI log strings `translatable="false"`** in `strings.xml` (lines 314-326) — developer-facing messages
- **Add `.gitignore` entries:** `*.apk`, `*.aab`, `*.hprof`, `*.keystore`, `*.jks`, `.env`
- **Remove boilerplate tests:** `ExampleUnitTest.kt` and `ExampleInstrumentedTest.kt` provide zero value
- **Add root-level plugin declarations** in root `build.gradle.kts` for `ksp` and `kotlin-android`

---

## Positive Notes

The codebase has genuine strengths:
- Excellent `ReaderScreen` Compose UI tests (1001 lines) — comprehensive gesture and lifecycle testing
- Thorough `SubtitleDaoTest` (488 lines) with in-memory Room
- Proper use of `StateFlow`/`collectAsStateWithLifecycle` throughout
- Clean version catalog management (`libs.versions.toml`) with no hardcoded versions
- Well-structured manual DI via `AppContainer` interface
- Domain layer (`SubtitleParser`, `YouTubeVideoIdNormalizer`, `SubtitleIdentity`) properly separated from Android
- Pure function testing pattern — many tests target extracted pure functions rather than testing composables directly
- `FAIL_ON_PROJECT_REPOS` enabled in `settings.gradle.kts`
- Non-transitive R classes enabled
- Configuration cache enabled for faster Gradle startup
