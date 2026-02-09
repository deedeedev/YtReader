---
status: pending
created: 2026-02-09
started: null
completed: null
---
# Task: Update HomeViewModel

## Description
Update the HomeViewModel to expose a method for deleting a library item (video) from the database.

## Background
The UI layer needs a way to delete items. The ViewModel manages the interaction with the database.

## Reference Documentation
**Required:**
- Design: specs/swipe-to-delete-library/design.md

## Technical Requirements
1.  Add `deleteLibraryItem(item: LibraryItem)` function to `HomeViewModel`.
2.  Use `viewModelScope.launch` to call `subtitleDao.deleteByVideoId`.
3.  The UI state should automatically update via the `subtitleDao.getAll()` flow already collected in the `init` block.

## Dependencies
- `app/src/main/java/com/deedeedev/ytreader/ui/home/HomeViewModel.kt`
- Task 1: Update DAO must be complete.

## Implementation Approach
1.  Open `HomeViewModel.kt`.
2.  Add the `deleteLibraryItem` function.
3.  Ensure thread safety and coroutine scope usage.

## Acceptance Criteria

1.  **Delete Library Item**
    - Given a valid `LibraryItem` with a `videoId`.
    - When `deleteLibraryItem` is called.
    - Then the corresponding `SubtitleDao.deleteByVideoId` is invoked with the correct ID.

2.  **UI Updates**
    - Given the user calls delete.
    - When the database operation completes.
    - Then the `uiState.savedSubtitles` list is updated to reflect the deletion (since it's a flow).

## Metadata
- **Complexity**: Low
- **Labels**: viewmodel, logic, database
- **Required Skills**: Android, Kotlin, ViewModel
