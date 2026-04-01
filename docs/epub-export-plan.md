# EPUB Export — Implementation Plan & Progress

## Goal

Add EPUB export functionality to YtReader, allowing users to export videos from the Library, from a single collection, or per-video as EPUB files.

## Design Decisions

- **No external library** — EPUB is generated manually as a ZIP (mimetype + META-INF + XHTML chapters + CSS + TOC)
- **Two export modes**: "Clean" (subtitle text + metadata only) and "Annotated" (includes highlights, notes, bookmarks)
- **Text source priority**: AI-cleaned text → studyContent → parsed raw subtitle content
- **All subtitle tracks** per video are included (all languages)
- **Post-export**: Share via Android share sheet using `FileProvider` (same pattern as `VideoNotesExport.kt`)

## EPUB Structure

```
Book Title: "Library" or "Collection: {name}" or "{Video Title}"
├── Chapter: Video 1 Title
│   ├── Metadata (channel, URL, date)
│   ├── Track 1 (e.g. English) — full text, optionally with <span> highlights + title notes
│   ├── Track 2 (e.g. Spanish) — same
│   └── Bookmarks section (annotated mode)
├── Chapter: Video 2 Title
│   └── ...
```

## Export Trigger Points (4 total)

1. **Library top bar** — export all library videos
2. **Collection detail screen header** — export that collection
3. **Collection list context menu** — each collection's dropdown menu
4. **Per-video context menu** — long-press dropdown on library item cards

---

## Files Overview

### Created (complete)

| File | Purpose |
|------|---------|
| `app/src/main/java/com/deedeedev/ytreader/data/EpubExporter.kt` | Core EPUB generation: `exportEpub()` (suspend), `shareEpub()`, ZIP writing, XHTML chapter generation, highlight rendering, HTML/XML escaping |
| `app/src/main/java/com/deedeedev/ytreader/ui/components/EpubExportDialog.kt` | Reusable Compose dialog with Clean/Annotated buttons, progress indicator, error display |

### Modified (complete)

| File | Changes |
|------|---------|
| `app/src/main/res/values/strings.xml` | Added 11 EPUB string resources (see String Resources below) |
| `app/src/main/java/com/deedeedev/ytreader/data/local/SubtitleDao.kt` | Added `getLibraryVideoIds()` suspend query |
| `app/src/main/java/com/deedeedev/ytreader/ui/home/HomeViewModel.kt` | Added `exportEpub()` and `getAllLibraryVideoIds()` methods |

### Partially modified

| File | What's done | What's missing |
|------|-------------|----------------|
| `app/src/main/java/com/deedeedev/ytreader/ui/home/LibraryScreen.kt` | Imports, new function signature (4 DAO params), state variables (`showEpubExport`, `epubExportVideoIds`, `epubExportTitle`), header row with export icon button | 1. `EpubExportDialog` composable at bottom of `LibraryScreen` function (after `addToCollectionTargetVideoId` dialog block) <br> 2. `onExportEpub` parameter on `LibraryItemCard` <br> 3. "Export as EPUB" `DropdownMenuItem` in `LibraryItemCard`'s context menu |

### Not yet modified

| File | Required changes |
|------|-----------------|
| `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionsScreen.kt` | Add "Export as EPUB" to each collection card's dropdown menu |
| `app/src/main/java/com/deedeedev/ytreader/ui/home/CollectionDetailScreen.kt` | Add export icon button in the header row |
| `app/src/main/java/com/deedeedev/ytreader/ui/MainScreen.kt` | Pass the 4 DAOs from `appContainer` to screen composables |

---

## String Resources (already added)

```xml
<string name="epub_export_title">Export as EPUB</string>
<string name="epub_export_clean">Clean</string>
<string name="epub_export_annotated">Annotated</string>
<string name="epub_export_clean_description">Subtitle text and video metadata</string>
<string name="epub_export_annotated_description">Include highlights, notes and bookmarks</string>
<string name="epub_exporting">Exporting…</string>
<string name="epub_export_error">Export failed.</string>
<string name="epub_export_video">Export as EPUB</string>
<string name="epub_export_library">Export library</string>
<string name="epub_export_collection">Export as EPUB</string>
<string name="epub_export_empty">No videos to export.</string>
```

---

## Remaining Steps (in order)

### Step 1 — Finish `LibraryScreen.kt`

Three changes needed:

#### 1a. Add `EpubExportDialog` at end of `LibraryScreen` function

After the `addToCollectionTargetVideoId?.let { ... }` block (line ~254), add:

```kotlin
if (showEpubExport) {
    EpubExportDialog(
        bookTitle = epubExportTitle,
        videoIds = epubExportVideoIds,
        subtitleDao = subtitleDao,
        videoDao = videoDao,
        highlightNoteDao = highlightNoteDao,
        bookmarkDao = bookmarkDao,
        onDismiss = { showEpubExport = false }
    )
}
```

#### 1b. Add `onExportEpub` parameter to `LibraryItemCard`

Add a new optional parameter:

```kotlin
onExportEpub: ((String, String) -> Unit)? = null
// First param: videoId, Second param: video title (for book title)
```

#### 1c. Add "Export as EPUB" DropdownMenuItem in `LibraryItemCard`

After the "Share video URL" menu item (around line 443), add:

```kotlin
onExportEpub?.let { exportFn ->
    DropdownMenuItem(
        text = { Text(stringResource(R.string.epub_export_video)) },
        onClick = {
            exportFn(item.videoId, item.title)
            showMenu = false
        },
        leadingIcon = {
            Icon(
                Icons.Default.IosShare,
                contentDescription = null
            )
        }
    )
}
```

Then in `LibraryScreen`, pass `onExportEpub` to `LibraryItemCard`:

```kotlin
onExportEpub = { videoId, title ->
    epubExportVideoIds = listOf(videoId)
    epubExportTitle = title
    showEpubExport = true
}
```

---

### Step 2 — Modify `CollectionsScreen.kt`

Add an `onExportEpub` callback parameter and "Export as EPUB" menu item to each collection's dropdown.

#### 2a. Add parameter to `CollectionsScreen`

```kotlin
fun CollectionsScreen(
    viewModel: HomeViewModel,
    onCollectionClick: (String) -> Unit,
    onExportEpub: (List<String>, String) -> Unit, // videoIds, bookTitle
    modifier: Modifier = Modifier
)
```

#### 2b. Pass callback to `CollectionCard`

Add `onExport` parameter to the private `CollectionCard` composable:

```kotlin
private fun CollectionCard(
    // ... existing params ...
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit   // NEW
)
```

#### 2c. Add DropdownMenuItem in `CollectionCard`

Inside the `DropdownMenu` block (after the "Delete" item, around line 368), add:

```kotlin
DropdownMenuItem(
    text = { Text(stringResource(R.string.epub_export_collection)) },
    onClick = {
        showMenu = false
        onExport()
    },
    leadingIcon = {
        Icon(imageVector = Icons.Default.IosShare, contentDescription = null)
    }
)
```

#### 2d. Wire up from `CollectionsScreen`

In `itemsIndexed`, update the `CollectionCard` call:

```kotlin
CollectionCard(
    // ... existing params ...
    onExport = {
        onExportEpub(collection.videoIds, collection.name)
    }
)
```

Also add the `IosShare` import:
```kotlin
import androidx.compose.material.icons.filled.IosShare
```

Also add EPUB export dialog state to `CollectionsScreen` (same pattern as LibraryScreen):

```kotlin
var showEpubExport by remember { mutableStateOf(false) }
var epubExportVideoIds by remember { mutableStateOf<List<String>>(emptyList()) }
var epubExportTitle by remember { mutableStateOf("") }
```

And add the 4 DAO parameters + `EpubExportDialog` at the bottom of the function. Alternatively, since `CollectionsScreen` doesn't currently receive DAOs, pass them through from `MainScreen.kt` (see Step 4).

---

### Step 3 — Modify `CollectionDetailScreen.kt`

Add an export icon button in the header row next to the back button and collection name.

#### 3a. Add parameters

```kotlin
fun CollectionDetailScreen(
    viewModel: HomeViewModel,
    collectionId: String,
    onSubtitleClick: (Long) -> Unit,
    onVideoClick: (String) -> Unit,
    onVideoSearchAgain: (String) -> Unit,
    onBack: () -> Unit,
    subtitleDao: SubtitleDao,        // NEW
    videoDao: VideoDao,               // NEW
    highlightNoteDao: HighlightNoteDao, // NEW
    bookmarkDao: BookmarkDao,         // NEW
    modifier: Modifier = Modifier
)
```

#### 3b. Add state variables

```kotlin
var showEpubExport by remember { mutableStateOf(false) }
```

#### 3c. Add export button in header row

Change the existing `Row` (line ~138) to include a `Spacer` and `IconButton`:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.collection_back_to_collections)
            )
        }
        Text(
            text = collection.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
    IconButton(onClick = {
        if (collection.videoIds.isNotEmpty()) {
            showEpubExport = true
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.epub_export_empty),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }) {
        Icon(
            imageVector = Icons.Default.IosShare,
            contentDescription = stringResource(R.string.epub_export_collection)
        )
    }
}
```

#### 3d. Add `EpubExportDialog` at end of function

```kotlin
if (showEpubExport && collection != null) {
    EpubExportDialog(
        bookTitle = collection.name,
        videoIds = collection.videoIds,
        subtitleDao = subtitleDao,
        videoDao = videoDao,
        highlightNoteDao = highlightNoteDao,
        bookmarkDao = bookmarkDao,
        onDismiss = { showEpubExport = false }
    )
}
```

#### 3e. Add imports

```kotlin
import androidx.compose.material.icons.filled.IosShare
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.VideoDao
import com.deedeedev.ytreader.ui.components.EpubExportDialog
```

---

### Step 4 — Modify `MainScreen.kt`

Pass the 4 DAOs from `appContainer` to the screen composables that need them.

#### 4a. LibraryScreen (line ~230)

The function signature already has the DAO parameters but they're not being passed from `MainScreen`. Update:

```kotlin
LibraryScreen(
    viewModel = viewModel,
    onSubtitleClick = { id -> openReader(id) },
    onVideoClick = openPreferredSubtitleForVideo,
    onVideoSearchAgain = searchVideoAgain,
    subtitleDao = appContainer.subtitleDao,
    videoDao = appContainer.videoDao,
    highlightNoteDao = appContainer.highlightNoteDao,
    bookmarkDao = appContainer.bookmarkDao
)
```

#### 4b. CollectionsScreen (line ~255)

Add `onExportEpub` callback and DAO parameters. Since `CollectionsScreen` needs to show the `EpubExportDialog`, either:

**Option A (recommended)**: Add 4 DAO parameters to `CollectionsScreen` and manage the dialog internally, same as `LibraryScreen`.

**Option B**: Manage dialog state in `MainScreen` and pass only a callback.

For Option A, the `MainScreen` call becomes:

```kotlin
CollectionsScreen(
    viewModel = viewModel,
    onCollectionClick = { collectionId ->
        navController.navigate("collection/$collectionId") {
            launchSingleTop = true
        }
    },
    subtitleDao = appContainer.subtitleDao,
    videoDao = appContainer.videoDao,
    highlightNoteDao = appContainer.highlightNoteDao,
    bookmarkDao = appContainer.bookmarkDao
)
```

And `CollectionsScreen` handles its own `EpubExportDialog` internally (same pattern as `LibraryScreen`).

#### 4c. CollectionDetailScreen (line ~295)

```kotlin
CollectionDetailScreen(
    viewModel = viewModel,
    collectionId = collectionId,
    onSubtitleClick = { id -> openReader(id) },
    onBack = { navController.popBackStack() },
    onVideoClick = openPreferredSubtitleForVideo,
    onVideoSearchAgain = searchVideoAgain,
    subtitleDao = appContainer.subtitleDao,
    videoDao = appContainer.videoDao,
    highlightNoteDao = appContainer.highlightNoteDao,
    bookmarkDao = appContainer.bookmarkDao
)
```

---

### Step 5 — Build & Verify

```bash
./gradlew :app:assembleDebug
```

If compilation succeeds, run lint:

```bash
./gradlew lint
```

---

## Key Implementation Details

### DAOs already available in AppContainer

All DAOs are accessible via `appContainer` in `MainScreen.kt`:
- `appContainer.subtitleDao: SubtitleDao`
- `appContainer.videoDao: VideoDao`
- `appContainer.highlightNoteDao: HighlightNoteDao`
- `appContainer.bookmarkDao: BookmarkDao`

### EpubExporter.kt API

```kotlin
// Suspend function — call from coroutine scope
suspend fun exportEpub(
    context: Context,
    subtitleDao: SubtitleDao,
    videoDao: VideoDao,
    highlightNoteDao: HighlightNoteDao,
    bookmarkDao: BookmarkDao,
    videoIds: List<String>,
    mode: EpubExportMode,        // CLEAN or ANNOTATED
    bookTitle: String
): File

// Launch share sheet — call on main thread
fun shareEpub(context: Context, file: File)
```

### EpubExportDialog API

```kotlin
@Composable
fun EpubExportDialog(
    bookTitle: String,
    videoIds: List<String>,
    subtitleDao: SubtitleDao,
    videoDao: VideoDao,
    highlightNoteDao: HighlightNoteDao,
    bookmarkDao: BookmarkDao,
    onDismiss: () -> Unit
)
```

The dialog handles the entire flow internally: shows Clean/Annotated buttons, calls `exportEpub()`, then `shareEpub()`, then calls `onDismiss`.

### HomeViewModel EPUB methods

```kotlin
// Export — delegates to EpubExporter.kt
suspend fun exportEpub(videoIds: List<String>, mode: EpubExportMode, bookTitle: String): File

// Get all video IDs in the library
suspend fun getAllLibraryVideoIds(): List<String>
```

Note: `EpubExportDialog` calls the top-level `exportEpub()` directly (not via ViewModel), so the ViewModel methods are only needed for the library-level export in `LibraryScreen`.

### FileProvider

The `exports/` directory is already configured in `file_provider_paths.xml`. No changes needed.

### Highlight serialization

Highlights are stored in `SubtitleEntity.highlights` as `"start,end,COLOR|start,end,COLOR|..."`. Parsed via `parseHighlights()` in `TextHighlight.kt`. The `EpubExporter` already handles this.

---

## Dependency & Import Reference

### New imports needed per file

**LibraryScreen.kt** (already added):
```kotlin
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.VideoDao
import com.deedeedev.ytreader.ui.components.EpubExportDialog
import androidx.compose.material.icons.filled.IosShare
```

**CollectionsScreen.kt** (needs adding):
```kotlin
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.VideoDao
import com.deedeedev.ytreader.ui.components.EpubExportDialog
import androidx.compose.material.icons.filled.IosShare
```

**CollectionDetailScreen.kt** (needs adding):
```kotlin
import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.data.local.VideoDao
import com.deedeedev.ytreader.ui.components.EpubExportDialog
import androidx.compose.material.icons.filled.IosShare
```

**MainScreen.kt** (no new imports needed — `AppContainer` already imported)
