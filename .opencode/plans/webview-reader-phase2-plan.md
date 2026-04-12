# WebView Reader Phase 2 Implementation Plan

## Overview

Phase 2 adds complete editing capabilities, AI cleaning integration, share/copy functionality, and find/replace - all implemented directly in the WebView layer. The WebView reader becomes fully independent from the native reader for all features.

**Status**: IMPLEMENTED

---

## 1. Architecture Decision: WebView-Native Independence

Instead of switching to the native reader for editing/AI features, we implemented all functionality directly in the WebView:

| Feature | Phase 1 (Native) | Phase 2 (WebView-Native) |
|---------|------------------|-------------------------|
| Edit mode | Switch to native | WebView `contenteditable` mode |
| Text editing | Native | WebView JS text manipulation |
| Remove empty lines | Native | WebView JS regex |
| Replace with clipboard | Native | WebView JS + paste handler |
| AI cleaning | N/A | WebView JS + external share |

**Key Principle**: All editing features run entirely in the WebView/JS layer. No switch to native reader needed.

---

## 2. Editing Features (Implemented In WebView)

### 2.1 Edit Mode - IMPLEMENTED

- JS function `setEditMode(enabled)` toggles `contenteditable` on `#content`
- Visual indicator added via CSS (`edit-mode` class)
- Kotlin coordinates via `WebViewStudyContentPane` with `isEditMode` parameter
- Save flow extracts `innerText` from WebView and calls `viewModel.updateContent()`

### 2.2 Text Editing Operations - IMPLEMENTED

| Operation | JS Function |
|-----------|------------|
| Remove empty lines | `removeEmptyLines()` |
| Replace with clipboard | `replaceWithText(text, replaceAllFlag)` |
| Trim whitespace | `trimWhitespace()` |
| Normalize spacing | `normalizeSpacing()` |
| Capitalize first | `capitalizeFirstLetter()` |

### 2.3 Replace with Clipboard - IMPLEMENTED

Uses existing Android `ClipboardManager` + JS `replaceWithText()`

---

## 3. AI Cleaning Features - IMPLEMENTED

AI cleaning now works in WebView without switching:
- External AI share via Android Intent.ACTION_SEND
- Internal AI cleaning via WorkManager
- Text extracted from WebView via `getAllText()`

---

## 4. Share and Copy - IMPLEMENTED

- Share uses Android Intent.ACTION_SEND
- Copy uses Android ClipboardManager
- WebView text extraction via JS bridge

---

## 5. Find and Replace - IMPLEMENTED

| Function | Description |
|----------|-------------|
| `findNext()` | Find next match, highlight |
| `findPrevious()` | Find previous match, highlight |
| `replaceSingle()` | Replace current match |
| `replaceAll()` | Replace all occurrences |
| `getMatchCount()` | Return count of matches |
| `clearFindHighlights()` | Clear all find highlights |
| `highlightFindMatch()` | Highlight specific match |

Case-sensitive and case-insensitive options supported.
Offset map is rebuilt after each replace operation.

---

## 6. Search in Original Subtitles - IMPLEMENTED

Find functionality works in both Study and Original modes.

---

## 7. Video Notes Integration - VERIFIED

Existing functionality works in WebView mode.

---

## 8. Bug Fixes from Phase 1

### Font Size Not Updating - FIXED
- Added explicit LaunchedEffect in WebViewStudyContentPane for font size changes
- setStyles is called when fontSize parameter changes

### Other Issues
- Brightness gesture: Works (Compose overlay)
- Annotations swipe: Works (Compose overlay)

---

## Files Modified

| File | Change |
|------|-------|
| `assets/reader/reader.js` | Add edit mode, find/replace functions, text operations |
| `assets/reader/reader.css` | Add edit mode styling, find match styling |
| `ui/reader/webview/WebViewStudyContentPane.kt` | Add isEditMode param, edit operation callbacks |
| `ui/reader/webview/WebViewReaderBridge.kt` | Add onContentTextChanged callback |
| `ui/reader/webview/WebViewReaderJs.kt` | Add 15+ JS bridge functions |
| `ui/reader/ReaderChrome.kt` | Enable edit/AI buttons in WebView mode |
| `ui/reader/ReaderScreenLayout.kt` | Wire WebView callbacks for edit operations |
| `ui/reader/ReaderScreen.kt` | Pass webViewStudyRef to layout |

---

## Feature Compatibility Matrix (Phase 2)

| Feature | WebView Phase 2 |
|---------|----------------|
| Study text rendering | Yes |
| Original text rendering | Yes |
| Text selection | Yes |
| Highlight creation | Yes |
| Bookmark display | Yes |
| Tap zones | Yes |
| Find/Search | Yes |
| Scroll position restore | Yes |
| Edit mode | Yes |
| Text editing | Yes |
| Remove empty lines | Yes |
| Replace with clipboard | Yes |
| AI cleaning | Yes |
| Share text | Yes |
| Copy text | Yes |
| Find and replace | Yes |
| Replace all | Yes |
| Replace single | Yes |
| Search in original | Yes |
| VideoNotes navigation | Yes |

---

## WebView Reader Independence

After Phase 2, the WebView reader has feature parity with the native reader:

| Feature | Native Reader | WebView Reader Phase 2 |
|---------|--------------|----------------------|
| Study text | Yes | Yes |
| Original text | Yes | Yes |
| Highlights | Yes | Yes |
| Bookmarks | Yes | Yes |
| Selection | Yes | Yes |
| Tap zones | Yes | Yes |
| Find/Search | Yes | Yes |
| Edit mode | Yes | Yes |
| Text editing | Yes | Yes |
| Remove empty lines | Yes | Yes |
| Replace clipboard | Yes | Yes |
| AI cleaning | Yes | Yes |
| Share | Yes | Yes |
| Copy | Yes | Yes |
| Find/Replace | Yes | Yes |
| Scroll position | Yes | Yes |

**Status**: Feature parity achieved. WebView can now replace native reader.

---

## Risk Assessment (Lessons Learned)

| Risk | Likelihood | Mitigation Applied |
|------|-----------|-------------------|
| `contenteditable` performance | Low | Works well for typical text sizes |
| Replace breaks offset map | Medium | Offset map rebuilt after each operation |
| Edit mode conflicts with selection | Medium | Selection continues to work |

---

## Testing Checklist

- [x] Enter edit mode in WebView
- [x] Type text directly in WebView
- [x] Save edited text, verify persistence
- [x] Remove empty lines operation
- [x] Replace with clipboard operation
- [x] Clean with AI via share
- [x] Share selected text
- [x] Copy selected text
- [x] Find and replace in WebView
- [x] Replace all in WebView
- [x] Replace single in WebView
- [x] Search in original mode
- [x] Font size changes reflect immediately
- [x] Brightness gesture works
- [x] Annotations swipe works

