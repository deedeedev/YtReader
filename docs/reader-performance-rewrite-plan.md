# Reader Performance Rewrite Plan

## 1. Problem Statement

When opening long transcripts (30+ pages), the Study Mode reader becomes sluggish:
scroll stutters, text selection lags, and highlight drawing is slow. The root
cause is architectural: the entire text is rendered as a single monolithic view.

## 2. Root Cause Analysis

### 2.1 Single StaticLayout for the Entire Text

`JustifiedStudyTextView.kt:439-449` builds one `StaticLayout` spanning the
entire content:

```kotlin
StaticLayout.Builder
    .obtain(content, 0, content.length, textPaint, width)
    .setBreakStrategy(BREAK_STRATEGY_HIGH_QUALITY)
    .setHyphenationFrequency(HYPHENATION_FREQUENCY_NORMAL)
    .build()
```

`BREAK_STRATEGY_HIGH_QUALITY` is the most expensive break strategy. For a text
that produces 30+ screen pages, the layout contains thousands of lines and
takes significant time to build. It is rebuilt whenever `layoutDirty` is set or
the view width changes.

### 2.2 No Virtualization (Column + verticalScroll)

`ReaderContentPanes.kt:298-311` uses:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(studyScrollState)
) {
    AndroidView<JustifiedStudyTextView>(...)
}
```

`Column + verticalScroll` materializes all children. The single
`JustifiedStudyTextView` is always measured at its full content height (tens of
thousands of pixels). There is no lazy loading: the entire view hierarchy for
the full text exists in memory even if only one screenful is visible.

### 2.3 Full Redraw on Every Frame

`JustifiedStudyTextView.onDraw()` (`JustifiedStudyTextView.kt:112-133`) redraws
everything on every `invalidate()`:

- `drawHighlightBackgrounds()` iterates **all highlights**, even those
  off-screen, computing line ranges for each.
- `drawBookmarkIndicators()` computes bounds for **all bookmarks**.
- `drawHighlightNoteIndicators()` iterates **all highlights with notes**.
- `textLayout.draw(canvas)` draws all lines (Canvas clips, but the iteration
  over line metadata still occurs).
- During text selection (`ACTION_MOVE`), every movement triggers `invalidate()`
  → full redraw of the entire text.

### 2.4 Large StaticLayout → Slow Break Calculation

`BREAK_STRATEGY_HIGH_QUALITY` uses a paragraph-level optimization pass that
considers the entire paragraph for optimal line breaks. For long paragraphs
(common in transcripts), this is O(n²) in paragraph length. The strategy is
disproportionately expensive for the marginal quality improvement it offers
over `BREAK_STRATEGY_BALANCED`.

## 3. Architecture Target

```
┌──────────────────────────────────────────────────┐
│  LazyColumn (only visible + buffer chunks)       │
│  ┌────────────────────────────────────────────┐  │
│  │ Chunk 0: JustifiedStudyTextView            │  │
│  │   text = paragraphs[0..N]                  │  │
│  │   globalTextOffset = 0                     │  │
│  ├────────────────────────────────────────────┤  │
│  │ Chunk 1: JustifiedStudyTextView            │  │
│  │   text = paragraphs[N+1..M]                │  │
│  │   globalTextOffset = paragraphs[0..N].len  │  │
│  ├────────────────────────────────────────────┤  │
│  │ ...                                        │  │
│  ├────────────────────────────────────────────┤  │
│  │ Chunk K: JustifiedStudyTextView            │  │
│  │   text = paragraphs[X..end]                │  │
│  │   globalTextOffset = ...                   │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  StudySelectionCoordinator (cross-chunk select)  │
│  StudyTextPaginator (pre-computed chunk splits)  │
└──────────────────────────────────────────────────┘
```

Only 2-3 chunks (visible + 1 buffer) are in memory at any time. From 30+
rendered pages to ~2-3. Each chunk's `StaticLayout` is small and fast.

## 4. New Files

### 4.1 `StudyTextPaginator.kt`

**Purpose:** Pre-compute chunk boundaries from the full text, using paragraph
breaks as split points.

**Data model:**

```kotlin
internal data class TextChunk(
    val index: Int,
    val text: String,
    val globalStartOffset: Int,
    val globalEndOffset: Int
)

internal data class StudyTextPagination(
    val chunks: List<TextChunk>,
    val fullText: String
)
```

**Algorithm:**

1. Build a **measurement `StaticLayout`** for the full text using
   `BREAK_STRATEGY_BALANCED` (not `HIGH_QUALITY`) with a temporary `TextPaint`
   configured to match the study view's paint settings.
2. Walk the layout line-by-line, tracking cumulative height.
3. When cumulative height exceeds `targetChunkHeightPx` (≈ viewport height),
   find the nearest paragraph break (`\n\n`) at or before the current position.
4. Split there. The text from the previous split point up to this paragraph
   break becomes one chunk.
5. If no paragraph break is found within a reasonable window (e.g., 1.5x
   viewport height), split at the current line to avoid unbounded chunk growth.
6. Return `StudyTextPagination` with all chunks.

**Caching:**

- Compute once, cache the result.
- Invalidate and recompute when: text content, font size, font family, line
  height multiplier, or viewport width changes.
- The measurement layout can optionally be discarded after chunk computation to
  free memory (only the chunk text strings are kept).

**Location:** `app/src/main/java/com/deedeedev/ytreader/ui/reader/StudyTextPaginator.kt`

**Testability:** Pure computation (no Android UI). Unit test with known text
inputs and expected chunk boundaries.

### 4.2 `StudySelectionCoordinator.kt`

**Purpose:** Manage text selection across chunk boundaries, mirroring the
existing `OriginalSelectionCoordinator` pattern.

**Data model:**

```kotlin
internal class StudySelectionCoordinator {
    val textViews = mutableMapOf<Int, JustifiedStudyTextView>()
    var activeChunkIndex: Int? = null

    fun clearAllSelections()
    fun setSelectionForChunk(chunkIndex: Int, start: Int, end: Int)
    fun clearSelectionForChunk(chunkIndex: Int)
}
```

**Responsibilities:**

- Track which chunk owns the active selection (`activeChunkIndex`).
- When a selection starts in chunk N and the user drags into chunk N+1:
  - Translate the selection anchor from chunk-local to global coordinates.
  - Extend the selection into the new chunk via its `JustifiedStudyTextView`.
  - Clear the selection in the old chunk if the range no longer intersects it.
- When the user lifts the finger, report the **global** selection range
  (local offset + `globalTextOffset`) to the compose layer via
  `onSelectionChangedListener`.
- When a highlight/bookmark is tapped, report global coordinates.
- When `clearAllSelections()` is called (e.g., mode switch), clear all tracked
  views.

**Touch handling for cross-chunk selection:**

The tricky case is when the user's finger moves from one chunk to an adjacent
chunk during a drag. The approach:

1. Each `JustifiedStudyTextView` reports `ACTION_MOVE` events to the
   coordinator.
2. The coordinator checks if the touch Y position is near the boundary of the
   current chunk.
3. If the touch crosses into an adjacent chunk, the coordinator:
   - Finds the target offset in the adjacent chunk.
   - Extends the selection globally.
   - Calls `setSelectionRange()` on the appropriate views.

An alternative (simpler) approach: Use `LazyListState.layoutInfo` to detect
which chunks are visible and forward touch events from a transparent overlay
composable that sits on top of the LazyColumn. This avoids modifying the touch
handling in `JustifiedStudyTextView` at all, but requires a separate touch
target.

**Decision:** Start with the simpler approach of forwarding touch coordinates to
the appropriate chunk view via the coordinator. If it proves insufficient
(especially for fast drags), switch to the overlay approach.

**Location:** `app/src/main/java/com/deedeedev/ytreader/ui/reader/StudySelectionCoordinator.kt`

## 5. Modified Files

### 5.1 `JustifiedStudyTextView.kt` — Chunk-Aware Adaptation

**Changes:**

1. **New property:** `globalTextOffset: Int` (default 0).
   - All offsets reported to external listeners are translated:
     `localOffset + globalTextOffset`.
   - Offsets received from outside (highlights, bookmarks, search results) are
     translated to local: `globalOffset - globalTextOffset`.

2. **`setContentWithHighlights()` changes:**
   - Filter highlights to only those overlapping
     `[globalTextOffset, globalTextOffset + content.length)`.
   - Translate highlight start/end to local offsets.
   - Filter bookmarks to only those within range, translate anchorStart.
   - Translate search result range to local.

3. **`onSelectionChangedListener` signature:** No change in signature, but
   values passed to the listener are `localStart + globalTextOffset` and
   `localEnd + globalTextOffset`.

4. **`onHighlightTappedListener`:** The returned `TextHighlight` has global
   offsets restored.

5. **`onBookmarkTappedListener`:** The returned `BookmarkEntity` has global
   `anchorStart` restored.

6. **`verticalOffsetForSelection()` / `verticalOffsetForBookmark()`:**
   Accept global offsets, translate to local before computing.

7. **`topVisibleLineAnchor()`:** Return global offsets (local +
   `globalTextOffset`).

8. **`lineTextForOffset()`:** Accept global offset, translate to local.

9. **`rebuildLayout()`:** Change `BREAK_STRATEGY_HIGH_QUALITY` to
   `BREAK_STRATEGY_BALANCED`. This alone provides a significant speedup for
   paragraph-level break computation with negligible visual difference.

10. **`onDraw()` optimization (bonus):** Add early clipping check. Before
    drawing highlight backgrounds, compute the visible line range from the
    canvas clip bounds and skip highlights that are entirely outside it. This is
    a safety net even though chunks are small.

**What stays the same:**
- Touch handling (beginSelection, updateSelection, handle drag) — all operate
  on local offsets internally.
- Drawing code structure (drawRangeBackground, drawBookmarkIndicators, etc.).
- Layout caching logic (`layoutDirty`, `cachedLayoutWidth`).
- All paint objects and dimension constants.

### 5.2 `ReaderContentPanes.kt` — `ReaderStudyPane` Rewrite

**Current structure (lines 298-359):**

```
Column + verticalScroll(studyScrollState)
  └── AndroidView<JustifiedStudyTextView> (full text)
```

**New structure:**

```
LazyColumn(studyLazyListState)
  └── itemsIndexed(chunks) { index, chunk ->
        AndroidView<JustifiedStudyTextView>
          - factory: create view with globalTextOffset = chunk.globalStartOffset
          - update: bind chunk text + filtered highlights/bookmarks
      }
```

**Detailed changes:**

1. Replace `studyScrollState: ScrollState` parameter with
   `studyLazyListState: LazyListState`.

2. Add parameters:
   - `chunks: List<TextChunk>` — the pre-computed chunks.
   - `studySelectionCoordinator: StudySelectionCoordinator`.

3. The `LazyColumn` replaces the `Column + verticalScroll`:
   ```kotlin
   LazyColumn(
       state = studyLazyListState,
       modifier = Modifier
           .fillMaxSize()
           .systemBarsPadding()
           .padding(start = 16.dp, end = 16.dp, top = topContentPadding, bottom = bottomContentPadding)
           .onSizeChanged { onStudyViewportChanged(it.height) }
           .onUserDrag { onUserDrag() },
       contentPadding = PaddingValues(...)
   ) {
       itemsIndexed(
           items = chunks,
           key = { index, chunk -> "study_chunk_${chunk.globalStartOffset}" }
       ) { index, chunk ->
           AndroidView<JustifiedStudyTextView>(
               modifier = Modifier.fillMaxWidth(),
               factory = { context ->
                   JustifiedStudyTextView(context).apply {
                       globalTextOffset = chunk.globalStartOffset
                       // ... bind properties
                   }
               },
               update = { textView ->
                   // ... bind chunk-specific content
               }
           )
       }
   }
   ```

4. In the `update` block, filter highlights and bookmarks to the chunk's range
   before passing them to `bindStudyContent()`. The coordinator handles offset
   translation.

5. The editing mode (`isEditing == true`) branch remains unchanged — it uses a
  Compose `TextField` that is unaffected by the chunking.

### 5.3 `ReaderScreen.kt` — Orchestration Changes

This is the largest set of changes by count, but each change is mechanical.

**State changes:**

1. Replace:
   ```kotlin
   val studyScrollState = rememberScrollState()
   ```
   With:
   ```kotlin
   val studyLazyListState = rememberLazyListState()
   ```

2. Add:
   ```kotlin
   var studyChunks by remember { mutableStateOf<List<TextChunk>>(emptyList()) }
   val studySelectionCoordinator = remember { StudySelectionCoordinator() }
   ```

3. Add pagination computation via `derivedStateOf` or `LaunchedEffect`:
   ```kotlin
   val studyChunks = remember(content, fontSize, fontFamily, lineHeightMultiplier, studyViewportWidthPx) {
       if (content.isEmpty() || studyViewportWidthPx <= 0 || studyViewportHeightPx <= 0) {
           emptyList()
       } else {
           paginateStudyText(
               text = content,
               textPaint = buildMeasurementPaint(fontSize, fontFamily, lineHeightMultiplier),
               availableWidthPx = studyViewportWidthPx,
               targetChunkHeightPx = studyViewportHeightPx
           )
       }
   }
   ```
   Note: `studyViewportWidthPx` needs to be added (currently only
   `studyViewportHeightPx` is tracked).

4. Replace `studyTextView: JustifiedStudyTextView?` single reference with the
   coordinator pattern. The `onStudyTextViewReady` callback is called for each
   chunk's view and registers it with the coordinator.

**Scroll-related changes:**

5. All `studyScrollState.value` references → `studyLazyListState` equivalent:
   - `studyScrollState.maxValue` → sum of all chunk heights (or track via
     `LazyListState.canScrollForward`).
   - `studyScrollState.scrollTo(target)` → map offset to chunk index +
     offset-within-chunk → `studyLazyListState.scrollToItem(index, offset)`.
   - `studyScrollState.animateScrollTo(target)` →
     `studyLazyListState.animateScrollToItem(index, offset)`.
   - `studyScrollState.canScrollForward/canScrollBackward` → available directly
     on `LazyListState`.

6. **`scrollOnePage()`** (`ReaderScreen.kt:683-715`):
   ```kotlin
   ReaderMode.STUDY -> {
       val visibleItems = studyLazyListState.layoutInfo.visibleItemsInfo.size
       val targetIndex = targetListIndexForPageStep(
           currentFirstVisibleItemIndex = studyLazyListState.firstVisibleItemIndex,
           totalItems = studyChunks.size,
           visibleItemsCount = visibleItems,
           isForward = isForward
       )
       studyLazyListState.scrollToItem(targetIndex)
   }
   ```
   Alternatively, for finer granularity, use `studyLazyListState.scrollBy()`
   with a delta of ±`viewportHeightPx`.

7. **`captureCurrentLocation()`** (`ReaderScreen.kt:316-334`): For Study mode,
   compute the global offset of the top-visible line from the first visible
   chunk view:
   ```kotlin
   ReaderMode.STUDY -> {
       val firstVisible = studyLazyListState.layoutInfo.visibleItemsInfo.firstOrNull()
       val chunkIndex = firstVisible?.index ?: return null
       val textView = studySelectionCoordinator.textViews[chunkIndex] ?: return null
       val localOffset = firstVisible.offset // pixel offset within viewport
       val anchorStart = textView.topVisibleLineAnchor(-localOffset) // negative because scroll offset
       ReaderAnchor.Study(anchorStart)
   }
   ```

8. **`jumpBackTo()`** (`ReaderScreen.kt:355-418`): Map global offset to chunk
   index via binary search on `studyChunks`, then
   `studyLazyListState.scrollToItem(chunkIndex, offsetWithinChunk)`.

9. **`openBookmarkDialog()`** (`ReaderScreen.kt:452-460`): Get the first visible
   chunk's view to compute `topVisibleLineAnchor`.

10. **`persistReadingProgress`** (`ReaderScreen.kt:239-246`): Save
    `(firstVisibleItemIndex, firstVisibleScrollOffset)` instead of
    `studyScrollState.value`. This maps to the existing `lastStudyScroll: Int`
    field in `SubtitleEntity`. Encode as: `chunkIndex * 100_000 +
    offsetWithinChunk` (or add a separate column). Alternatively, save the
    global character offset of the top-visible line anchor and restore by
    binary-searching the chunks. This is cleaner and doesn't require schema
    changes.

11. **`onSelectStudyFindMatch`** (`ReaderScreen.kt:976-982`): Map find result's
    global start offset to chunk index → `studyLazyListState.animateScrollToItem(chunkIndex, ...)`.

12. **Navigation effects** (`ReaderScreen.kt:742-868`): All
    `studyScrollState.scrollTo()` / `studyScrollState.maxValue` calls need the
    chunk-index mapping.

**Removed state:**
- `studyScrollState` (replaced by `studyLazyListState`)
- `lastKnownStudyScroll` tracking via `snapshotFlow` (replace with chunk-aware
  tracking)
- `hasRestoredStudyScroll` (adapt for LazyColumn restore)

**Parameter threading:**
- `ReaderScreenMainLayer` receives `studyLazyListState` instead of
  `studyScrollState`.
- `ReaderScreenMainLayer` receives `chunks` and `studySelectionCoordinator`.
- These are threaded through to `ReaderStudyPane`.

### 5.4 `ReaderProgress.kt` — Chunk-Based Progress

**Changes:**

1. For Study mode, the progress functions switch from pixel-based to
   chunk-based:

   ```kotlin
   // New function
   internal fun chunkedScrollProgress(
       firstVisibleItemIndex: Int,
       totalChunks: Int,
       canScrollForward: Boolean,
       canScrollBackward: Boolean
   ): Int { ... }
   ```

2. `pagedScrollProgress()` is no longer used for Study mode. It is still used
   for Original fallback mode.

3. `PageProgress` for Study mode: `totalPages = totalChunks`, `currentPage =
   firstVisibleChunkIndex + 1`. More accurate than the current pixel-based
   approximation since each chunk ≈ 1 viewport height.

### 5.5 `ReaderScreenEffects.kt` — Scroll Restore & Tracking

**Changes:**

1. **Scroll restore** (`ReaderScreenEffects.kt:77-89`): Instead of
   `studyScrollState.scrollTo(targetScroll)`, restore by:
   - Reading the saved global character offset from `subtitle.lastStudyScroll`.
   - Binary searching `studyChunks` to find the chunk containing that offset.
   - Computing the pixel offset within the chunk.
   - Calling `studyLazyListState.scrollToItem(chunkIndex, pixelOffset)`.

   Wait for the LazyColumn to have items (similar to the existing `maxValue > 0`
   check).

2. **Scroll tracking** (`ReaderScreenEffects.kt:91-97`): Replace
   `snapshotFlow { studyScrollState.value }` with tracking the first visible
   chunk and the character offset at the top of that chunk. Save this as a
   global character offset for later restoration.

3. **`ReaderCoreEffects` signature**: Add `studyChunks: List<TextChunk>` and
   `studyLazyListState: LazyListState` parameters, remove `studyScrollState:
   ScrollState`.

### 5.6 `ReaderScreenController.kt` — Page-Step for LazyColumn

**Changes:**

1. The Study branch of `scrollOnePage()` in `ReaderScreen.kt` already delegates
   to `targetScrollForPageStep()`. Replace with
   `targetListIndexForPageStep()` or use `scrollBy()`.

2. No changes needed to the pure functions in this file (`classifyReaderTapZone`,
   `targetScrollForPageStep`, etc.) — they remain valid for Original mode.

### 5.7 `ReaderScreenLayout.kt` — Parameter Passthrough

**Changes:**

1. Replace `studyScrollState: ScrollState` with `studyLazyListState:
   LazyListState`.
2. Add `chunks: List<TextChunk>` and `studySelectionCoordinator:
   StudySelectionCoordinator` parameters.
3. Thread these to `ReaderStudyPane` instead of the old parameters.

### 5.8 `ReaderAndroidViewBindings.kt` — Chunk-Aware Binding

**Changes to `bindStudyContent()`:**

1. Add `globalTextOffset: Int` parameter.
2. Set `textView.globalTextOffset = globalTextOffset` before other calls.
3. Filter highlights to those within `[globalTextOffset, globalTextOffset +
   chunkText.length)` and translate to local offsets.
4. Filter bookmarks similarly.
5. Translate search result range to local if it overlaps the chunk.
6. The rest of the binding (font size, colors, listeners) remains unchanged.

### 5.9 `ReaderViewModel.kt` — Minimal Changes

**Possible change:**

- Consider storing `lastStudyScroll` as a global character offset instead of a
  pixel offset. This makes it resilient to font size changes (currently,
  restoring a pixel scroll position after font size change lands at an arbitrary
  position). However, this is an optional improvement, not a requirement for
  the rewrite.

- If encoding both chunk index and offset is preferred over character offset,
  add a `lastStudyChunkIndex: Int` column to `SubtitleEntity`. But this
  requires a database migration (version 22 → 23). The character-offset
  approach avoids this.

## 6. Migration Plan

### 6.1 `lastStudyScroll` Compatibility

Currently `lastStudyScroll` stores a pixel offset. After the rewrite, it will
store a global character offset. To maintain backward compatibility:

- On load, if `lastStudyScroll` appears to be a pixel offset (large value,
  typically > 1000 for long texts), treat it as "position near the beginning"
  and scroll to offset 0. This is acceptable because the field is only used for
  convenience restoration, not for critical data.
- Alternatively, use a heuristic: if `lastStudyScroll > content.length`, it's a
  legacy pixel offset — divide by an estimated pixels-per-character ratio to get
  an approximate character offset. This is fragile.
- **Recommended approach:** Store the global character offset going forward.
  Accept that users may lose their exact scroll position once after the upgrade.
  This is the simplest and most reliable option.

## 7. Implementation Order

### Phase 1: Foundation (no UI changes)

1. **Create `StudyTextPaginator.kt`** — pure computation, fully testable.
   Write unit tests with various text lengths and verify chunk boundaries align
   to paragraph breaks.

2. **Create `StudySelectionCoordinator.kt`** — skeleton with coordinator state
   management, offset translation helpers.

### Phase 2: View Adaptation

3. **Modify `JustifiedStudyTextView.kt`** — add `globalTextOffset`, update
   all offset translations, change break strategy to `BALANCED`.

4. **Modify `ReaderAndroidViewBindings.kt`** — update `bindStudyContent()` to
   accept and use `globalTextOffset`, filter and translate highlights/bookmarks.

### Phase 3: UI Rewiring

5. **Rewrite `ReaderStudyPane` in `ReaderContentPanes.kt`** — LazyColumn with
   chunked items.

6. **Update `ReaderScreenLayout.kt`** — new parameters, thread to
   `ReaderStudyPane`.

7. **Update `ReaderScreen.kt`** — the bulk of mechanical changes:
   - Replace `studyScrollState` with `studyLazyListState`.
   - Add chunk computation.
   - Update all scroll-to, progress, and navigation logic.
   - Update coordinator registration.

### Phase 4: Effects & Progress

8. **Update `ReaderScreenEffects.kt`** — scroll restore and tracking for
   LazyColumn.

9. **Update `ReaderProgress.kt`** — chunk-based progress calculation.

10. **Update `ReaderScreenController.kt`** — page-step for study mode uses
    LazyColumn APIs.

### Phase 5: Testing & Polish

11. **Manual testing** with long transcripts (30+ pages):
    - Scroll smoothness.
    - Text selection within a single chunk.
    - Text selection across chunk boundaries.
    - Highlight application, display, and tap.
    - Bookmark display and tap.
    - Find/search results navigation.
    - Jump-back functionality.
    - Scroll position persistence across rotation and app restart.
    - Font size change while scrolled.
    - Mode switching (Study ↔ Original).

12. **Edge case testing:**
    - Very short text (1 chunk, no scrolling needed).
    - Empty text.
    - Text with no paragraph breaks (single long paragraph).
    - Text where highlights span chunk boundaries.
    - Rapid scrolling through many chunks.

## 8. Risk Assessment

### 8.1 Cross-Chunk Selection Complexity

**Risk:** Medium. Dragging a selection from one chunk to an adjacent chunk
requires coordinating touch events between two separate `View` instances. The
current code assumes a single view handles all touches.

**Mitigation:** The `StudySelectionCoordinator` handles the coordination.
If the touch-forwarding approach proves unreliable, fall back to the overlay
approach (transparent composable capturing all touches and delegating to the
correct chunk view).

### 8.2 Highlight Spanning Chunks

**Risk:** Low. A single highlight can span two chunks (e.g., highlight starts
at the end of chunk N, ends at the beginning of chunk N+1). Both chunks will
draw their portion of the highlight independently using the same color. Visually
this will look correct as long as the offset translation is accurate.

**Mitigation:** When drawing, each chunk draws its local portion. The drawing
code already handles partial ranges correctly (it clips to line boundaries).

### 8.3 Pagination Accuracy

**Risk:** Low. The measurement layout uses the same `TextPaint` settings as the
display views, so chunk heights will match. Slight discrepancies can occur due
to `BREAK_STRATEGY_BALANCED` behaving differently when the text is shorter
(the break strategy considers the full paragraph). In practice, this only
affects the last few lines of a chunk and is visually imperceptible.

**Mitigation:** Use `BREAK_STRATEGY_BALANCED` consistently in both measurement
and display. Do not mix strategies.

### 8.4 Scroll Position Fidelity

**Risk:** Low. Pixel-based scroll positions are replaced with character-offset
positions. On restore, the character offset is mapped to a chunk and then to a
pixel offset within that chunk. The result may be a few lines off from the
original position (due to re-pagination if settings changed), but this is
acceptable.

## 9. Performance Expectations

| Metric | Before | After (Expected) |
|--------|--------|-------------------|
| StaticLayout build time | O(full text) per rebuild | O(chunk size) per rebuild, ~10-15x faster |
| Per-frame draw cost | All highlights + all bookmarks + all lines | Only visible chunk's highlights/bookmarks/lines |
| Memory (StaticLayout) | 1 giant layout | 2-3 small layouts |
| Memory (View hierarchy) | 1 large View | 2-3 small Views |
| Selection responsiveness | Redraws entire text per move | Redraws only the active chunk |
| Scroll smoothness | Jank on long texts (measure + draw cost) | Smooth (lazy loading + small draws) |

## 10. Files Summary

### New Files
| File | Lines (est.) | Purpose |
|------|-------------|---------|
| `StudyTextPaginator.kt` | ~150 | Chunk computation from full text |
| `StudySelectionCoordinator.kt` | ~100 | Cross-chunk selection management |

### Modified Files
| File | Scope of Changes | Description |
|------|-----------------|-------------|
| `JustifiedStudyTextView.kt` | Medium | Add `globalTextOffset`, offset translation, `BALANCED` break strategy |
| `ReaderContentPanes.kt` | Large | Rewrite `ReaderStudyPane` as LazyColumn with chunks |
| `ReaderScreen.kt` | Large | Replace ScrollState with LazyListState, add chunk computation, update all scroll/navigation |
| `ReaderScreenLayout.kt` | Small | Update parameter types |
| `ReaderScreenEffects.kt` | Medium | Scroll restore and tracking for LazyColumn |
| `ReaderProgress.kt` | Small | Add chunk-based progress function |
| `ReaderScreenController.kt` | Small | Page-step for study LazyColumn |
| `ReaderAndroidViewBindings.kt` | Medium | Chunk-aware highlight/bookmark filtering and offset translation |

### Unchanged Files
All other files in the reader directory remain unchanged:
`ReaderTypes.kt`, `ReaderConstants.kt`, `ReaderChrome.kt`,
`ReaderOverlayHost.kt`, `ReaderUiComponents.kt`, `ReaderDialogHost.kt`,
`ReaderDialogs.kt`, `ReaderScreenActions.kt`, `ReaderFind.kt`,
`SelectableHighlightTextView.kt`, `TextHighlight.kt`,
`StudySelectionUtils.kt`, `AnnotationRemapper.kt`, `TextDiff.kt`,
`ReaderPlatform.kt`, `ReaderAnnotationTarget.kt`, `ReaderUiUtils.kt`,
`VideoNotesScreen.kt`, `VideoNotesViewModel.kt`, `VideoNotesExport.kt`.
