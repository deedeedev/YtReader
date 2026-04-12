# WebView Reader Experiment — Implementation Plan

## Motivation

The current reader renders the entire study text in a single custom `JustifiedStudyTextView` (a `View` subclass that draws text via `StaticLayout` on a `Canvas`). Despite performance mitigations (hardware layers during scroll, visible-rect clipping, dirty-flag gating), long texts cause jitter and sluggish scroll/selection because:

1. The full `StaticLayout` is rebuilt on any content/style change.
2. All highlights, bookmarks, and search markers are drawn via `Canvas` every frame.
3. The view lives inside a `Column(verticalScroll)`, so Compose manages the scroll — adding overhead for non-lazy content.
4. `requestLayout()` + `invalidate()` on a very tall view is inherently expensive.

A WebView backed by the system Chromium engine offers GPU-composited scrolling, CSS-driven text layout (justification, line-height, font-family), native text selection, and DOM-based highlight overlays — all hardware-accelerated out of the box.

## Scope

| Aspect | Decision |
|--------|----------|
| **Toggle** | Settings switch: "Experimental WebView Reader" (off by default) |
| **Modes** | Both Study and Original |
| **Architecture** | Hybrid — Compose chrome (toolbars, dialogs, FAB, overlays) + WebView via `AndroidView` for text area only |
| **Editing / AI** | Read-only initially; editing/AI cleaning falls back to the native reader or is deferred |
| **Data** | Same `ReaderViewModel`, same `ReaderUiState`, same database. Zero data duplication or migration. |

---

## 1. High-Level Architecture

```
┌──────────────────────────────────────────────┐
│               ReaderScreen (Compose)          │
│  ┌──────────────────────────────────────────┐ │
│  │    ReaderScreenMainLayer (Compose Box)   │ │
│  │  ┌────────────────────────────────────┐  │ │
│  │  │  WebView Reader Pane (AndroidView) │  │ │
│  │  │  ┌──────────────────────────────┐  │  │ │
│  │  │  │  reader.html (asset)         │  │  │ │
│  │  │  │  ├─ CSS: text styling        │  │  │ │
│  │  │  │  ├─ JS:  highlight engine    │  │  │ │
│  │  │  │  ├─ JS:  selection bridge    │  │  │ │
│  │  │  │  └─ JS:  scroll tracker      │  │  │ │
│  │  │  └──────────────────────────────┘  │  │ │
│  │  │  Kotlin ↔ JS bridge                │  │ │
│  │  └────────────────────────────────────┘  │ │
│  │  ┌────────────────────────────────────┐  │ │
│  │  │  Compose overlays (unchanged):     │  │ │
│  │  │  TopBar, BottomBar, FAB,           │  │ │
│  │  │  SelectionToolbar, SearchToolbar,  │  │ │
│  │  │  ProgressIndicator, Brightness     │  │ │
│  │  └────────────────────────────────────┘  │ │
│  └──────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

**Key principle**: The `ReaderViewModel` is untouched. A new set of Composable/content pane files act as the WebView "view layer", reading from the same `ReaderUiState` and calling the same ViewModel methods.

---

## 2. New Files to Create

All under `app/src/main/java/com/deedeedev/ytreader/ui/reader/webview/`:

| File | Purpose |
|------|---------|
| `WebViewReaderPane.kt` | `AndroidView` wrapper hosting the WebView. Manages lifecycle, settings, scroll listeners. |
| `WebViewReaderBridge.kt` | `@JavascriptInterface`-annotated class. Exposes Kotlin callbacks to JS (selection changed, tap, highlight tap, bookmark tap, scroll progress). |
| `WebViewReaderJs.kt` | Kotlin functions that call `evaluateJavascript()` to push state into the WebView (set content, apply highlights, apply bookmarks, update styles, scroll to offset, find text, clear selection). |
| `WebViewStudyContentPane.kt` | Composable replacing `ReaderStudyPane` for WebView mode. Wires ViewModel state → `WebViewReaderJs` calls. |
| `WebViewOriginalContentPane.kt` | Composable replacing `ReaderOriginalPane` for WebView mode. Renders timestamped segments or fallback text in the WebView. |

Under `app/src/main/assets/reader/`:

| File | Purpose |
|------|---------|
| `reader.html` | HTML shell. Loads CSS + JS, defines the content container `<div id="content">`. |
| `reader.css` | Typography, highlight classes, bookmark markers, selection styles, theme variables. |
| `reader.js` | Core engine: content rendering, highlight overlay management, selection handling, bookmark rendering, find/search highlighting, scroll tracking, tap zone detection. |

---

## 3. HTML/CSS/JS Design

### 3.1 `reader.html`

Minimal shell:
```html
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
  <link rel="stylesheet" href="reader.css">
</head>
<body>
  <div id="content"></div>
  <script src="reader.js"></script>
</body>
</html>
```

### 3.2 `reader.css`

Key styles:
- `body`: `margin: 0; padding: 0; -webkit-text-size-adjust: none;` (disable iOS-style auto-adjust; safe on Android).
- `#content`: `padding: 0 16px; text-align: justify;` with CSS custom properties for `--font-size`, `--line-height`, `--font-family`, `--text-color`, `--bg-color` that are updated from Kotlin.
- `.highlight-red`: `background-color: rgba(229, 115, 115, 0.4);` (matching `#66E57373`).
- `.highlight-blue`, `.highlight-green`, `.highlight-yellow`: analogous.
- `.search-result`: `background-color: rgba(255, 179, 0, 0.6);`.
- `.bookmark-marker`: `::after` pseudo-element with a small bookmark shape, `position: absolute; right: 0;`.
- `.note-indicator`: small red dot `::before` pseudo-element.
- `.segment-timestamp`: timestamp styling for Original mode.
- `::selection`: colored to match theme.

### 3.3 `reader.js`

**Global state**:
```js
let highlights = [];
let bookmarks = [];
let searchRange = null;
let selectionChangeTimeout = null;
```

**Functions exposed to Kotlin** (called via `evaluateJavascript`):

| Function | Parameters | Description |
|----------|-----------|-------------|
| `setContent(html)` | `string` | Sets `#content` innerHTML. Re-applies highlights/bookmarks. |
| `setStyles(fontSize, lineHeight, fontFamily, textColor, bgColor)` | `number, number, string, string, string` | Updates CSS custom properties on `#content`. |
| `setHighlights(json)` | `string` (JSON array) | Parses highlight data, wraps matching text ranges in `<span class="highlight-{color}">`. |
| `setBookmarks(json)` | `string` (JSON array) | Inserts bookmark markers at character offsets. |
| `setSearchRange(start, end)` | `number, number` | Highlights the search result range. |
| `clearSearchRange()` | — | Removes search highlighting. |
| `clearSelection()` | — | Removes DOM selection. |
| `setSelectionRange(start, end)` | `number, number` | Programmatically selects character range. |
| `getScrollOffset()` | — | Returns `window.scrollY` (called from Kotlin). |
| `getTotalHeight()` | — | Returns `document.body.scrollHeight`. |
| `scrollToOffset(y)` | `number` | Scrolls to pixel offset. |
| `scrollToCharOffset(offset)` | `number` | Scrolls to the line containing the character offset. |
| `getCharOffsetAtTop()` | — | Returns the character offset of the first visible line. |
| `setOriginalSegments(json)` | `string` (JSON array) | Renders timestamped segments for Original mode. |

**Highlight implementation strategy**:

Use **character-offset-based DOM Range** manipulation. Each highlight is stored as `{start, end, color, id}`. When `setHighlights()` is called:
1. Iterate highlights sorted by `start` ascending.
2. For each highlight, create a `Range` from character offset `start` to `end` using a pre-built text node offset map.
3. `surroundContents()` with a `<span class="highlight-{color}">`.
4. Handle overlapping highlights by nesting spans (outer = earlier start, inner = later start within same range).

**Character offset map**: On `setContent()`, walk all text nodes in `#content` and build an array of `{node, start, end}` entries. This maps flat character offsets to DOM text node positions. This is rebuilt whenever content changes.

**Selection handling**:
- Listen to `selectionchange` event with debounce (100ms).
- On selection change, compute character offsets from the DOM `Selection` range using the offset map.
- Call `Bridge.onSelectionChanged(start, end)`.

**Tap zone detection**:
- Listen to `click` event on `#content`.
- Compute `(x / viewportWidth, y / viewportHeight)` fractions.
- Check if tap landed on a highlight span → call `Bridge.onHighlightTapped(id)`.
- Check if tap landed on a bookmark marker → call `Bridge.onBookmarkTapped(id)`.
- Otherwise call `Bridge.onTap(xFraction, yFraction)`.

**Scroll tracking**:
- Listen to `scroll` event with throttle (16ms ≈ 60fps).
- Call `Bridge.onScrollProgress(scrollY, totalHeight, viewportHeight)`.

---

## 4. Kotlin ↔ JavaScript Bridge

### 4.1 `WebViewReaderBridge.kt`

```kotlin
class WebViewReaderBridge : Any {
    @JavascriptInterface
    fun onSelectionChanged(start: Int, end: Int) { ... }

    @JavascriptInterface
    fun onHighlightTapped(highlightId: String?) { ... }

    @JavascriptInterface
    fun onBookmarkTapped(bookmarkId: Long) { ... }

    @JavascriptInterface
    fun onTap(xFraction: Float, yFraction: Float) { ... }

    @JavascriptInterface
    fun onScrollProgress(scrollY: Int, totalHeight: Int, viewportHeight: Int) { ... }

    @JavascriptInterface
    fun onContentHeightChanged(height: Int) { ... }
}
```

Each `@JavascriptInterface` method posts to a `Handler(Looper.getMainLooper())` to call the corresponding Compose-side callback (passed in constructor or set as mutable properties). This is necessary because JS bridge calls happen on a background thread.

### 4.2 `WebViewReaderJs.kt`

Utility object with functions like:

```kotlin
object WebViewReaderJs {
    fun WebView.setContent(content: String) {
        val escaped = content.escapeForJs()
        evaluateJavascript("setContent('$escaped')", null)
    }

    fun WebView.setHighlights(highlights: List<TextHighlight>) { ... }
    fun WebView.setBookmarks(bookmarks: List<BookmarkEntity>) { ... }
    fun WebView.setStyles(...) { ... }
    fun WebView.scrollToCharOffset(offset: Int) { ... }
    fun WebView.getScrollOffset(): Int { ... }  // uses evaluateJavascript with ValueCallback
    // etc.
}
```

Uses `evaluateJavascript()` for fire-and-forget calls, and `evaluateJavascript(script, ValueCallback)` for queries.

**Text escaping**: Content text must be JS-escaped (backslash, quotes, newlines). Use a simple escaper or `JSONObject.quote()`.

**Highlight data format**: JSON array of `{"start": 123, "end": 456, "color": "red", "id": "h_123_456"}` where `id` is deterministic (`"h_${start}_${end}"`) so bridge callbacks can reference specific highlights.

---

## 5. Integration Points

### 5.1 Settings Toggle

**`UserPreferencesRepository.kt`**:
- Add `_useWebViewReader = MutableStateFlow(false)`.
- Add `val useWebViewReader: StateFlow<Boolean>`.
- Add `fun setUseWebViewReader(enabled: Boolean)`.
- Persist with key `"use_webview_reader"`.

**`SettingsScreen.kt`** (or `SettingsSections.kt`):
- Add an "Experimental" section with a `Switch` toggle for "WebView Reader".
- Include a description: "Uses a WebView to render text. May improve performance with long texts. Some features like editing are not yet available."

**String resources** (`strings.xml`):
- `settings_webview_reader_title` = "WebView Reader (Experimental)"
- `settings_webview_reader_description` = "Use a WebView-based text renderer. May improve scrolling performance with long texts. Editing and AI cleaning are not yet supported in this mode."

### 5.2 Reader Routing

**`ReaderScreen.kt`**:
- At the top level of `ReaderScreen`, observe `userPreferencesRepository.useWebViewReader`.
- Pass a `useWebView: Boolean` flag down to `ReaderScreenMainLayer`.

**`ReaderScreenLayout.kt`**:
- `ReaderScreenMainLayer` receives `useWebView: Boolean`.
- In the content area section (lines 123-177), branch on `useWebView`:
  - If `false`: existing `ReaderStudyPane` / `ReaderOriginalPane` (unchanged).
  - If `true`: `WebViewStudyContentPane` / `WebViewOriginalContentPane`.

### 5.3 Handling Edit Mode in WebView Reader

When `useWebView` is `true` and the user triggers edit mode:
- **Option A (recommended)**: Disable the edit button in the bottom bar. Show a toast: "Editing is not yet available in WebView reader. Switch to the standard reader in Settings to edit text."
- **Option B**: Switch to the native reader temporarily for the edit session, then switch back.
- AI cleaning button should also be hidden/disabled with the same explanation.

### 5.4 Feature Compatibility Matrix

| Feature | WebView (Phase 1) | Notes |
|---------|-------------------|-------|
| Study text rendering | Yes | HTML with justified text |
| Original text rendering | Yes | Segments with timestamps |
| Font size / family | Yes | CSS custom properties |
| Line height | Yes | CSS custom properties |
| Text selection | Yes | Native WebView selection + bridge |
| Highlight creation | Yes | Selection → toolbar → ViewModel |
| Highlight display (4 colors) | Yes | CSS background spans |
| Highlight note indicator | Yes | CSS `::before` dot |
| Bookmark display | Yes | CSS bookmark marker |
| Bookmark tap | Yes | JS hit-testing |
| Tap zones (page/UI toggle) | Yes | JS click handler |
| Find/Search | Yes | JS-based text search + highlight |
| Scroll progress tracking | Yes | JS scroll listener |
| Progress bar | Yes | Compose overlay (unchanged) |
| Brightness gesture | Yes | Compose overlay (unchanged) |
| Annotations swipe | Yes | Compose overlay (unchanged) |
| VideoNotes navigation | Yes | Same NavHost integration |
| Text editing | No (Phase 2) | Hidden, shows explanation |
| AI cleaning | No (Phase 2) | Hidden, shows explanation |
| Scroll position restore | Yes | JS `scrollToCharOffset` |

---

## 6. Implementation Steps (Ordered)

### Phase 1: Foundation (Estimated: ~4-5 hours)

1. **Add preference to `UserPreferencesRepository`** (~15 min)
   - Add `useWebViewReader` StateFlow, getter, setter, persistence.
   - Update `PreferencesBackup` data class and export/import methods.

2. **Add Settings UI** (~30 min)
   - Add "Experimental" section in `SettingsScreen.kt` with the toggle.
   - Add string resources.

3. **Create HTML/CSS/JS assets** (~2 hours)
   - `reader.html`, `reader.css`, `reader.js` in `app/src/main/assets/reader/`.
   - Implement: content rendering, CSS variable-based styling, highlight span wrapping, bookmark markers, note indicators.
   - Implement: text offset map (character offset → DOM text node/offset).
   - Implement: selection change detection with character offset computation.
   - Implement: tap detection with highlight/bookmark hit-testing and zone classification.
   - Implement: scroll progress tracking.
   - Implement: find/search highlighting.

4. **Create `WebViewReaderBridge.kt`** (~45 min)
   - `@JavascriptInterface` methods for all JS → Kotlin callbacks.
   - Thread-safe callback invocation via `Handler(Looper.getMainLooper())`.

5. **Create `WebViewReaderJs.kt`** (~45 min)
   - Kotlin extension functions on `WebView` to call JS functions.
   - Text escaping, JSON serialization for highlights/bookmarks.

### Phase 2: Study Mode Integration (Estimated: ~3-4 hours)

6. **Create `WebViewStudyContentPane.kt`** (~2 hours)
   - `AndroidView` factory: create `WebView`, load `file:///android_asset/reader/reader.html`, inject bridge.
   - `update` block: call `WebViewReaderJs` methods when state changes.
   - Wire callbacks: selection → ViewModel, taps → controller, scroll → progress.
   - Handle scroll position save/restore (track `scrollY` and total height).

7. **Wire into `ReaderScreenLayout.kt`** (~30 min)
   - Add `useWebView` parameter.
   - Branch content rendering.

8. **Wire into `ReaderScreen.kt`** (~30 min)
   - Observe `useWebViewReader` preference.
   - Pass flag down.
   - Handle WebView-specific scroll state (separate from native `ScrollState`).

9. **Handle Edit/AI fallback** (~30 min)
   - Conditionally hide edit/AI buttons in `ReaderBottomBar` when `useWebView && readerMode == STUDY`.
   - Show informational toast if somehow triggered.

### Phase 3: Original Mode Integration (Estimated: ~2-3 hours)

10. **Create `WebViewOriginalContentPane.kt`** (~1.5 hours)
    - Render timestamped segments as HTML: `<div class="segment"><div class="timestamp">0:30</div><div class="text">...</div></div>`.
    - Fallback: plain text (same as study mode but without highlights).
    - Selection coordination (only one segment selectable at a time).

11. **Wire Original mode** (~30 min)
    - In `ReaderScreenLayout.kt`, when `readerMode == ORIGINAL && useWebView`, use `WebViewOriginalContentPane`.
    - Handle timestamp tap → `onOriginalTimestampTap`.

### Phase 4: Scroll Position & Navigation (Estimated: ~2 hours)

12. **Scroll position persistence** (~1 hour)
    - On WebView scroll changes, track `(scrollY / totalHeight)` as a percentage.
    - On navigate-away, save percentage to the same mechanism used by native reader.
    - On navigate-back, restore by scrolling to `percentage * totalHeight`.

13. **Navigate to highlight/bookmark** (~45 min)
    - `scrollToCharOffset(offset)`: find the text node at the character offset, use `getBoundingClientRect()` to get its Y position, scroll to it.
    - Wire `initialHighlightRange` and `initialBookmarkStart` navigation.

14. **Navigate from VideoNotes** (~15 min)
    - Already handled by `initialHighlightRange`/`initialBookmarkStart` — just need the WebView to respond to the same navigation triggers.

### Phase 5: Polish & Testing (Estimated: ~2-3 hours)

15. **Theme support** (~30 min)
    - Pass theme colors (dark/light) to JS via `setStyles()`.
    - Update CSS variables when theme changes.

16. **Performance testing** (~1 hour)
    - Test with texts of various lengths: 1K, 10K, 50K, 100K+ characters.
    - Compare scroll smoothness, selection responsiveness, highlight rendering.
    - Measure WebView initialization time.

17. **Edge cases** (~1 hour)
    - Empty content, very short content.
    - Highlights at text boundaries.
    - Overlapping highlights.
    - Concurrent highlight changes during scrolling.
    - Screen rotation.
    - Multi-window/split-screen.

18. **Unit tests** (~1 hour)
    - Test `WebViewReaderBridge` callback routing.
    - Test `WebViewReaderJs` escaping/serialization.
    - Test JS highlight rendering logic (could be tested in a headless WebView or via instrumented tests).

---

## 7. Technical Considerations

### 7.1 WebView Initialization Latency

WebView has a cold-start cost (~100-300ms). Mitigations:
- Use a global `WebView` pool or eagerly initialize on app start.
- Show a loading indicator while the WebView initializes.
- The `reader.html` asset is local, so there's no network latency.

### 7.2 Memory

WebView uses more memory than a custom View (Chromium renderer process). For typical subtitle texts (< 100KB), this is negligible. For very large texts (> 1MB), monitor memory usage.

### 7.3 Text Selection in WebView

Android WebView supports text selection natively, but it shows the system selection menu (Copy, Select All, Share). We need to:
- Disable the system action menu: override `onActionModeStarted` in the Activity, or use CSS `-webkit-user-select: text` and handle selection entirely via JS.
- **Recommended approach**: Use JS `selectionchange` + `Range` API for selection tracking, and hide the native WebView selection menu. This gives us full control over selection handles and the toolbar.

### 7.4 Highlight Persistence with Character Offsets

The existing system uses flat character offsets (`start`, `end`) relative to the study text string. The WebView implementation must use the **same** offset system. The JS offset map must be consistent with Kotlin's string indexing.

**Risk**: Unicode normalization differences between Kotlin strings and JavaScript strings. Mitigation: both use UTF-16 code units (JavaScript strings, Kotlin `String`), so offsets should match. Test with multi-byte characters (CJK, emoji).

### 7.5 Scroll Synchronization

Compose overlays (progress bar, brightness indicator) depend on knowing the scroll state. The WebView manages its own scrolling internally. We bridge this via:
- JS `scroll` event → `Bridge.onScrollProgress(scrollY, totalHeight, viewportHeight)`.
- Kotlin side computes `scrollPercent = scrollY / (totalHeight - viewportHeight)` and updates the same `derivedStateOf` that drives the progress indicator.

### 7.6 Touch Event Interception

The brightness gesture (left-edge vertical drag) and annotations swipe (bottom-edge vertical swipe) are Compose-level gesture detectors overlaid on top of the WebView. These should work because they are in the Compose layer above the `AndroidView`. The WebView receives touches that are not consumed by Compose gesture detectors.

However, tap handling needs coordination:
- If the tap is in the left 5% of the screen, the brightness gesture area should consume it.
- Otherwise, the WebView should receive the tap.
- The existing `ReaderBrightnessGestureArea` and `ReaderAnnotationsSwipeArea` are Compose overlays that use `pointerInput` modifiers — they should continue to work since they're layered on top.

### 7.7 Editing Mode Fallback

When `useWebView == true` and the user wants to edit:
- Option: temporarily switch to the native reader. This requires saving WebView scroll position, recreating the native view, and restoring after edit.
- Simpler option for Phase 1: disable editing entirely in WebView mode, with a clear message directing users to Settings to toggle back.

---

## 8. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| WebView text selection is buggy or inconsistent across Android versions | Medium | High | Test on API 26-36. Use JS-based selection only, not native WebView selection. |
| Character offset mismatch between Kotlin and JS | Low | High | Both use UTF-16. Add validation tests with diverse character sets. |
| WebView memory usage too high for low-end devices | Low | Medium | The toggle lets users choose. Native reader remains default. |
| Highlight wrapping breaks on complex HTML structures | Medium | Medium | Keep HTML structure flat (single `<div id="content">` with text nodes + highlight spans). No nested paragraphs. |
| Scroll position not perfectly preserved across mode switches | Medium | Low | Use percentage-based position rather than pixel offset. Accept minor imprecision. |
| CSS `text-align: justify` looks different from `StaticLayout.JUSTIFICATION_MODE_INTER_WORD` | Medium | Low | Accept minor visual differences. The WebView version is "good enough" for the experiment. |

---

## 9. Files Modified (Summary)

| File | Change |
|------|--------|
| `data/UserPreferencesRepository.kt` | Add `useWebViewReader` preference |
| `ui/settings/SettingsScreen.kt` | Add experimental section with toggle |
| `ui/reader/ReaderScreen.kt` | Observe preference, pass `useWebView` flag |
| `ui/reader/ReaderScreenLayout.kt` | Branch on `useWebView` for content pane selection |
| `ui/reader/ReaderChrome.kt` | Conditionally hide edit/AI buttons |
| `res/values/strings.xml` | Add WebView reader strings |

## 10. Files Created (Summary)

| File | Lines (est.) |
|------|-------------|
| `assets/reader/reader.html` | ~20 |
| `assets/reader/reader.css` | ~120 |
| `assets/reader/reader.js` | ~500 |
| `ui/reader/webview/WebViewReaderPane.kt` | ~150 |
| `ui/reader/webview/WebViewReaderBridge.kt` | ~100 |
| `ui/reader/webview/WebViewReaderJs.kt` | ~200 |
| `ui/reader/webview/WebViewStudyContentPane.kt` | ~200 |
| `ui/reader/webview/WebViewOriginalContentPane.kt` | ~150 |

**Total new code**: ~1,440 lines  
**Total modified code**: ~60 lines across 6 existing files  
**No changes to**: ViewModel, database, entities, navigation routes, data layer.

---

## 11. Future Phases (Out of Scope for Phase 1)

- **Phase 2**: Edit mode support in WebView (contentEditable or textarea overlay).
- **Phase 2**: AI cleaning integration (show preview in WebView, apply changes).
- **Phase 3**: If the experiment is successful, make WebView the default and deprecate the native reader.
- **Phase 3**: Consider paginated (horizontal swipe) reading mode via CSS columns or viewport-based pagination in WebView.
