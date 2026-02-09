# Swipe to Delete Feature Implementation

## Objective
Implement a "swipe-to-delete" feature for library items in the main library view.

## Execution Order
1. specs/swipe-to-delete-library/tasks/task-01-update-dao.code-task.md
2. specs/swipe-to-delete-library/tasks/task-02-update-viewmodel.code-task.md
3. specs/swipe-to-delete-library/tasks/task-03-update-library-screen.code-task.md

## Acceptance Criteria
- **Data Layer**:
  - `SubtitleDao` must have a method to delete all subtitles for a given `videoId`.
  - The method uses a proper SQL query (`DELETE FROM subtitles WHERE videoId = :videoId`).

- **Logic Layer**:
  - `HomeViewModel` must expose a `deleteLibraryItem(item: LibraryItem)` function.
  - This function calls the DAO method.
  - The UI state automatically reflects changes via the collected flow.

- **UI Layer**:
  - Library items (cards) are swipeable (end-to-start / right-to-left).
  - A red background with a delete icon is revealed during the swipe.
  - Completing the swipe triggers the delete action and removes the item from the list.
  - Partial swipes snap back without deleting.
