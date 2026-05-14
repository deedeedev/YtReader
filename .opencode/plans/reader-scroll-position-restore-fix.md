# Reader Scroll Position Not Restored on Reopen

**Date:** 2026-05-14
**Status:** Fixed
**Files changed:** `app/src/main/assets/reader/reader.js`

## Symptoms

When reopening a video subtitle in the reader, the saved scroll position was not restored. The text always displayed from the beginning, even though the saved position was correctly displayed on the video card in the library.

## Root Cause

The scroll restoration relies on `scrollToCharOffsetWhenReady()` in `reader.js`, which uses `getBoundingClientRect()` to find the pixel position of a character offset within the DOM. This function retried up to 20 times via `requestAnimationFrame` when the position came back as 0.

The problem was in `setContent()`:

```javascript
function setContent(html) {
  const container = document.getElementById("content");
  container.textContent = html;  // Creates ONE giant text node
  // ...
}
```

`container.textContent = html` creates a **single text node** containing the entire subtitle content (e.g., 103,530 characters). The Android WebView (Chromium-based) can compute `document.body.scrollHeight` (142,308 px) from estimated font metrics without doing full per-character layout. However, `getBoundingClientRect()` requires the rendering engine to have completed actual text layout — line breaking, character positioning, etc.

For a single ~100K character text node, the WebView defers this expensive layout computation. As a result, `getBoundingClientRect()` returns `{top: 0}` for **all** character positions within that node, even after 20 retry attempts spanning ~350ms. The scroll function interpreted `targetY=0` as "not ready yet" and kept retrying until exhausting all attempts, then scrolled to position 0 (the top).

### Why it worked on first open but not on reopen

- **First open:** `lastStudyScroll=0` (never scrolled), so the scroll effect was **skipped entirely**. The user scrolled manually after the WebView had plenty of time to complete text layout.
- **Reopen:** `lastStudyScroll=1615` (saved position), so the scroll effect fired ~200ms after `setContent()`. The WebView hadn't completed text layout yet, causing all `getBoundingClientRect()` calls to return 0.

### Evidence from logs

```
wvMeasured=[1032x2159]     // WebView IS laid out by Android
innerH=719                 // Viewport IS non-zero
bodyH=142308               // scrollHeight IS computed
contentLen=103530          // Content IS loaded
rect.top=0                 // But character positions NOT computed (all 20 attempts)
```

## Fix

Added a **percentage-based scroll fallback** in `scrollToCharOffsetWhenReady()`. After 5 failed attempts where `getBoundingClientRect()` returns `targetY <= 0`, the function computes an approximate pixel position from the character offset as a percentage of total text length:

```javascript
if (attempt >= 5 && contentLen > 0) {
  var percent = (offset / contentLen) * 100;
  var maxScroll = totalHeight - viewportHeight;
  if (maxScroll > 0 && percent > 0 && percent < 100) {
    var fallbackY = Math.round((percent / 100) * maxScroll);
    window.scrollTo(0, fallbackY);
    return;  // Stop retrying
  }
}
```

This is accurate to within ~1-2 lines because `scrollHeight` is computed correctly (verified: target offset 1615 produced actual position 1593).

### Trade-offs

- **Precision:** The percentage-based approach is approximate. Characters at the start of the document map to a slightly different percentage than characters at the end due to non-uniform line heights. In practice, the error is small (~22 characters for a 103K document).
- **Cross-line highlights unaffected:** Since this doesn't change the DOM structure (unlike splitting the content into multiple elements), highlight ranges that span multiple lines continue to work via `range.surroundContents()`.
- **Retry budget:** Increased from 20 to 30 attempts. The fallback triggers at attempt 5, so there are 5 attempts for `getBoundingClientRect()` to work (for smaller documents where layout completes quickly) before falling back.

## Future Improvements

A more robust long-term fix would be to split the content in `setContent()` into multiple smaller DOM elements (e.g., one `<span>` per paragraph/line) instead of one giant text node. This would allow the WebView to lay out each element independently and make `getBoundingClientRect()` work quickly for all character positions. This was not implemented to avoid risking regressions with highlight/bookmark range computations that span element boundaries.
