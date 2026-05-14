# Archived Collection Feature Design

## Summary

Add a fixed "Archived" system collection that allows users to archive videos from the Library. Archived videos are hidden from the Library and only visible in the Archived collection. Users can unarchive to restore them.

## Approach

Use a fixed collection with well-known ID (`__archived__`). This reuses all existing collection infrastructure (detail screen, video listing, filters, export) without schema changes.

## Data Layer

### Constants

```kotlin
const val ARCHIVED_COLLECTION_ID = "__archived__"
const val ARCHIVED_COLLECTION_NAME = "Archived"
```

### CollectionEntity

A standard `CollectionEntity` with:
- `id` = `"__archived__"`
- `name` = `"Archived"`
- `sortOrder` = `-1` (always first)
- Created on first app launch if missing

### CollectionRepository additions

- `ensureArchivedCollectionExists()` - inserts the fixed collection if not present. Called during app initialization alongside `migrateLegacyCollectionsIfNeeded()`.
- `archiveVideo(videoId)` - normalizes video ID, sets `isInLibrary = false` on all subtitles for the video via `SubtitleRepository.updateLibraryVisibility()`, then adds video to `__archived__` collection via `insertCollectionVideo()`.
- `unarchiveVideo(videoId)` - normalizes video ID, removes from `__archived__` collection, then sets `isInLibrary = true` via `SubtitleRepository.updateLibraryVisibility()`.
- `isArchivedCollection(id)` - returns `id == ARCHIVED_COLLECTION_ID`.

### Initialization

In `DefaultAppContainer.runMigrations()`, call `collectionRepository.ensureArchivedCollectionExists()`.

## UI Layer

### Library Screen - Archive Action

**LibraryItemCard.kt**:
- Add "Archive" menu item in the long-press dropdown menu, positioned after "Add to Collection".
- Only shown when the video card is in the Library context (not in a collection detail view). Use the existing `onRemoveFromLibrary != null` as the condition.
- Calls a new `onArchive: ((videoId: String) -> Unit)?` callback.

**LibraryViewModel**:
- Add `archiveVideo(videoId: String)` which calls `collectionRepository.archiveVideo(videoId)`.
- No undo snackbar needed (the video is recoverable from the Archived collection).

### Collections Screen - Archived Collection Behavior

**CollectionsScreen.kt**:
- Archived collection always appears first (sortOrder = -1 ensures this from the DB query ordering).
- **Not draggable**: Skip this collection in the drag-and-drop state.
- **Not editable**: Hide "Rename" in overflow menu when `collection.id == ARCHIVED_COLLECTION_ID`.
- **Not deletable**: Hide "Delete" in overflow menu when `collection.id == ARCHIVED_COLLECTION_ID`.
- Same visual card layout as other collections.

### Collection Detail Screen - Unarchive Action

**CollectionDetailScreen.kt** for the Archived collection:
- Videos show with normal actions: mark as read, export ePub, download thumbnail, reset progress, etc.
- "Remove from Collection" is hidden for the Archived collection.
- "Unarchive" option is shown instead. Calls `unarchiveVideo(videoId)`.
- "Restore to Library" is not shown (unarchive handles both removal and library restore).
- "Delete permanently" is available.

**LibraryItemCard.kt**:
- Add `onUnarchive: ((videoId: String) -> Unit)?` callback.
- When in the Archived collection context, show "Unarchive" menu item.

### CollectionsViewModel

- Add `unarchiveVideo(videoId: String)` which calls `collectionRepository.unarchiveVideo(videoId)`.

## Files to Modify

| File | Changes |
|------|---------|
| `data/CollectionRepository.kt` | Add `ensureArchivedCollectionExists()`, `archiveVideo()`, `unarchiveVideo()`, `isArchivedCollection()` |
| `AppContainer.kt` | Call `ensureArchivedCollectionExists()` during initialization |
| `ui/home/LibraryItemCard.kt` | Add "Archive" and "Unarchive" menu items with callbacks |
| `ui/home/LibraryScreen.kt` | Wire `onArchive` callback to ViewModel |
| `ui/home/LibraryViewModel.kt` | Add `archiveVideo()` method |
| `ui/home/CollectionsScreen.kt` | Disable drag/edit/delete for archived collection |
| `ui/home/CollectionsViewModel.kt` | Add `unarchiveVideo()` method |
| `ui/home/CollectionDetailScreen.kt` | Wire unarchive action for archived collection |

## User Flow

1. User long-presses a video card in Library -> sees "Archive" option
2. Taps "Archive" -> video disappears from Library, appears in Archived collection
3. Opens Archived collection -> sees archived videos
4. Long-presses an archived video -> sees "Unarchive" option
5. Taps "Unarchive" -> video returns to Library, removed from Archived collection
