# Search in Original Mode from Study Selection

## Summary

Allow the user to select text in **Study Mode**, search it in the **Original Mode** subtitle segments, and navigate to the exact matching segment. Results are shown in a dialog with timestamps.

## User Flow

1. User is in **Study Mode** reading cleaned/edited subtitle text.
2. User **long-presses** to select a word, then drags to extend the selection.
3. The **Highlight Selection Toolbar** appears at the bottom with color buttons, note, and delete.
4. User taps the new **Search in Original** button (magnifying glass icon).
5. The app takes the selected text, escapes regex special characters, and performs a **case-insensitive literal search** across all original subtitle segments (`SubtitleSegment` list parsed from `SubtitleEntity.content`).
6. A **dialog** opens showing matching segments — one result per segment (first occurrence only), each with:
   - **Timestamp** (formatted as `HH:MM:SS` from `SubtitleSegment.startTime` milliseconds)
   - **Text excerpt** with the matched portion highlighted in context
7. User taps a result:
   - App **switches to Original Mode**.
   - Scrolls the `LazyColumn` to the exact **segment index**.
   - The matching text range within that segment is **highlighted** using `BackgroundColorSpan`.
   - A **JumpBack toolbar** appears so the user can return to the previous Study Mode position.
8. User can tap **Jump Back** to return to the exact scroll position in Study Mode.

## Implementation Details

### 1. New Data Class — `SearchInOriginalResult`

**File:** `ReaderTypes.kt`

```kotlin
internal data class SearchInOriginalResult(
    val segmentIndex: Int,
    val startTime: Long,
    val excerpt: String,
    val matchStart: Int,
    val matchEnd: Int
)
```

Fields:
- `segmentIndex` — index in the `List<SubtitleSegment>` used by the Original Mode `LazyColumn`.
- `startTime` — segment start time in milliseconds (from `SubtitleSegment.startTime`).
- `excerpt` — short text excerpt around the match (built with the existing `buildFindExcerpt` pattern).
- `matchStart` / `matchEnd` — character offsets of the match within the segment text, used to apply a highlight span when navigating.

### 2. Search Function — `findLiteralInSegments`

**File:** `ReaderFind.kt`

```kotlin
internal fun findLiteralInSegments(
    query: String,
    segments: List<SubtitleSegment>,
    excerptEllipsis: String
): List<SearchInOriginalResult>
```

Logic:
1. If `query` is blank or `segments` is empty → return empty list.
2. Escape the query for literal regex matching: `Regex.escape(query.trim())`.
3. Compile with `RegexOption.IGNORE_CASE`.
4. Iterate over `segments` with `forEachIndexed`:
   - Run `regex.find(segment.text)` — only the **first** match per segment.
   - If found, compute excerpt using the existing `FIND_EXCERPT_RADIUS` pattern.
   - Build a `SearchInOriginalResult` with the segment's `startTime`, the match offsets, and the excerpt.
5. Return the full result list.

This function is pure (no Android dependencies) and can be unit-tested independently.

### 3. State in `ReaderScreen.kt`

New state variables:

```kotlin
var searchInOriginalResults by remember { mutableStateOf<List<SearchInOriginalResult>>(emptyList()) }
var showSearchInOriginalDialog by remember { mutableStateOf(false) }
var searchInOriginalQuery by remember { mutableStateOf("") }
```

These are local UI state (not in `ReaderUiState` / ViewModel) because they are transient dialog state, consistent with how the existing Find dialog state is managed.

### 4. Trigger — Search Button in Selection Toolbar

**File:** `ReaderUiComponents.kt` — `HighlightSelectionToolbar`

Add a new parameter:

```kotlin
showSearchInOriginal: Boolean,
onSearchInOriginal: () -> Unit
```

Add a `FilledTonalButton` with `Icons.Filled.Search` icon **before** the note button. The button is only shown when `showSearchInOriginal` is `true`.

**File:** `ReaderScreenLayout.kt` and `ReaderOverlayHost.kt`

Thread the new parameters through the composable tree:
- Pass `showSearchInOriginal = (currentMode == ReaderMode.STUDY && !isEditing)`
- Pass `onSearchInOriginal` handler.

**File:** `ReaderScreen.kt` — handler

```kotlin
onSearchInOriginal = {
    val query = content.substring(selectionRange.start, selectionRange.end)
    val segments = SubtitleParser.parseToSegments(uiState.subtitle?.content ?: "")
    val results = findLiteralInSegments(query, segments, "…")
    searchInOriginalQuery = query
    searchInOriginalResults = results
    showSearchInOriginalDialog = true
}
```

The selection toolbar is dismissed when the dialog opens (set `selectionRange = null`).

### 5. Dialog — `SearchInOriginalDialog`

**File:** `ReaderDialogs.kt`

New composable:

```kotlin
@Composable
internal fun SearchInOriginalDialog(
    query: String,
    results: List<SearchInOriginalResult>,
    segments: List<SubtitleSegment>,
    onSelectResult: (SearchInOriginalResult) -> Unit,
    onDismiss: () -> Unit
)
```

Layout:
- `AlertDialog` with scrollable content.
- **Title:** "Search results for '…'" (truncated query).
- If `results` is empty: show "No results found" message.
- Otherwise: `LazyColumn` of result rows.
- **Dismiss button:** "Close".

Each result row (reuse `Surface` + `Column` pattern from existing `FindResultRow`):

```
┌─────────────────────────────────┐
│ 02:34                           │  ← timestamp, labelMedium, secondary color
│ …preceding text **matched**     │  ← excerpt with bold match
│ following text…                 │
│ Segment 5 of 42                 │  ← segment number, labelSmall, tertiary
└─────────────────────────────────┘
```

- Timestamp: `formatTime(result.startTime)` — reuse existing `formatTime()` from `ReaderUiUtils.kt`.
- Excerpt: the `result.excerpt` string (match is already in context).
- Segment indicator: `"${result.segmentIndex + 1} / ${segments.size}"` for context.
- Row is `clickable { onSelectResult(result) }`.

### 6. Navigation on Result Selection

**File:** `ReaderScreen.kt` — handler for `onSelectResult`

When the user taps a result:

```kotlin
onSelectResult = { result ->
    showSearchInOriginalDialog = false

    // 1. Register jump-back (saves current study mode position)
    registerProgrammaticJump(ReaderJumpReason.SEARCH)

    // 2. Set pending action to switch to Original Mode
    pendingAction = PendingAction.SwitchMode(ReaderMode.ORIGINAL)

    // 3. Set pending find selection to highlight the match in the segment
    pendingFindSelection = PendingFindSelection.OriginalSegment(
        segmentIndex = result.segmentIndex,
        start = result.matchStart,
        end = result.matchEnd
    )

    // 4. Switch mode (this triggers mode change and the pending effects)
    currentMode = ReaderMode.ORIGINAL
}
```

The existing `ReaderCoreEffects` (`ReaderScreenEffects.kt`) already handles:
- `PendingAction.SwitchMode` → triggers mode transition.
- `pendingFindSelection` of type `OriginalSegment` → scrolls to the segment and applies a `BackgroundColorSpan` highlight using `searchResultSpanColor()`.

The `registerProgrammaticJump()` call saves a `ReaderLocation` with `ReaderAnchor.Study(anchorStart = currentScrollPosition)`, which enables the **JumpBack toolbar** to appear.

The `SearchResultsMode.OriginalSegment` state is **not** set here — the search-in-original flow is self-contained via the dialog + jump-back, and doesn't need the prev/next results toolbar. The jump-back toolbar is sufficient for returning.

### 7. Jump Back Behavior

The existing jump-back system in `ReaderScreen.kt` already handles this:
- `registerProgrammaticJump(ReaderJumpReason.SEARCH)` captures the current `ReaderLocation` (Study mode scroll position).
- After navigating to the original segment, `jumpBackState` is non-null → `JumpBackToolbar` is shown.
- `onJumpBack` calls `jumpBackTo()` which restores Study Mode and scrolls to the saved anchor.

No changes needed to the jump-back system itself.

### 8. String Resources

**File:** `res/values/strings.xml`

```xml
<string name="reader_search_in_original">Search in original</string>
<string name="reader_search_in_original_title">Results for \"%1$s\"</string>
<string name="reader_search_in_original_no_results">No results found in original subtitles</string>
<string name="reader_search_in_original_close">Close</string>
<string name="reader_search_in_original_segment">Segment %1$d of %2$d</string>
```

### 9. Test Coverage

**File:** New test class `ReaderFindTest.kt` (or extend existing test file for `ReaderFind.kt`)

Tests for `findLiteralInSegments`:
1. **Empty query** → returns empty list.
2. **Blank/whitespace query** → returns empty list.
3. **No matching segments** → returns empty list.
4. **Single match in one segment** → returns one result with correct offsets.
5. **Multiple segments with matches** → returns one result per segment (first occurrence only).
6. **Case insensitive matching** → query "hello" matches "Hello" and "HELLO".
7. **Special regex characters in query** → query "hello." matches literal "hello." not "helloX".
8. **Multiple matches in same segment** → returns only one result for that segment (the first match).
9. **Empty segment list** → returns empty list.

## Files Changed — Summary

| File | Change Type | Description |
|---|---|---|
| `ui/reader/ReaderTypes.kt` | Add | `SearchInOriginalResult` data class |
| `ui/reader/ReaderFind.kt` | Add | `findLiteralInSegments()` function |
| `ui/reader/ReaderUiComponents.kt` | Modify | Add Search button to `HighlightSelectionToolbar` |
| `ui/reader/ReaderDialogs.kt` | Add | `SearchInOriginalDialog` composable |
| `ui/reader/ReaderScreen.kt` | Modify | State, handlers, dialog integration |
| `ui/reader/ReaderScreenLayout.kt` | Modify | Thread new parameters to overlay host |
| `ui/reader/ReaderOverlayHost.kt` | Modify | Thread new parameters to toolbar |
| `res/values/strings.xml` | Add | 5 new string resources |
| Unit tests | Add | Tests for `findLiteralInSegments` |

## Edge Cases

- **No subtitle content**: If `uiState.subtitle?.content` is null or empty, the segments list will be empty and the dialog will show "No results found".
- **Study content differs from original**: The study text may have been AI-cleaned or manually edited, so the search may find different (or no) matches in the original. This is expected.
- **Very long selection**: The dialog title truncates the query with ellipsis if too long. The excerpt in each result row uses the existing `FIND_EXCERPT_RADIUS` (24 chars) for context.
- **Selection across paragraph breaks**: In study mode, the selected text may contain newlines. These are included in the literal search, which means they would need to match exactly in the original segment text. Since original segments typically don't span multiple lines, multi-line selections will likely return no results — this is acceptable behavior.
- **Mode already Original**: The search button is only shown when `currentMode == ReaderMode.STUDY`, so this case doesn't arise.
