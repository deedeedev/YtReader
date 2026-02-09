---
status: pending
created: 2026-02-09
started: null
completed: null
---
# Task: Implement Swipe to Delete UI

## Description
Implement the "swipe-to-delete" gesture in the Library view. This is the user-facing part of the deletion feature.

## Background
The user wants to delete items from the library list using a swipe gesture.

## Reference Documentation
**Required:**
- Design: specs/swipe-to-delete-library/design.md

## Technical Requirements
1.  Open `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt`.
2.  Wrap `LibraryItemCard` (or its usage in `items`) with `SwipeToDismissBox` (or `SwipeToDismiss`).
3.  Set the background to be revealed during the swipe (e.g., red color with a delete/trash icon).
4.  Handle the dismissal action by calling `viewModel.deleteLibraryItem(item)`.
5.  Use an appropriate threshold for dismissal (e.g., 50% or standard).

## Dependencies
- `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt`
- Task 2: Update HomeViewModel must be complete.
- Material3 icons (e.g., `Icons.Default.Delete`).

## Implementation Approach
1.  Identify the correct Material3 `SwipeToDismiss` component available in the project.
2.  Wrap the `LibraryItemCard` with the swipeable container.
3.  Configure the `SwipeToDismissBoxState` (or similar) to remember the dismissal state.
4.  Implement the `backgroundContent` for the swipe (red background + icon).
5.  Connect the `onDismissed` (or similar) callback to the ViewModel.

## Acceptance Criteria

1.  **Swipe Gesture**
    - Given a library item card.
    - When the user swipes it from right to left (end to start).
    - Then the card moves with the finger.
    - And a red background with a delete icon is revealed.

2.  **Delete Action**
    - Given the swipe passes the dismissal threshold.
    - When the user releases the card.
    - Then the card is dismissed (removed from view).
    - And the item is deleted from the database.

3.  **Visual Feedback**
    - Given the user swipes partially.
    - When they release before the threshold.
    - Then the card snaps back to its original position without deleting.

## Metadata
- **Complexity**: Medium
- **Labels**: ui, compose, material3, library
- **Required Skills**: Android, Kotlin, Jetpack Compose
