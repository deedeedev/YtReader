# Fix: Jump-Back Toolbar Not Showing After Annotation Navigation

## Problem
When the user selects an annotation from the "Highlights & Notes" (VideoNotes) sheet, the Reader navigates to the annotation but the jump-back button doesn't appear in the UI.

## Root Cause
In `ReaderScreenEffects.kt:64-66`, `LaunchedEffect(uiContent)` calls `clearSelectionState()` every time `uiContent` changes. The `clearSelectionState` callback in `ReaderScreen.kt:834-843` includes a `clearJumpBackState()` call. When navigating from VideoNotes back to Reader with an annotation target:

1. A new Reader composition is created with `initialJumpBackState` (non-null)
2. `jumpBackState` is initialized from `initialJumpBackState`
3. The ViewModel loads subtitle content, causing `uiContent` to change (null -> actual text)
4. `LaunchedEffect(uiContent)` fires → `clearSelectionState()` → `clearJumpBackState()`
5. `jumpBackState` becomes null → `showJumpBackToolbar` = false

## Fix
Remove `clearJumpBackState()` from the `clearSelectionState` callback (line 842 in `ReaderScreen.kt`). It's already called explicitly where needed:
- `onEnterEditing` callback (line 863)
- `closeSearchResults()` function (line 341-343, conditionally for SEARCH reason)
- `jumpBackTo()` function (lines 366, 391, 408)
- `BackHandler` (implicitly via `jumpBackTo`)

Mode switching callbacks (`onReaderModeChangedToOriginal`, `onReaderModeChangedToStudy`) don't need it — `jumpBackTo()` already handles mode changes internally.

## File Changes
1. `app/src/main/java/com/deedeedev/ytreader/ui/reader/ReaderScreen.kt` — Remove `clearJumpBackState()` from `clearSelectionState` callback (line 842)

## Verification
Run: `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest`
