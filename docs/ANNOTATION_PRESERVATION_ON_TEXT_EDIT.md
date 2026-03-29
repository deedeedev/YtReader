# Annotation Preservation on Text Edit — Implementation Plan

## Overview

When a user modifies the subtitle text in edit mode, the app should try to preserve existing annotations (highlights, notes, bookmarks) by remapping their character offsets using a diff algorithm, rather than unconditionally deleting them.

## Current Behavior

When text is saved via `ReaderViewModel.updateContent()` (`ReaderViewModel.kt:153-170`), **all annotations are unconditionally deleted**:

```kotlin
fun updateContent(content: String) {
    _uiState.update { state ->
        state.copy(
            content = content,
            highlights = emptyList(),  // <-- CLEARED
            subtitle = state.subtitle?.copy(
                studyContent = content,
                highlights = ""
            )
        )
    }
    viewModelScope.launch {
        subtitleDao.updateStudyContent(subtitleId, content)
        subtitleDao.updateHighlights(subtitleId, "")  // <-- CLEARED
        highlightNoteDao.deleteBySubtitleId(subtitleId)  // <-- CLEARED
        bookmarkDao.deleteBySubtitleId(subtitleId)  // <-- CLEARED
    }
}
```

### Why They're Currently Deleted

Annotations use **character offsets** into the study text:
- **Highlights**: range `[start, end)` — characters from position `start` (inclusive) to `end` (exclusive)
- **Bookmarks**: single point `anchorStart` — character offset into the text
- **Highlight Notes**: keyed by `(subtitleId, highlightStart, highlightEnd)` — references the highlight's range

When text changes, these offsets become invalid. The current solution is to delete everything.

### All Text Update Paths

All of these paths call `updateContent()` and currently clear annotations:

1. **Edit mode save** (`ReaderScreen.kt:1198`) — user edits text and taps save
2. **Replace with clipboard** (`ReaderScreen.kt:1229`) — calls `applyTextUpdate()`
3. **Remove empty lines** (`ReaderScreen.kt:1237`) — calls `applyTextUpdate()`
4. **Find & replace** (regex replace) — calls `applyTextUpdate()`
5. **AI preview apply** — calls `applyTextUpdate()`

The entry point is `applyReaderTextUpdate()` in `ReaderScreenController.kt:38-49`.

---

## Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Partial overlap handling | Shrink to surviving text | Preserves as much annotation data as possible; drops only if the entire highlighted text is gone |
| User feedback | Silent | No snackbar or summary; user can check annotations panel |
| Scope | All text update paths | Consistent behavior everywhere |

---

## Algorithm: Character-Level Myers Diff with Offset Mapping

We use the [Myers diff algorithm](https://neil.fraser.name/software/diff_match_patch/samples/demo_diff.html), which produces a minimal edit script of **equal**, **delete**, and **insert** operations. From this edit script, we build an **offset mapping table** that maps old character positions to new character positions.

**Why this approach:**
- Subtitle texts are small (typically 1-50 KB) — performance is not a concern
- Character-level precision gives the most accurate offset remapping
- No external dependency needed — the algorithm is ~100-150 lines of pure Kotlin

---

## Annotation Remapping Rules

| Scenario | Highlight | Bookmark |
|---|---|---|
| Text around annotation is unchanged | Shift offsets by delta | Shift offset by delta |
| Text inserted inside highlighted range | Expand range end | Shift offset |
| Text deleted inside highlighted range | Shrink range (verify text still matches) | Move to nearest valid offset |
| Highlighted text partially deleted | Preserve surviving portion (shrink) | Move to nearest valid offset |
| Highlighted text completely deleted | Drop annotation | Drop annotation |
| Text replaced entirely | Drop all annotations | Drop all annotations |

---

## New Files

### 1. `app/src/main/java/com/deedeedev/ytreader/ui/reader/TextDiff.kt`

Pure functions for computing character-level diffs and remapping offsets.

```kotlin
package com.deedeedev.ytreader.ui.reader

/**
 * Represents a contiguous chunk of the diff between old and new text.
 */
data class DiffHunk(
    val type: DiffType,    // EQUAL, DELETE, INSERT
    val oldStart: Int,      // inclusive start in old text
    val oldEnd: Int,        // exclusive end in old text
    val newStart: Int,      // inclusive start in new text
    val newEnd: Int         // exclusive end in new text
)

enum class DiffType {
    /** Text that appears in both old and new (unchanged). */
    EQUAL,
    /** Text that was deleted from old. */
    DELETE,
    /** Text that was inserted in new. */
    INSERT
}

/**
 * Computes the character-level Myers diff between oldText and newText.
 * Returns a list of DiffHunks sorted by oldStart.
 */
fun diff(oldText: String, newText: String): List<DiffHunk>

/**
 * Maps an old character offset to a new character offset.
 * Returns null if the position was deleted (no corresponding position in new text).
 * For positions in deleted regions, returns the offset of the nearest valid position.
 */
fun remapPoint(hunks: List<DiffHunk>, oldOffset: Int): Int?

/**
 * Maps an old character range [oldStart, oldEnd) to a new range.
 * - If the entire range was deleted, returns null.
 * - If part of the range was deleted, shrinks to the surviving portion.
 * - If the range was expanded (text inserted), expands accordingly.
 * - Returns null if no text from the original range survives.
 * - The surviving text is verified to match the original content.
 */
fun remapRange(
    hunks: List<DiffHunk>,
    oldStart: Int,
    oldEnd: Int,
    oldText: String,
    newText: String
): IntRange?
```

#### Algorithm Details

The Myers diff algorithm works as follows:

1. **Construct the edit graph**: Rows = old text length + 1, Columns = new text length + 1
2. **Find the shortest edit script (SES)**: Use dynamic programming to find the minimum number of insertions/deletions
3. **Backtrack to find the path**: Produces the sequence of operations
4. **Convert to hunks**: Merge adjacent operations into contiguous chunks

For efficiency with large texts, we use the O(ND) variant where N = old length + new length and D = edit distance.

#### Edge Cases Handled

- Empty old text (new text only) — all offsets map from 0
- Empty new text (all deleted) — all positions become null
- Identical texts — all hunks are EQUAL, offsets map 1:1
- Very large texts — algorithm is O(ND), acceptable for < 100KB

---

### 2. `app/src/main/java/com/deedeedev/ytreader/ui/reader/AnnotationRemapper.kt`

Remaps highlights, notes, and bookmarks from old text to new text.

```kotlin
package com.deedeedev.ytreader.ui.reader

/**
 * Result of annotation remapping.
 */
data class RemapResult(
    val highlights: List<TextHighlight>,
    val bookmarks: List<BookmarkEntity>,
    val lostHighlightCount: Int,
    val lostBookmarkCount: Int
)

/**
 * Remaps annotations from oldText to newText using diff hunks.
 * - Highlights: remap range, verify text matches, preserve notes
 * - Bookmarks: remap point, preserve title and timestamps
 * 
 * @return RemapResult with remapped annotations and counts of lost items
 */
fun remapAnnotations(
    oldText: String,
    newText: String,
    highlights: List<TextHighlight>,
    bookmarks: List<BookmarkEntity>
): RemapResult
```

#### Implementation Logic

1. Compute diff: `val hunks = diff(oldText, newText)`
2. **For each highlight**:
   - Call `remapRange(hunks, highlight.start, highlight.end, oldText, newText)`
   - If result is null: the highlight was fully deleted → increment `lostHighlightCount`
   - If result is non-null: create new `TextHighlight` with remapped range, same color, same note
3. **For each bookmark**:
   - Call `remapPoint(hunks, bookmark.anchorStart)`
   - If result is null: the bookmark position was deleted with no nearby fallback → increment `lostBookmarkCount`
   - If result is non-null: create new `BookmarkEntity` with remapped `anchorStart`, same title, same timestamps
4. Sort remapped highlights by `start`, bookmarks by `anchorStart`
5. Return `RemapResult`

---

### 3. `app/src/test/java/com/deedeedev/ytreader/ui/reader/TextDiffTest.kt`

Unit tests for the diff algorithm and offset mapping.

#### Test Cases

| Test | Description |
|---|---|
| `emptyTexts` | Both old and new are empty |
| `noChanges` | Old equals new — all EQUAL hunks, offsets unchanged |
| `insertBefore` | Insert at position 0 — all offsets shift right |
| `insertAfter` | Insert at end — offsets before insertion unchanged |
| `insertInMiddle` | Insert in middle — offsets shift accordingly |
| `deleteBefore` | Delete before annotation — offsets shift left |
| `deleteInMiddle` | Delete in middle — range shrinks |
| `deleteAll` | Delete entire text — all offsets become null |
| `replaceAll` | Replace entire text — all offsets invalid |
| `multipleEdits` | Multiple inserts and deletes — correct cumulative mapping |
| `insertOverlapsHighlight` | Insert inside highlighted range — range expands |
| `deleteOverlapsHighlight` | Delete inside highlighted range — range shrinks |
| `deleteEntireHighlight` | Delete entire highlighted text — highlight lost |
| `adjacentEdits` | Edit immediately before and after an annotation |

---

### 4. `app/src/test/java/com/deedeedev/ytreader/ui/reader/AnnotationRemapperTest.kt`

Unit tests for annotation remapping.

#### Test Cases

| Test | Description |
|---|---|
| `noAnnotations` | No highlights or bookmarks — returns empty lists, 0 lost |
| `noTextChange` | Text unchanged — all annotations preserved unchanged |
| `allHighlightsPreserved` | Insert before all highlights — offsets shifted correctly |
| `allBookmarksPreserved` | Insert before all bookmarks — offsets shifted correctly |
| `highlightInDeletedRegion` | Highlight in deleted region — lost, count incremented |
| `bookmarkInDeletedRegion` | Bookmark at deleted position — lost, count incremented |
| `highlightPartialOverlap` | Highlight partially overlaps edit — shrunk to surviving text |
| `highlightWithNotePreserved` | Highlight with note — note preserved at new range |
| `bookmarkTitlePreserved` | Bookmark with custom title — title preserved |
| `mixedPreservedAndLost` | Some annotations preserved, some lost — correct counts |
| `emptyOldText` | Old text empty — new annotations start from 0 |
| `emptyNewText` | New text empty — all annotations lost |

---

## Modified Files

### `app/src/main/java/com/deedeedev/ytreader/ui/reader/ReaderViewModel.kt`

Modify `updateContent()` to use `AnnotationRemapper`:

```kotlin
fun updateContent(content: String) {
    val oldContent = _uiState.value.content
    val oldHighlights = _uiState.value.highlights
    val oldBookmarks = _uiState.value.bookmarks
    
    val hasAnnotations = oldHighlights.isNotEmpty() || oldBookmarks.isNotEmpty()
    val textChanged = oldContent != content
    
    // Remap annotations if text changed and there are existing annotations
    val remapResult = if (textChanged && hasAnnotations) {
        remapAnnotations(oldContent, content, oldHighlights, oldBookmarks)
    } else null
    
    val newHighlights = remapResult?.highlights ?: if (textChanged) emptyList() else oldHighlights
    val newBookmarks = remapResult?.bookmarks ?: if (textChanged) emptyList() else oldBookmarks
    
    _uiState.update { state ->
        state.copy(
            content = content,
            highlights = newHighlights,
            subtitle = state.subtitle?.copy(
                studyContent = content,
                highlights = serializeHighlights(newHighlights)
            )
        )
    }
    
    viewModelScope.launch {
        subtitleDao.updateStudyContent(subtitleId, content)
        subtitleDao.updateHighlights(subtitleId, serializeHighlights(newHighlights))
        
        // Rebuild notes and bookmarks with remapped offsets
        highlightNoteDao.deleteBySubtitleId(subtitleId)
        bookmarkDao.deleteBySubtitleId(subtitleId)
        
        remapResult?.let { result ->
            val timestamp = System.currentTimeMillis()
            
            // Re-insert remapped highlight notes
            result.highlights.filter { it.note != null }.forEach { highlight ->
                highlightNoteDao.upsert(
                    HighlightNoteEntity(
                        subtitleId = subtitleId,
                        highlightStart = highlight.start,
                        highlightEnd = highlight.end,
                        noteText = highlight.note!!,
                        createdAt = timestamp,
                        updatedAt = timestamp
                    )
                )
            }
            
            // Re-insert remapped bookmarks
            result.bookmarks.forEach { bookmark ->
                bookmarkDao.upsert(bookmark)
            }
        }
    }
}
```

#### Key Points

1. **Preserves existing behavior when no annotations exist** — if `oldHighlights` and `oldBookmarks` are empty, behaves identically to before
2. **Preserves existing behavior when text unchanged** — if `oldContent == content`, no remapping needed
3. **Uses existing DAO methods** — `deleteBySubtitleId()` then `upsert()` for clean rebuild
4. **Notes embedded in highlights** — the `highlight.note` field is populated from `HighlightNoteEntity` at load time (see `ReaderViewModel.kt:86-91`)

---

## No Changes Required

- **Database schema** — no migrations needed
- **DAOs** — using existing `deleteBySubtitleId()` + `upsert()` pattern
- **UI layer** — no composables modified
- **Serialization format** — highlights string format stays the same (`"start,end,COLOR|..."`)

---

## Implementation Order

1. **Implement `TextDiff.kt`** — pure functions, no dependencies
2. **Write `TextDiffTest.kt`** — verify algorithm correctness
3. **Implement `AnnotationRemapper.kt`** — uses TextDiff
4. **Write `AnnotationRemapperTest.kt`** — verify remapping logic
5. **Modify `ReaderViewModel.kt`** — integrate remapping into `updateContent()`
6. **Run tests**: `./gradlew :app:testDebugUnitTest`
7. **Run lint**: `./gradlew :app:lint`

---

## Risk Mitigation

- **Graceful fallback**: If remapping fails (e.g., unexpected algorithm edge case), falls back to clear-all behavior
- **Text verification**: When shrinking highlights, verifies that the surviving text matches the original to avoid corrupting annotations
- **No breaking changes**: Existing functionality preserved; only adds annotation preservation
- **Comprehensive tests**: Both unit tests cover edge cases

---

## Related Files (Read-Only Reference)

- `ReaderViewModel.kt` — lines 153-170: current `updateContent()` implementation
- `ReaderScreenController.kt` — lines 38-49: `applyReaderTextUpdate()` entry point
- `TextHighlight.kt` — highlight data model and serialization
- `SubtitleEntity.kt` — database entity with `highlights` field
- `HighlightNoteEntity.kt` — highlight notes storage
- `BookmarkEntity.kt` — bookmarks storage
- `SubtitleDao.kt`, `HighlightNoteDao.kt`, `BookmarkDao.kt` — database operations
