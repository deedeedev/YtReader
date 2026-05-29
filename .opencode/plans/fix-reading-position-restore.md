# Fix: Unreliable Reading Position Restoration

## Problem
When reopening a video in the reader, the saved position (e.g., "30% 27/88") is incorrectly restored to a different position (e.g., "27% 24/88"). Even scrolling further and reopening still restores to the wrong position.

## Root Causes

### 1. Two disconnected coordinate systems
- **Card display**: pixel-based percent = `scrollY / (totalHeight - viewportHeight) * 100`
- **Restoration**: character offset from `getCharOffsetAtTop()`, restored via `scrollToCharOffsetWhenReady()`
- These don't align because charOffset→pixel mapping varies with layout

### 2. `scrollToCharOffsetWhenReady()` is unreliable
- Only 100ms delay before first attempt (content/highlights/bookmarks may not be laid out)
- Falls back to text-percentage (`offset / contentLength * 100`) after 5 failed attempts — this is **text-based percentage**, not the same as the **pixel-based percentage** shown on cards
- `targetY <= 0` check can incorrectly trigger the fallback

### 3. `scrollToPercent()` uses wrong formula
- Computes `totalHeight * percent / 100` instead of `(totalHeight - viewportHeight) * percent / 100`
- Would scroll to wrong position if used directly

### 4. Monotonic percent guard prevents accurate saving
- `ReaderViewModel.updateReadingProgress()` has `if (percent < currentSaved && percent < 100) return`
- Prevents saving a lower percent, so the card shows highest-achieved position, not current position
- On restore, `lastSavedPercent` resets to 0 in a new session, creating inconsistency

### 5. Race condition on initial scroll
- `WebViewStudyContentPane` fires `scrollToCharOffsetWhenReady` after only 100ms
- Content, highlights, and bookmarks load in separate `LaunchedEffect`s
- CharOffset was saved with annotations present, but restoration tries before they're applied

### 6. `webViewCharOffsetAtTop` starts at 0, debounced save skips 0
- `snapshotFlow { webViewCharOffsetAtTop }.debounce(2000)` only saves when `offset > 0`
- If user is near the top, `lastStudyScroll` may never update

## Fix Plan

### Change 1: Fix `scrollToPercent()` formula in `reader.js`

**File**: `app/src/main/assets/reader/reader.js`

Change `scrollToPercent()` to use `(totalHeight - viewportHeight)` instead of `totalHeight`:

```javascript
function scrollToPercent(percent) {
  if (percent <= 0) return;
  var totalHeight = document.body.scrollHeight;
  var viewportHeight = window.innerHeight;
  var maxScroll = totalHeight - viewportHeight;
  if (maxScroll <= 0) return;
  var targetY = Math.round((maxScroll * percent) / 100);
  window.scrollTo(0, Math.min(targetY, maxScroll));
}
```

Also allow `percent >= 100` (removed the `percent >= 100` check) since 100% is a valid position (bottom of content).

### Change 2: Add `readingProgressPercent` property to `ReaderUiState`

**File**: `app/src/main/java/com/deedeedev/ytreader/ui/reader/ReaderViewModel.kt`

Add to `ReaderUiState`:
```kotlin
val readingProgressPercent: Int get() = subtitleWithStates?.readingState?.readingProgressPercent ?: 0
```

### Change 3: Remove monotonic percent guard, add deduplication

**File**: `app/src/main/java/com/deedeedev/ytreader/ui/reader/ReaderViewModel.kt`

Replace:
```kotlin
private val lastSavedPercent = MutableStateFlow(0)

fun updateReadingProgress(percent: Int, currentPage: Int, totalPages: Int) {
    val currentSaved = lastSavedPercent.value
    if (percent < currentSaved && percent < 100) return
    lastSavedPercent.value = percent.coerceIn(0, 100)
    viewModelScope.launch {
        subtitleRepository.updateReadingProgress(
            subtitleId = subtitleId,
            percent = percent.coerceIn(0, 100),
            currentPage = currentPage.coerceAtLeast(0),
            totalPages = totalPages.coerceAtLeast(0)
        )
    }
}
```

With:
```kotlin
private data class ProgressState(val percent: Int, val currentPage: Int, val totalPages: Int)

private val lastSavedProgress = MutableStateFlow<ProgressState?>(null)

fun updateReadingProgress(percent: Int, currentPage: Int, totalPages: Int) {
    val progress = ProgressState(
        percent.coerceIn(0, 100),
        currentPage.coerceAtLeast(0),
        totalPages.coerceAtLeast(0)
    )
    if (progress == lastSavedProgress.value) return
    lastSavedProgress.value = progress
    viewModelScope.launch {
        subtitleRepository.updateReadingProgress(
            subtitleId = subtitleId,
            percent = progress.percent,
            currentPage = progress.currentPage,
            totalPages = progress.totalPages
        )
    }
}
```

This still prevents redundant identical writes but allows the percent to go UP or DOWN.

### Change 4: Add `initialCharOffset` parameter to `WebViewStudyContentPane` and `ReaderScreenLayout`

**File**: `app/src/main/java/com/deedeedev/ytreader/ui/reader/webview/WebViewStudyContentPane.kt`

Change `initialScrollPercent: Int = 0` to two parameters:
```kotlin
initialScrollPercent: Int = 0,  // pixel-based reading progress percent (0-100)
initialCharOffset: Int = 0,      // character offset for precise positioning
```

**File**: `app/src/main/java/com/deedeedev/ytreader/ui/reader/ReaderScreenLayout.kt`

Add `initialCharOffset: Int = 0` parameter to `ReaderScreenMainLayer` and pass it through to `WebViewStudyContentPane`.

### Change 5: Implement two-phase scroll restoration in `WebViewStudyContentPane`

**File**: `app/src/main/java/com/deedeedev/ytreader/ui/reader/webview/WebViewStudyContentPane.kt`

Replace the current `LaunchedEffect` for initial scroll:
```kotlin
LaunchedEffect(isWebViewReady, lastContent, initialScrollPercent, initialCharOffset, annotationScrollOffset) {
    if (!isWebViewReady || hasAppliedInitialScroll) return@LaunchedEffect
    if (lastContent.isEmpty()) return@LaunchedEffect
    val wv = webView ?: return@LaunchedEffect
    if (annotationScrollOffset != null && annotationScrollOffset > 0) {
        delay(300)
        with(WebViewReaderJs) {
            wv.scrollToCharOffsetWhenReady(annotationScrollOffset)
        }
        onAnnotationNavigated?.invoke()
        hasAppliedInitialScroll = true
    } else if (initialScrollPercent > 0 || initialCharOffset > 0) {
        delay(300)
        if (initialScrollPercent > 0) {
            with(WebViewReaderJs) {
                wv.scrollToPercent(initialScrollPercent)
            }
        }
        if (initialCharOffset > 0) {
            delay(100)
            with(WebViewReaderJs) {
                wv.scrollToCharOffsetWhenReady(initialCharOffset)
            }
        }
        hasAppliedInitialScroll = true
    }
}
```

**Key changes**:
- Increased delay from 100ms to 300ms
- Two-phase: first scroll to approximate `readingProgressPercent` position, then refine with charOffset
- This ensures even if charOffset restoration fails, the percent gives a close position

### Change 6: Update `ReaderScreen.kt` - pass both values, debounce progress, save on stop

**File**: `app/src/main/java/com/deedeedev/ytreader/ui/reader/ReaderScreen.kt`

**6a. Pass both `initialScrollPercent` and `initialCharOffset`:**

Replace:
```kotlin
initialScrollPercent = run {
    val v = uiState.lastStudyScroll
    Log.d("PosRestore", "ReaderScreen: initialScrollPercent=$v subtitleId=${subtitle?.id} isLoading=${uiState.isLoading}")
    v
}
```

With:
```kotlin
initialScrollPercent = uiState.readingProgressPercent,
initialCharOffset = uiState.lastStudyScroll,
```

**6b. Debounce `updateReadingProgress` calls:**

Replace:
```kotlin
LaunchedEffect(fullscreenProgressPercent, fullscreenPageProgress) {
    viewModel.updateReadingProgress(
        percent = fullscreenProgressPercent,
        currentPage = fullscreenPageProgress.currentPage,
        totalPages = fullscreenPageProgress.totalPages
    )
}
```

With:
```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { Triple(fullscreenProgressPercent, fullscreenPageProgress.currentPage, fullscreenPageProgress.totalPages) }
        .debounce(500)
        .collectLatest { (percent, currentPage, totalPages) ->
            viewModel.updateReadingProgress(percent, currentPage, totalPages)
        }
}
```

Add import: `import kotlinx.coroutines.flow.collectLatest`

**6c. Save `readingProgressPercent` on lifecycle stop:**

Update `persistReadingProgress`:
```kotlin
val persistReadingProgress by rememberUpdatedState(newValue = {
    viewModel.updateLastStudyScroll(webViewCharOffsetAtTop.coerceAtLeast(0))
    viewModel.updateReadingProgress(
        percent = if (webViewTotalHeight > webViewViewportHeight && webViewTotalHeight > 0) {
            ((webViewScrollY.toFloat() / (webViewTotalHeight - webViewViewportHeight)) * 100).roundToInt().coerceIn(0, 100)
        } else 0,
        currentPage = if (webViewViewportHeight > 0 && webViewTotalHeight > webViewViewportHeight) {
            ((webViewTotalHeight + webViewViewportHeight - 1) / webViewViewportHeight).coerceAtLeast(1).let { totalPages ->
                val maxScrollY = webViewTotalHeight - webViewViewportHeight
                if (webViewScrollY >= maxScrollY - 1) totalPages
                else ((webViewScrollY + webViewViewportHeight) / webViewViewportHeight).coerceIn(1, totalPages)
            }
        } else 1,
        totalPages = if (webViewViewportHeight > 0 && webViewTotalHeight > webViewViewportHeight) {
            ((webViewTotalHeight + webViewViewportHeight - 1) / webViewViewportHeight).coerceAtLeast(1)
        } else 1
    )
})
```

Actually, this is getting too complex inline. A simpler approach - just call the existing progress calculation with current values:

```kotlin
val persistReadingProgress by rememberUpdatedState(newValue = {
    viewModel.updateLastStudyScroll(webViewCharOffsetAtTop.coerceAtLeast(0))
    val percent = if (webViewTotalHeight > webViewViewportHeight) {
        ((webViewScrollY.toFloat() / (webViewTotalHeight - webViewViewportHeight)) * 100).roundToInt().coerceIn(0, 100)
    } else 0
    val totalPages = if (webViewViewportHeight > 0 && webViewTotalHeight > webViewViewportHeight) {
        ((webViewTotalHeight + webViewViewportHeight - 1) / webViewViewportHeight).coerceAtLeast(1)
    } else 1
    val currentPage = if (totalPages <= 1) 1 else {
        val maxScrollY = webViewTotalHeight - webViewViewportHeight
        if (webViewScrollY >= maxScrollY - 1) totalPages
        else ((webViewScrollY + webViewViewportHeight) / webViewViewportHeight).coerceIn(1, totalPages)
    }
    viewModel.updateReadingProgress(percent, currentPage, totalPages)
})
```

Wait, this references `webViewScrollY`, `webViewTotalHeight`, `webViewViewportHeight` which ARE defined before `persistReadingProgress` at line 263. Actually, these are at lines 256-258. So they're available. The problem was only with `fullscreenProgressPercent` defined later. Since we're computing the values inline from `webViewScrollY` etc., this works.

Actually, looking more carefully, `persistReadingProgress` is defined at line 263, and `webViewScrollY`, `webViewTotalHeight`, `webViewViewportHeight` are defined at lines 256-258. So they're accessible. Good.

But wait, `persistReadingProgress` uses `rememberUpdatedState` which captures the lambda. The lambda captures `webViewCharOffsetAtTop` (a state variable), `webViewScrollY`, etc. Since these are Compose state variables, `rememberUpdatedState` will always read the latest value when the lambda is invoked. So this should work.

Actually, I need to reconsider. `rememberUpdatedState` creates a state that always has the latest value. The lambda is NOT a composable scope - it's just a regular function. When the lambda references Compose state variables like `webViewScrollY`, those are read at invocation time, not at composition time. So this should work correctly.

**6d. Remove `offset > 0` guard on `lastStudyScroll` save:**

Replace:
```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { webViewCharOffsetAtTop }
        .debounce(2000)
        .collect { offset ->
            if (offset > 0) {
                viewModel.updateLastStudyScroll(offset)
            }
        }
}
```

With:
```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { webViewCharOffsetAtTop }
        .debounce(500)
        .collect { offset ->
            viewModel.updateLastStudyScroll(offset)
        }
}
```

Changes:
- Removed `if (offset > 0)` guard — now saves even when offset is 0 (user at top of content)
- Reduced debounce from 2000ms to 500ms for more frequent saves

### Change 7: Remove debug log from `ReaderScreen.kt`

Remove the `Log.d("PosRestore", ...)` line that was used for debugging:
```kotlin
initialScrollPercent = run {
    val v = uiState.lastStudyScroll
    Log.d("PosRestore", "ReaderScreen: initialScrollPercent=$v subtitleId=${subtitle?.id} isLoading=${uiState.isLoading}")
    v
}
```

This is replaced by change 6a above.

## Summary ofcoordinate system changes

| Before | After |
|--------|-------|
| Restore uses `lastStudyScroll` (charOffset) only | Restore uses `readingProgressPercent` (pixel-based) first, then refines with `lastStudyScroll` (charOffset) |
| `scrollToCharOffsetWhenReady` called after 100ms | `scrollToPercent` called after 300ms, then `scrollToCharOffsetWhenReady` after additional 100ms |
| `scrollToPercent` uses `totalHeight * percent / 100` (wrong) | `scrollToPercent` uses `(totalHeight - viewportHeight) * percent / 100` (correct) |
| `readingProgressPercent` only goes UP during session | `readingProgressPercent` reflects actual current position |
| `lastStudyScroll` not saved when offset = 0 | `lastStudyScroll` always saved |
| `updateReadingProgress` called on every scroll | `updateReadingProgress` debounced at 500ms |
| Progress not saved on lifecycle stop | Both `lastStudyScroll` and `readingProgressPercent` saved on lifecycle stop |