# Design: Swipe to Delete Library Item

## Problem
Currently, there is no easy way for users to remove videos and their associated subtitles from the library view.

## Solution
Implement a "swipe-to-delete" gesture on the library items (video cards). Swiping an item to the left should reveal a delete action (red background with trash icon) and, upon completion, remove the item and its data from the database.

## Technical Implementation

### Data Layer
- **File:** `app/src/main/java/com/deedeedev/ytreader/data/local/SubtitleDao.kt`
- **Change:** Add a `@Query` method to delete all subtitles associated with a specific `videoId`.
  ```kotlin
  @Query("DELETE FROM subtitles WHERE videoId = :videoId")
  suspend fun deleteByVideoId(videoId: String)
  ```

### ViewModel
- **File:** `app/src/main/java/com/deedeedev/ytreader/ui/home/HomeViewModel.kt`
- **Change:** Add a function `deleteLibraryItem(item: LibraryItem)` that calls the new DAO method.
  ```kotlin
  fun deleteLibraryItem(item: LibraryItem) {
      viewModelScope.launch {
          subtitleDao.deleteByVideoId(item.videoId)
          // The UI state will update automatically via the Flow collector
      }
  }
  ```

### UI Layer
- **File:** `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt`
- **Change:** Wrap the `LibraryItemCard` composable (or its usage in the list) with `SwipeToDismissBox` (or `SwipeToDismiss` depending on Material3 version availability).
- **Behavior:**
  - Swipe direction: End to Start (Right to Left).
  - Background: Red color with a delete icon.
  - Action: Trigger `viewModel.deleteLibraryItem(item)` when the swipe threshold is met.

## User Experience
- User swipes a card left.
- A red background appears behind the card.
- If swiped far enough, the card is dismissed.
- The item is removed from the list immediately.
