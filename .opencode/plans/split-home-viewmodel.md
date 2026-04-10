# Plan: Split HomeViewModel God Class

## Problem

`HomeViewModel` (855 lines, 9 constructor dependencies) serves four screens — Search, Library, Collections, and CollectionDetail — making it the largest and most complex ViewModel in the project. It holds all state for all four screens in a single `HomeUiState` data class, meaning a state change on one screen recomposes observers on all four. It is also completely untested.

## Proposed Split

Split into **three** ViewModels along screen boundaries:

| New ViewModel | Screens | ~Lines | Dependencies |
|---|---|---|---|
| `SearchViewModel` | SearchScreen | ~200 | `youtubeRepository`, `subtitleDao`, `searchHistoryDao`, `userPreferencesRepository` |
| `LibraryViewModel` | LibraryScreen | ~350 | `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao`, `userPreferencesRepository`, `collectionRepository`, `youtubeRepository` |
| `CollectionsViewModel` | CollectionsScreen, CollectionDetailScreen | ~300 | `subtitleDao`, `videoDao`, `highlightNoteDao`, `bookmarkDao`, `userPreferencesRepository`, `collectionRepository`, `youtubeRepository` |

Shared types (`SortOption`, `LibraryItem`, `LibraryVisibilityFilter`, `ReadStatusFilter`, `CollectionFilterState`, etc.) remain in `LibraryModels.kt`.

---

## Phase 1: Create SearchViewModel

### 1.1 New file: `ui/home/SearchViewModel.kt`

**UiState to extract from `HomeUiState`:**
```kotlin
data class SearchUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val streamInfo: StreamInfo? = null,
    val savedSubtitles: List<SubtitleEntity> = emptyList(),
    val favoriteLanguages: Set<String> = emptySet(),
    val searchHistory: List<SearchHistoryEntity> = emptyList(),
    val showHistory: Boolean = false
)
```

**Functions to move from HomeViewModel:**

| Function | Lines (HomeViewModel) | Notes |
|---|---|---|
| `loadSavedSubtitles()` | 158-164 | Observes `subtitleDao.getAll()` — needed for "already downloaded" badge |
| `loadFavoriteLanguages()` | 146-152 | Observes `userPreferencesRepository.favoriteLanguages` |
| `toggleFavoriteLanguage()` | 154-156 | Delegates to `userPreferencesRepository` |
| `loadSearchHistory()` | 166-172 | Observes `searchHistoryDao.getAll()` |
| `onUrlChange()` | 224-234 | Search input field state |
| `searchVideo()` | 251-270 | Fetches stream info, saves to history |
| `toggleHistory()` | 272-274 | Toggle history dropdown |
| `deleteHistoryEntry()` | 276-280 | Delete single history item |
| `searchFromHistory()` | 282-285 | Fill URL + search |
| `saveToSearchHistory()` | 287-300 | Private helper |
| `downloadSubtitle()` | 302-354 | Downloads subtitle + upserts video metadata |
| `upsertVideoMetadata()` | 683-712 | Private helper (also needed by Library/Collections) |
| `downloadThumbnail()` | 714-726 | Private helper |
| `resolveVideoLookupUrl()` | 590-600 | Private helper |
| `displayUrlFor()` | 734-744 | Private helper |
| `selectSubtitle()` | 236-241 | Not used by SearchScreen directly — move to Library |
| `clearSelection()` | 247-249 | Not used by SearchScreen — move to Library |
| `getPreferredSubtitleIdForVideo()` | 243-245 | Used by MainScreen for navigation — move to Library |

**Event type:**
```kotlin
sealed interface SearchEvent {
    data class ShowMessage(val message: String) : SearchEvent
}
```

**Constructor:**
```kotlin
class SearchViewModel(
    private val appContext: Context,
    private val youtubeRepository: YoutubeRepository,
    private val subtitleDao: SubtitleDao,
    private val videoDao: VideoDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao
)
```

6 dependencies (down from 9).

### 1.2 Shared helpers to extract

The following private methods are needed by multiple ViewModels. Extract them to a new file `ui/home/HomeViewModelHelpers.kt` (internal top-level functions):

- `upsertVideoMetadata(videoDao, youtubeRepository, appContext, videoId, fallbackVideoUrl, fallbackTitle, fallbackChannelName, fallbackUploadDate, info: StreamInfo)` — lines 683-712
- `downloadThumbnail(youtubeRepository, appContext, videoId, sourceUrl): String?` — lines 714-726
- `displayUrlFor(videoId, videoUrl): String` — lines 734-744
- `resolveVideoLookupUrl(subtitle): String` — lines 590-600
- `deleteVideoIfUnreferenced(subtitleDao, videoDao, collectionRepository, appContext, videoId)` — lines 672-681

### 1.3 Update SearchScreen.kt

Change `viewModel: HomeViewModel` parameter to `viewModel: SearchViewModel` on line 74. All function calls remain the same since the method signatures are identical.

### 1.4 Update MainScreen.kt

Replace the `HomeViewModel` instantiation in `MainScreen` composable:

**Before (lines 98-110):**
```kotlin
viewModel: HomeViewModel = viewModel(
    factory = HomeViewModel.provideFactory(...)
)
```

**After:**
```kotlin
val searchViewModel: SearchViewModel = viewModel(
    factory = SearchViewModel.provideFactory(
        appContainer.appContext,
        appContainer.youtubeRepository,
        appContainer.subtitleDao,
        appContainer.videoDao,
        appContainer.userPreferencesRepository,
        appContainer.searchHistoryDao
    )
)
```

Update `SearchScreen` composable call (line 241-246):
```kotlin
SearchScreen(
    viewModel = searchViewModel,
    onSubtitleClick = { id -> openReader(id, 0 to 0) }
)
```

Update `searchVideoAgain` lambda (lines 153-163):
```kotlin
val searchVideoAgain: (String) -> Unit = { url ->
    searchViewModel.onUrlChange(url)
    searchViewModel.searchVideo()
    navController.navigate(Screen.Search.route) { ... }
}
```

### 1.5 Update AppContainer.kt

No changes needed — the container provides DAOs/repositories which are now consumed by individual ViewModels via their factories.

---

## Phase 2: Create LibraryViewModel

### 2.1 New file: `ui/home/LibraryViewModel.kt`

**UiState to extract from `HomeUiState`:**
```kotlin
data class LibraryUiState(
    val selectedChannelFilter: String? = null,
    val libraryVisibilityFilter: LibraryVisibilityFilter = LibraryVisibilityFilter.ALL,
    val libraryReadStatusFilter: ReadStatusFilter = ReadStatusFilter.ALL,
    val sortOption: SortOption = SortOption.DOWNLOADED,
    val isAscending: Boolean = false,
    val downloadingSubtitleIds: Set<Long> = emptySet(),
    val downloadingThumbnailVideoIds: Set<String> = emptySet(),
    val collections: List<VideoCollection> = emptyList(),
    val error: String? = null
)
```

**Functions to move:**

| Function | Lines | Notes |
|---|---|---|
| `libraryChannels` (property) | 102-103 | `subtitleDao.observeLibraryChannels()` |
| `libraryItems` (property) | 105-129 | Complex reactive flow |
| `loadCollections()` | 138-144 | Observes collection flow |
| `setChannelFilter()` | 174-178 | Filter state |
| `setLibraryVisibilityFilter()` | 180-184 | Filter state |
| `setLibraryReadStatusFilter()` | 186-190 | Filter state |
| `setSortOption()` | 192-196 | Sort state |
| `toggleSortOrder()` | 198-202 | Sort state |
| `selectSubtitle()` | 236-241 | For subtitle selection (used by Library cards) |
| `clearSelection()` | 247-249 | Clear selection |
| `getPreferredSubtitleIdForVideo()` | 243-245 | Used by MainScreen navigation lambdas |
| `downloadSubtitleAgain()` | 356-408 | Re-download with annotation deletion |
| `downloadThumbnailForVideo()` | 410-445 | Thumbnail download for library items |
| `removeLibraryItem()` | 446-452 | Remove from library |
| `restoreLibraryItem()` | 454-462 | Restore to library |
| `resetVideoProgress()` | 464-468 | Reset reading progress |
| `markVideoAsRead()` | 470-474 | Mark as read |
| `deleteVideoPermanently()` | 476-481 | Hard delete |
| `deleteSubtitle()` | 483-491 | Delete single subtitle |
| `addVideoToCollection()` | 547-560 | Add video to existing collection |
| `createCollection()` | 493-505 | Create new collection |
| `exportEpub()` | 562-577 | EPUB export |
| `getAllLibraryVideoIds()` | 579-581 | For EPUB export |
| `observeLibraryItemsForRows()` | 641-670 | Private helper |
| `createInitialLibraryUiState()` | derived from 746-758 | Just library portion |
| `persistLibraryFilterState()` | 760-770 | Private helper |
| Filter conversion extensions | 798-817 | Private helpers |

**Event type:**
```kotlin
sealed interface LibraryEvent {
    data class ShowMessage(val message: String) : LibraryEvent
}
```

**Constructor:**
```kotlin
class LibraryViewModel(
    private val appContext: Context,
    private val youtubeRepository: YoutubeRepository,
    private val subtitleDao: SubtitleDao,
    private val videoDao: VideoDao,
    private val highlightNoteDao: HighlightNoteDao,
    private val bookmarkDao: BookmarkDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val collectionRepository: CollectionRepository
)
```

8 dependencies (down from 9). No `searchHistoryDao`.

### 2.2 Update LibraryScreen.kt

Change `viewModel: HomeViewModel` to `viewModel: LibraryViewModel` on line 58.

All ViewModel method calls (`viewModel.uiState`, `viewModel.libraryChannels`, `viewModel.libraryItems`, `viewModel.events`, etc.) remain the same method names on the new ViewModel.

### 2.3 Update MainScreen.kt

Add `LibraryViewModel` instantiation:
```kotlin
val libraryViewModel: LibraryViewModel = viewModel(
    factory = LibraryViewModel.provideFactory(
        appContainer.appContext,
        appContainer.youtubeRepository,
        appContainer.subtitleDao,
        appContainer.videoDao,
        appContainer.highlightNoteDao,
        appContainer.bookmarkDao,
        appContainer.userPreferencesRepository,
        appContainer.collectionRepository
    )
)
```

Update navigation lambdas:
- `openPreferredSubtitleForVideo` (line 129): use `libraryViewModel.getPreferredSubtitleIdForVideo()`
- `openReader` (line 165): no ViewModel change needed

Update `LibraryScreen` composable call (lines 255-267):
```kotlin
LibraryScreen(
    viewModel = libraryViewModel,
    onSubtitleClick = { id, scrollPosition -> openReader(id, scrollPosition) },
    onVideoClick = openPreferredSubtitleForVideo,
    onVideoSearchAgain = searchVideoAgain,
    subtitleDao = appContainer.subtitleDao,
    videoDao = appContainer.videoDao,
    highlightNoteDao = appContainer.highlightNoteDao,
    bookmarkDao = appContainer.bookmarkDao,
    initialScrollPosition = libraryScrollPosition
)
```

---

## Phase 3: Create CollectionsViewModel

### 3.1 New file: `ui/home/CollectionsViewModel.kt`

**UiState to extract from `HomeUiState`:**
```kotlin
data class CollectionsUiState(
    val collections: List<VideoCollection> = emptyList(),
    val collectionFilterStates: Map<String, CollectionFilterState> = emptyMap(),
    val savedSubtitles: List<SubtitleEntity> = emptyList(),
    val downloadingSubtitleIds: Set<Long> = emptySet(),
    val downloadingThumbnailVideoIds: Set<String> = emptySet(),
    val error: String? = null
)
```

**Functions to move:**

| Function | Lines | Notes |
|---|---|---|
| `loadCollections()` | 138-144 | Observes collection flow |
| `loadSavedSubtitles()` | 158-164 | Needed for read status calculations in CollectionsScreen |
| `getCollectionFilterState()` | 204-206 | Per-collection filter state |
| `setCollectionChannelFilter()` | 208-210 | Collection filter |
| `setCollectionSortOption()` | 212-214 | Collection filter |
| `setCollectionReadStatusFilter()` | 216-218 | Collection filter |
| `toggleCollectionSortOrder()` | 220-222 | Collection filter |
| `createCollection()` | 493-505 | CRUD |
| `renameCollection()` | 507-522 | CRUD |
| `deleteCollection()` | 524-532 | CRUD |
| `reorderCollections()` | 534-545 | Drag-drop reorder |
| `addVideoToCollection()` | 547-560 | Video management |
| `removeVideoFromCollection()` | 583-588 | Video management |
| `observeCollectionChannels()` | 602-608 | Collection channel list |
| `observeCollectionItems()` | 610-631 | Collection item list |
| `observeCollectionVideoCount()` | 633-639 | Video count |
| `downloadSubtitleAgain()` | 356-408 | Re-download (shared with Library) |
| `downloadThumbnailForVideo()` | 410-445 | Thumbnail download (shared with Library) |
| `markVideoAsRead()` | 470-474 | Mark as read |
| `resetVideoProgress()` | 464-468 | Reset progress |
| `deleteSubtitle()` | 483-491 | Delete subtitle |
| `restoreLibraryItem()` | 454-462 | Restore from collection detail |
| `normalizeVideoIds()` | 728-732 | Private helper |
| `observeLibraryItemsForRows()` | 641-670 | Private helper (shared with Library) |
| `updateCollectionFilterState()` | 772-796 | Private helper |
| Filter conversion extensions | 798-817 | Private helpers |

**Event type:**
```kotlin
sealed interface CollectionsEvent {
    data class ShowMessage(val message: String) : CollectionsEvent
}
```

**Constructor:**
```kotlin
class CollectionsViewModel(
    private val appContext: Context,
    private val youtubeRepository: YoutubeRepository,
    private val subtitleDao: SubtitleDao,
    private val videoDao: VideoDao,
    private val highlightNoteDao: HighlightNoteDao,
    private val bookmarkDao: BookmarkDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val collectionRepository: CollectionRepository
)
```

8 dependencies (down from 9). No `searchHistoryDao`.

### 3.2 Update CollectionsScreen.kt

Change `viewModel: HomeViewModel` to `viewModel: CollectionsViewModel` on line 79.

### 3.3 Update CollectionDetailScreen.kt

Change `viewModel: HomeViewModel` to `viewModel: CollectionsViewModel` on line 54.

### 3.4 Update MainScreen.kt

Add `CollectionsViewModel` instantiation:
```kotlin
val collectionsViewModel: CollectionsViewModel = viewModel(
    factory = CollectionsViewModel.provideFactory(
        appContainer.appContext,
        appContainer.youtubeRepository,
        appContainer.subtitleDao,
        appContainer.videoDao,
        appContainer.highlightNoteDao,
        appContainer.bookmarkDao,
        appContainer.userPreferencesRepository,
        appContainer.collectionRepository
    )
)
```

Update navigation lambdas:
- `openPreferredSubtitleForVideoFromCollection` (line 141): use `libraryViewModel.getPreferredSubtitleIdForVideo()` (this is a navigation concern, not collection-specific)

Update composable calls:
```kotlin
CollectionsScreen(
    viewModel = collectionsViewModel,
    ...
)

CollectionDetailScreen(
    viewModel = collectionsViewModel,
    ...
)
```

---

## Phase 4: Shared Code Deduplication

### 4.1 New file: `ui/home/HomeViewModelHelpers.kt`

Extract the following as internal top-level functions (so they can be called from any of the three ViewModels):

```kotlin
// From HomeViewModel lines 672-681
internal suspend fun deleteVideoIfUnreferenced(
    subtitleDao: SubtitleDao,
    videoDao: VideoDao,
    collectionRepository: CollectionRepository,
    appContext: Context,
    videoId: String
)

// From HomeViewModel lines 683-712
internal suspend fun upsertVideoMetadata(
    videoDao: VideoDao,
    youtubeRepository: YoutubeRepository,
    appContext: Context,
    videoId: String,
    fallbackVideoUrl: String,
    fallbackTitle: String,
    fallbackChannelName: String,
    fallbackUploadDate: Long,
    info: StreamInfo
)

// From HomeViewModel lines 714-726
internal suspend fun downloadThumbnail(
    youtubeRepository: YoutubeRepository,
    appContext: Context,
    videoId: String,
    sourceUrl: String?
): String?

// From HomeViewModel lines 734-744
internal fun displayUrlFor(videoId: String, videoUrl: String): String

// From HomeViewModel lines 590-600
internal fun resolveVideoLookupUrl(subtitle: SubtitleEntity): String

// From HomeViewModel lines 641-670
internal fun observeLibraryItemsForRows(
    subtitleDao: SubtitleDao,
    collectionRepository: CollectionRepository,
    rows: List<LibraryVideoRow>
): Flow<List<LibraryItem>>
```

### 4.2 Duplicated methods to consolidate

The following methods appear in **both** `LibraryViewModel` and `CollectionsViewModel`:

| Method | Strategy |
|---|---|
| `downloadSubtitleAgain()` | Move to `HomeViewModelHelpers.kt` as a top-level suspend function, call from both VMs |
| `downloadThumbnailForVideo()` | Move to `HomeViewModelHelpers.kt` — operates on DAOs directly, emits events back via callback |
| `markVideoAsRead()` | Identical in both — keep in each (trivial 3-line delegation to DAO) |
| `resetVideoProgress()` | Identical in both — keep in each (trivial) |
| `deleteSubtitle()` | Identical in both — keep in each |
| `restoreLibraryItem()` | Identical in both — keep in each |
| `loadCollections()` | Identical in both — keep in each (needed for the AddToCollectionDialog) |
| `createCollection()` | Keep in both (needed for AddToCollectionDialog from both Library and CollectionDetail) |
| `addVideoToCollection()` | Keep in both (needed for AddToCollectionDialog from both Library and CollectionDetail) |

For the complex ones (`downloadSubtitleAgain`, `downloadThumbnailForVideo`), extract to helpers:

```kotlin
// HomeViewModelHelpers.kt
internal suspend fun redownloadSubtitle(
    subtitle: SubtitleEntity,
    youtubeRepository: YoutubeRepository,
    subtitleDao: SubtitleDao,
    highlightNoteDao: HighlightNoteDao,
    bookmarkDao: BookmarkDao,
    videoDao: VideoDao,
    collectionRepository: CollectionRepository,
    appContext: Context,
    onStateUpdate: (suspend (Set<Long>) -> Set<Long>) -> Unit,  // updates downloadingSubtitleIds
    onError: (String) -> Unit,
    onSuccess: () -> Unit
)
```

Or more simply: keep the method bodies in both ViewModels but extract the shared sub-operations (`upsertVideoMetadata`, `deleteVideoIfUnreferenced`, `observeLibraryItemsForRows`) to helper functions. The 5-6 duplicated trivial methods (3-5 lines each) are acceptable duplication vs. the indirection of callbacks.

---

## Phase 5: Delete HomeViewModel

After Phases 1-4 are complete:

1. Delete `ui/home/HomeViewModel.kt`
2. Delete `HomeUiState` data class
3. Delete `HomeEvent` sealed interface
4. Delete `HomeViewModel.provideFactory()` companion object
5. Remove `HomeViewModel` import from `MainScreen.kt`

---

## Phase 6: Update Tests

### 6.1 New test: `SearchViewModelTest.kt`

Test cases:
- `searchVideo()` — success path: sets loading, fetches stream info, saves to history
- `searchVideo()` — error path: sets error message
- `searchVideo()` — blank URL: does nothing
- `onUrlChange()` — updates URL state, clears error, hides history
- `downloadSubtitle()` — success: upserts entity, upserts video metadata
- `downloadSubtitle()` — error: sets error message
- `toggleFavoriteLanguage()` — delegates to repository
- `deleteHistoryEntry()` — calls DAO delete
- `searchFromHistory()` — fills URL and calls search
- `saveToSearchHistory()` — caps at 100 entries

### 6.2 New test: `LibraryViewModelTest.kt`

Test cases:
- `setChannelFilter()` — updates state and persists
- `setLibraryVisibilityFilter()` — updates state and persists
- `setSortOption()` / `toggleSortOrder()` — updates state and persists
- `getPreferredSubtitleIdForVideo()` — returns subtitle ID
- `downloadSubtitleAgain()` — success and error paths
- `downloadThumbnailForVideo()` — success and error paths
- `removeLibraryItem()` — hides from library, deletes video if unreferenced
- `restoreLibraryItem()` — restores visibility
- `resetVideoProgress()` / `markVideoAsRead()` — delegates to DAO
- `deleteVideoPermanently()` — removes from collections and deletes subtitles
- `deleteSubtitle()` — deletes and cleans up if last subtitle for video
- `createCollection()` / `addVideoToCollection()` — validation + delegation
- `exportEpub()` — calls export function

### 6.3 New test: `CollectionsViewModelTest.kt`

Test cases:
- `createCollection()` — blank name rejected, duplicate name rejected
- `renameCollection()` — blank name rejected, duplicate name rejected
- `deleteCollection()` — removes filter state + delegates
- `reorderCollections()` — validates input, delegates
- Collection filter state management (`setCollectionChannelFilter`, etc.)
- `removeVideoFromCollection()` — removes and cleans up unreferenced videos
- `observeCollectionItems()` / `observeCollectionChannels()` — reactive flow

---

## File Change Summary

### New Files
| File | Purpose |
|---|---|
| `ui/home/SearchViewModel.kt` | Search + subtitle download ViewModel |
| `ui/home/LibraryViewModel.kt` | Library screen ViewModel |
| `ui/home/CollectionsViewModel.kt` | Collections + CollectionDetail ViewModel |
| `ui/home/HomeViewModelHelpers.kt` | Shared internal helper functions |
| `test/.../SearchViewModelTest.kt` | Unit tests |
| `test/.../LibraryViewModelTest.kt` | Unit tests |
| `test/.../CollectionsViewModelTest.kt` | Unit tests |

### Modified Files
| File | Changes |
|---|---|
| `ui/MainScreen.kt` | Replace single `HomeViewModel` with 3 ViewModel instances; update all composable call sites |
| `ui/home/SearchScreen.kt` | Change `viewModel: HomeViewModel` → `viewModel: SearchViewModel` |
| `ui/home/LibraryScreen.kt` | Change `viewModel: HomeViewModel` → `viewModel: LibraryViewModel` |
| `ui/home/CollectionsScreen.kt` | Change `viewModel: HomeViewModel` → `viewModel: CollectionsViewModel` |
| `ui/home/CollectionDetailScreen.kt` | Change `viewModel: HomeViewModel` → `viewModel: CollectionsViewModel` |

### Deleted Files
| File | Reason |
|---|---|
| `ui/home/HomeViewModel.kt` | Replaced by the three new ViewModels |

---

## Execution Order & Risk Mitigation

### Step-by-step execution order

1. **Create `HomeViewModelHelpers.kt`** — extract shared functions, compile-test
2. **Create `SearchViewModel.kt`** — move search-only state and methods
3. **Update `SearchScreen.kt`** and `MainScreen.kt` for SearchViewModel
4. **Compile and test search flow** — verify search, download, history still work
5. **Create `LibraryViewModel.kt`** — move library-only state and methods
6. **Update `LibraryScreen.kt`** and `MainScreen.kt` for LibraryViewModel
7. **Compile and test library flow** — verify filters, sort, download-again, thumbnails
8. **Create `CollectionsViewModel.kt`** — move collection-only state and methods
9. **Update `CollectionsScreen.kt`**, `CollectionDetailScreen.kt`, and `MainScreen.kt`
10. **Compile and test collections flow** — verify CRUD, reorder, filters
11. **Delete `HomeViewModel.kt`** — only after all 3 ViewModels are verified
12. **Write unit tests** for all three new ViewModels
13. **Run full test suite** — `./gradlew test` + `./gradlew connectedAndroidTest`

### Risk mitigation

- **State isolation**: Each ViewModel holds only its own state. No risk of cross-screen state pollution.
- **`collections` list used by LibraryScreen's AddToCollectionDialog**: Both `LibraryViewModel` and `CollectionsViewModel` need to observe `collectionRepository.collections`. This is fine — it's a shared `Flow` from a singleton repository, so both VMs get the same data.
- **`downloadSubtitleAgain` deletes annotations (the existing data-loss bug)**: This is a pre-existing issue documented in the codebase improvements plan. Fix it in the extracted helper function as part of this refactor, wrapping the delete + replace in a Room `@Transaction`.
- **`getPreferredSubtitleIdForVideo()` used by MainScreen**: Move to `LibraryViewModel` since it queries the subtitle DAO for a library navigation concern. MainScreen passes it as a lambda to both Library and Collection screens.
- **Backward compatibility**: The `HomeUiState`, `HomeEvent`, and `HomeViewModel` types are only used internally within the app module. No external API surface changes.

### Verification checklist

After all changes:
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] `./gradlew :app:connectedAndroidTest` passes
- [ ] Search: type URL → get stream info → download subtitle → open in reader
- [ ] Library: filter by channel → sort → download-again → remove → undo
- [ ] Collections: create → add video → reorder → delete
- [ ] Collection detail: filter → sort → remove from collection → open reader → back
- [ ] No `HomeViewModel` references remain in codebase
