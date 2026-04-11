# Plan: Introduce Repository Layer for DAOs Bypassing Repository Pattern

## Problem Statement

5 of 6 DAOs (`SubtitleDao`, `VideoDao`, `HighlightNoteDao`, `BookmarkDao`, `SearchHistoryDao`) are passed directly from `AppContainer` through Composable screens into ViewModels, Workers, Widgets, and utility functions. Only `CollectionDao` is properly wrapped by `CollectionRepository`. This creates several issues:

- **No centralized business logic** — Data operations (e.g., cascade deletes, video metadata upserts) are duplicated across ViewModels via shared helpers (`HomeViewModelHelpers.kt`).
- **No caching or error-handling layer** — Each ViewModel handles DAO errors independently (or not at all).
- **Tight coupling to Room** — Switching storage or adding cross-cutting concerns (logging, analytics) requires touching every ViewModel.
- **Composables receive DAOs as parameters** — UI layer depends directly on the data layer, violating clean architecture.
- **DAOs thread through multiple layers** — `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao` are passed through `MainActivity → MainScreen → Screen Composables → EpubExportDialog` solely for EPUB export.

## Current Architecture (Simplified)

```
AppContainer
  ├── subtitleDao ─────────┐
  ├── videoDao ────────────┤
  ├── highlightNoteDao ────┤──→ MainActivity → MainScreen → Screens → ViewModels
  ├── bookmarkDao ─────────┤
  ├── searchHistoryDao ────┘
  ├── collectionDao → CollectionRepository ✅ (properly wrapped)
  ├── youtubeRepository (remote only)
  ├── userPreferencesRepository (SharedPreferences only)
  └── aiCleaningRepository (remote only)
```

## Target Architecture

```
AppContainer
  ├── SubtitleRepository (NEW) ─── wraps SubtitleDao
  ├── VideoRepository (NEW) ────── wraps VideoDao
  ├── NoteRepository (NEW) ─────── wraps HighlightNoteDao + BookmarkDao
  ├── SearchHistoryRepository (NEW) ── wraps SearchHistoryDao
  ├── CollectionRepository (existing, unchanged)
  ├── YoutubeRepository (existing, unchanged)
  ├── UserPreferencesRepository (existing, unchanged)
  └── AiCleaningRepository (existing, unchanged)
```

Composables and Workers receive **repositories only** — never DAOs.

## Affected Files (Full Inventory)

### New files to create (4 repositories)
| File | Purpose |
|------|---------|
| `app/src/main/java/com/deedeedev/ytreader/data/SubtitleRepository.kt` | Wraps `SubtitleDao` |
| `app/src/main/java/com/deedeedev/ytreader/data/VideoRepository.kt` | Wraps `VideoDao` |
| `app/src/main/java/com/deedeedev/ytreader/data/NoteRepository.kt` | Wraps `HighlightNoteDao` + `BookmarkDao` |
| `app/src/main/java/com/deedeedev/ytreader/data/SearchHistoryRepository.kt` | Wraps `SearchHistoryDao` |

### Files to modify (16 files)

| # | File | DAOs Used Directly | Changes Required |
|---|------|-------------------|------------------|
| 1 | `AppContainer.kt` | All 5 DAOs | Add 4 new repository properties; remove direct DAO exposure (or make private) |
| 2 | `MainActivity.kt` | `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao`, `searchHistoryDao` | Replace DAO params with repository params in ViewModel factory calls |
| 3 | `MainScreen.kt` | `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao` | Replace DAO params with repository params in all screen composable calls |
| 4 | `LibraryViewModel.kt` | `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao`, `database` | Replace DAOs with repositories; move transaction logic into repositories |
| 5 | `SearchViewModel.kt` | `subtitleDao`, `videoDao`, `searchHistoryDao` | Replace DAOs with repositories |
| 6 | `CollectionsViewModel.kt` | `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao`, `database` | Replace DAOs with repositories; move transaction logic into repositories |
| 7 | `ReaderViewModel.kt` | `subtitleDao`, `highlightNoteDao`, `bookmarkDao` | Replace DAOs with repositories |
| 8 | `VideoNotesViewModel.kt` | `subtitleDao`, `highlightNoteDao`, `bookmarkDao` | Replace DAOs with repositories |
| 9 | `AnnotationsViewModel.kt` | `subtitleDao`, `highlightNoteDao`, `bookmarkDao` | Replace DAOs with repositories |
| 10 | `SettingsViewModel.kt` | `videoDao` | Replace DAO with `VideoRepository` |
| 11 | `AiCleaningWorker.kt` | `subtitleDao` | Replace DAO with `SubtitleRepository` |
| 12 | `ReaderWidgetProvider.kt` | `subtitleDao` | Replace DAO with `SubtitleRepository` |
| 13 | `EpubExporter.kt` | `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao` | Replace DAO params with repository params |
| 14 | `HomeViewModelHelpers.kt` | `subtitleDao`, `videoDao` | Replace DAO params with repository params (or inline into repositories) |
| 15 | `LibraryScreen.kt` | `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao` | Replace DAO params with repository params |
| 16 | `CollectionsScreen.kt` | `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao` | Replace DAO params with repository params |

> **Note:** `CollectionDetailScreen.kt`, `ReaderScreen.kt`, `VideoNotesScreen.kt`, `SettingsScreen.kt`, `AnnotationsScreen.kt`, and `EpubExportDialog.kt` may also need parameter changes. These should be discovered and updated during implementation by following the compiler errors.

---

## Implementation Plan

### Phase 1: Create Repository Classes

Create the 4 new repository classes. Each repository should:
- Accept its DAO(s) via constructor injection
- Wrap every DAO method with `withContext(Dispatchers.IO)` for suspend functions
- Expose `Flow`-returning methods directly (Room already handles threading for `Flow`)
- Centralize cross-cutting operations currently scattered in `HomeViewModelHelpers.kt`

#### 1.1 `SubtitleRepository`

```kotlin
class SubtitleRepository(private val subtitleDao: SubtitleDao) {

    // Flow-based queries (Room handles threading)
    fun observeAll(): Flow<List<SubtitleEntity>> = subtitleDao.getAll()
    fun observeLibraryChannels(...) = subtitleDao.observeLibraryChannels(...)
    fun observeLibraryVideoRows(...) = subtitleDao.observeLibraryVideoRows(...)
    fun observeCollectionChannels(...) = subtitleDao.observeCollectionChannels(...)
    fun observeCollectionVideoRows(...) = subtitleDao.observeCollectionVideoRows(...)
    fun observeSubtitleTracksForVideos(...) = subtitleDao.observeSubtitleTracksForVideos(...)
    fun observeCollectionVideoCount(...) = subtitleDao.observeCollectionVideoCount(...)
    fun observeById(id: Long) = subtitleDao.observeById(id)
    fun observeByVideoId(videoId: String) = subtitleDao.observeByVideoId(videoId)
    fun observeAllAccessibleSubtitles() = subtitleDao.observeAllAccessibleSubtitles()

    // Suspend operations (wrapped with IO dispatcher)
    suspend fun getById(id: Long) = withContext(Dispatchers.IO) { subtitleDao.getById(id) }
    suspend fun getPreferredSubtitleForVideo(videoId: String) = ...
    suspend fun countByVideoId(videoId: String) = ...
    suspend fun insertIgnore(subtitle: SubtitleEntity) = ...
    suspend fun upsertByIdentity(subtitle: SubtitleEntity) = ...
    suspend fun getByIdentity(videoId: String, trackIdentity: String) = ...
    suspend fun delete(subtitle: SubtitleEntity) = ...
    suspend fun updateDownloadedSubtitle(...) = ...
    suspend fun updateLastTimestamp(...) = ...
    suspend fun updateLastOpenedAt(...) = ...
    suspend fun replaceContentForRedownload(...) = ...
    suspend fun resetReadingProgressForVideo(videoId: String) = ...
    suspend fun markVideoAsRead(videoId: String) = ...
    suspend fun deleteByVideoId(videoId: String) = ...
    suspend fun getMostRecentlyOpened() = ...
    suspend fun updateLibraryVisibility(...) = ...
    suspend fun updateHighlights(...) = ...
    suspend fun updateLastStudyScroll(...) = ...
    suspend fun updateReadingProgress(...) = ...
    suspend fun updateFontSize(...) = ...
    suspend fun updateFontFamily(...) = ...
    suspend fun updateStudyContent(...) = ...
    suspend fun markAiCleaningQueued(...) = ...
    suspend fun storeAiCleaningResult(...) = ...
    suspend fun storeAiCleaningFailure(...) = ...
    suspend fun cancelAiCleaning(...) = ...
    suspend fun clearAiCleaningResult(...) = ...
    suspend fun clearAiCleaningError(...) = ...
    suspend fun countLibraryEntriesByVideoId(videoId: String) = ...
    suspend fun getLibraryVideoIds() = ...
}
```

#### 1.2 `VideoRepository`

```kotlin
class VideoRepository(private val videoDao: VideoDao) {
    suspend fun upsert(video: VideoEntity) = withContext(Dispatchers.IO) { videoDao.upsert(video) }
    suspend fun getByVideoId(videoId: String) = ...
    suspend fun getAll() = ...
    suspend fun getAllMissingThumbnailPath() = ...
    suspend fun getAllReferencedThumbnailPaths() = ...
    suspend fun deleteByVideoId(videoId: String) = ...

    // Business logic currently in HomeViewModelHelpers
    suspend fun upsertVideoMetadata(videoId: String, title: String, uploader: String, thumbnailUrl: String?) {
        // Move from HomeViewModelHelpers.upsertVideoMetadata()
    }

    suspend fun deleteVideoIfUnreferenced(videoId: String, isInCollection: Boolean) {
        // Move from HomeViewModelHelpers.deleteVideoIfUnreferenced()
    }
}
```

#### 1.3 `NoteRepository`

Combines `HighlightNoteDao` and `BookmarkDao` into a single cohesive "notes/annotations" repository:

```kotlin
class NoteRepository(
    private val highlightNoteDao: HighlightNoteDao,
    private val bookmarkDao: BookmarkDao
) {
    // Highlights
    fun observeHighlightsBySubtitleId(subtitleId: Long) = highlightNoteDao.observeBySubtitleId(subtitleId)
    fun observeHighlightsBySubtitleIds(subtitleIds: List<Long>) = highlightNoteDao.observeBySubtitleIds(subtitleIds)
    suspend fun upsertHighlight(note: HighlightNoteEntity) = ...
    suspend fun deleteHighlightByRange(...) = ...
    suspend fun deleteHighlightsBySubtitleId(subtitleId: Long) = ...

    // Bookmarks
    fun observeBookmarksBySubtitleId(subtitleId: Long) = bookmarkDao.observeBySubtitleId(subtitleId)
    fun observeBookmarksBySubtitleIds(subtitleIds: List<Long>) = bookmarkDao.observeBySubtitleIds(subtitleIds)
    suspend fun upsertBookmark(bookmark: BookmarkEntity) = ...
    suspend fun deleteBookmarkByAnchor(...) = ...
    suspend fun deleteBookmarksBySubtitleId(subtitleId: Long) = ...

    // Composite operations (currently in ViewModels)
    suspend fun deleteAllNotesForSubtitle(subtitleId: Long) {
        // Cascade delete highlights + bookmarks for a subtitle
        deleteHighlightsBySubtitleId(subtitleId)
        deleteBookmarksBySubtitleId(subtitleId)
    }
}
```

#### 1.4 `SearchHistoryRepository`

```kotlin
class SearchHistoryRepository(private val searchHistoryDao: SearchHistoryDao) {
    fun observeAll(): Flow<List<SearchHistoryEntity>> = searchHistoryDao.getAll()
    suspend fun getCount() = withContext(Dispatchers.IO) { searchHistoryDao.getCount() }
    suspend fun upsert(entry: SearchHistoryEntity) = ...
    suspend fun deleteOldest(limit: Int) = ...
    suspend fun delete(id: Long) = ...
}
```

### Phase 2: Update AppContainer

Modify `AppContainer.kt` and `DefaultAppContainer`:

1. Add the 4 new repository properties
2. Keep DAO properties but change visibility to `private` (internal to `DefaultAppContainer`)
3. Alternatively, remove DAO properties from the interface entirely and only expose them within `DefaultAppContainer`'s constructor block

```kotlin
interface AppContainer {
    val appContext: Context
    // Repositories (public API)
    val youtubeRepository: YoutubeRepository
    val userPreferencesRepository: UserPreferencesRepository
    val collectionRepository: CollectionRepository
    val aiCleaningRepository: AiCleaningRepository
    val subtitleRepository: SubtitleRepository      // NEW
    val videoRepository: VideoRepository              // NEW
    val noteRepository: NoteRepository                // NEW
    val searchHistoryRepository: SearchHistoryRepository  // NEW

    fun runMigrations()
    fun closeDatabase()
}
```

### Phase 3: Migrate ViewModels (one at a time)

Migrate each ViewModel to accept repositories instead of DAOs. **Migrate one ViewModel at a time, compiling and testing between each.**

Order (least dependent → most dependent):

#### 3.1 `SettingsViewModel`
- Replace `videoDao: VideoDao` → `videoRepository: VideoRepository`
- Update `SettingsViewModel.provideFactory()` signature
- Update `SettingsScreen` to receive `videoRepository` instead of accessing `appContainer.videoDao`

#### 3.2 `SearchViewModel`
- Replace `subtitleDao`, `videoDao`, `searchHistoryDao` → `subtitleRepository`, `videoRepository`, `searchHistoryRepository`
- Update `SearchViewModel.provideFactory()` in `MainActivity`
- Move `upsertVideoMetadata()` calls from `HomeViewModelHelpers` into `VideoRepository`

#### 3.3 `ReaderViewModel`
- Replace `subtitleDao`, `highlightNoteDao`, `bookmarkDao` → `subtitleRepository`, `noteRepository`
- Update `ReaderViewModel.provideFactory()`
- Update `ReaderScreen` composable parameters

#### 3.4 `VideoNotesViewModel`
- Replace `subtitleDao`, `highlightNoteDao`, `bookmarkDao` → `subtitleRepository`, `noteRepository`
- Update `VideoNotesViewModel.provideFactory()`
- Update `VideoNotesScreen` / `VideoNotesSheetRoute` parameters

#### 3.5 `AnnotationsViewModel`
- Replace `subtitleDao`, `highlightNoteDao`, `bookmarkDao` → `subtitleRepository`, `noteRepository`
- Update `AnnotationsViewModel.provideFactory()` in `MainScreen`

#### 3.6 `LibraryViewModel`
- Replace `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao` → `subtitleRepository`, `videoRepository`, `noteRepository`
- Remove `database: AppDatabase` param — move `withTransaction` blocks into repository methods
- Update `LibraryViewModel.provideFactory()` in `MainActivity`
- Update `LibraryScreen` composable parameters

#### 3.7 `CollectionsViewModel`
- Same as LibraryViewModel — replace DAOs with repositories
- Move transaction logic into repositories
- Update `CollectionsViewModel.provideFactory()` in `MainActivity`
- Update `CollectionsScreen` / `CollectionDetailScreen` parameters

### Phase 4: Migrate Non-ViewModel Consumers

#### 4.1 `AiCleaningWorker`
- Replace `container.subtitleDao` → `container.subtitleRepository`
- The worker accesses the container via the `Application` class

#### 4.2 `AiCleaningCancelReceiver` (in `AiCleaningWorker.kt`)
- Replace direct `container.subtitleDao.cancelAiCleaning()` call → `container.subtitleRepository.cancelAiCleaning()`

#### 4.3 `ReaderWidgetProvider`
- Replace `application.container.subtitleDao.getMostRecentlyOpened()` → `application.container.subtitleRepository.getMostRecentlyOpened()`

#### 4.4 `EpubExporter.kt`
- Change `exportEpub()` function signature to accept repositories instead of DAOs:
  ```kotlin
  suspend fun exportEpub(
      subtitleRepository: SubtitleRepository,
      videoRepository: VideoRepository,
      noteRepository: NoteRepository,
      ...
  )
  ```
- Update `EpubExportDialog` to pass repositories

### Phase 5: Migrate Composable Screens

Remove all DAO parameters from Composable function signatures:

| Composable | Current DAO Params | Replacement |
|-----------|-------------------|-------------|
| `LibraryScreen` | `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao` | `subtitleRepository`, `videoRepository`, `noteRepository` |
| `CollectionsScreen` | same | same |
| `CollectionDetailScreen` | same | same |
| `ReaderScreen` | `subtitleDao`, `highlightNoteDao`, `bookmarkDao` | `subtitleRepository`, `noteRepository` |
| `VideoNotesSheetRoute` | `subtitleDao`, `highlightNoteDao`, `bookmarkDao` | `subtitleRepository`, `noteRepository` |
| `SettingsScreen` | `appContainer` (accesses `videoDao`) | `videoRepository` or `appContainer` (which now exposes repos) |
| `EpubExportDialog` | `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao` | `subtitleRepository`, `videoRepository`, `noteRepository` |

### Phase 6: Clean Up

#### 6.1 Remove or relocate `HomeViewModelHelpers.kt`
- Move `upsertVideoMetadata()` into `VideoRepository`
- Move `deleteVideoIfUnreferenced()` into `VideoRepository`
- Move `deleteSubtitleAndDependencies()` into a combined repository method (e.g., `SubtitleRepository.deleteSubtitleCascade()` that also calls `NoteRepository`)
- Move `observeSubtitleTracksForVideos()` — this is already a simple DAO passthrough; it goes into `SubtitleRepository`
- Delete `HomeViewModelHelpers.kt` once all helpers are absorbed

#### 6.2 Remove DAO properties from `AppContainer` interface
- Make DAOs internal to `DefaultAppContainer` only
- Verify no compilation errors — all consumers should now use repositories

#### 6.3 Add `@Inject` annotations (optional, future-proofing)
- If the project later migrates to Hilt/Dagger, the repository classes are already properly structured for DI.

### Phase 7: Testing

#### 7.1 Write unit tests for new repositories
- Test each repository method with a mocked DAO
- Verify `Dispatchers.IO` usage for suspend functions
- Test composite operations (e.g., cascade deletes in `NoteRepository`)

#### 7.2 Update existing ViewModel tests
- Mock repositories instead of DAOs in ViewModel tests
- This should simplify test setup since repositories have fewer methods than the combined DAOs

#### 7.3 Run full test suite
```bash
./gradlew :app:testDebugUnitTest
./gradlew :extractor:test
```

#### 7.4 Build verification
```bash
./gradlew :app:assembleDebug
./gradlew lint
```

---

## Migration Strategy: Incremental Approach

To minimize risk, this refactoring should be done incrementally:

1. **Create repositories first** — They can coexist with direct DAO usage. No behavior change.
2. **Update AppContainer** — Add new repository properties alongside existing DAO properties.
3. **Migrate one ViewModel at a time** — After each migration, run `./gradlew :app:assembleDebug` to verify compilation. This ensures no cascading breakage.
4. **Migrate screens, workers, and widgets** — After all ViewModels are migrated.
5. **Clean up** — Remove DAO exposure from `AppContainer` interface, delete `HomeViewModelHelpers.kt`.
6. **Test** — Run full test suite and lint.

Each step should produce a **compilable, testable state**. No big-bang refactoring.

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Transaction boundary changes — moving `withTransaction` from ViewModel to Repository may alter atomicity | Keep `withTransaction` in repository and verify the repository has access to `AppDatabase` if needed, or accept a `CoroutineScope` for transaction boundaries |
| `HomeViewModelHelpers.kt` shared helpers used by multiple ViewModels | Move helpers into repositories first, then update ViewModels to call repository methods |
| EPUB export runs synchronously with DAOs during composable composition | `EpubExporter` is already called from a coroutine; repository wrapping won't change this |
| Widget provider accesses container directly | Ensure `Application.container` exposes repositories, not DAOs |
| Large number of `SubtitleDao` methods (30+) | Accept that `SubtitleRepository` will be a large class — this is expected for a central data access point |

## Estimated Scope

- **4 new files** (repositories)
- **~16 modified files** (ViewModels, screens, workers, exporters)
- **1 deleted file** (`HomeViewModelHelpers.kt`)
- **~30 methods** in `SubtitleRepository`, **~8** in `VideoRepository`, **~10** in `NoteRepository`, **~5** in `SearchHistoryRepository`
- **Recommended PR count**: 2-3 PRs (Phase 1-2 as one, Phase 3-4 as one, Phase 5-7 as one)
