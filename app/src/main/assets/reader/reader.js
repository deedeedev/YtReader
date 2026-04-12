"use strict";

let highlights = [];
let bookmarks = [];
let searchRange = null;
let offsetMap = [];
let selectionChangeTimeout = null;
let scrollThrottleTimeout = null;

function buildOffsetMap() {
  offsetMap = [];
  const container = document.getElementById("content");
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null, false);
  let currentOffset = 0;
  while (walker.nextNode()) {
    const node = walker.currentNode;
    const len = node.textContent.length;
    offsetMap.push({ node: node, start: currentOffset, end: currentOffset + len });
    currentOffset += len;
  }
}

function findOffsetEntry(charOffset) {
  for (let i = 0; i < offsetMap.length; i++) {
    if (charOffset >= offsetMap[i].start && charOffset < offsetMap[i].end) {
      return { node: offsetMap[i].node, localOffset: charOffset - offsetMap[i].start };
    }
  }
  return null;
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.appendChild(document.createTextNode(text));
  return div.innerHTML;
}

function setContent(html) {
  const container = document.getElementById("content");
  const escaped = escapeHtml(html);
  const paragraphs = escaped.split(/\n/).map(function(line) {
    return "<p>" + (line || "&nbsp;") + "</p>";
  }).join("");
  container.innerHTML = paragraphs;
  buildOffsetMap();
  applyHighlights();
  applyBookmarks();
  applySearchRange();
  Bridge.onContentHeightChanged(document.body.scrollHeight);
}

function setStyles(fontSize, lineHeight, fontFamily, textColor, bgColor) {
  const root = document.documentElement;
  root.style.setProperty("--font-size", fontSize + "px");
  root.style.setProperty("--line-height", lineHeight);
  root.style.setProperty("--font-family", fontFamily);
  root.style.setProperty("--text-color", textColor);
  root.style.setProperty("--bg-color", bgColor);
}

function applyHighlights() {
  const container = document.getElementById("content");
  const spans = container.querySelectorAll("span[data-highlight-id]");
  for (let i = 0; i < spans.length; i++) {
    const parent = spans[i].parentNode;
    while (spans[i].firstChild) {
      parent.insertBefore(spans[i].firstChild, spans[i]);
    }
    parent.removeChild(spans[i]);
  }
  parent.normalize ? container.normalize() : void 0;
  buildOffsetMap();

  var sorted = highlights.slice().sort(function(a, b) { return a.start - b.start || b.end - a.end; });
  for (var i = sorted.length - 1; i >= 0; i--) {
    var h = sorted[i];
    var startEntry = findOffsetEntry(h.start);
    var endEntry = findOffsetEntry(h.end);
    if (!startEntry || !endEntry) continue;

    try {
      var range = document.createRange();
      range.setStart(startEntry.node, startEntry.localOffset);
      range.setEnd(endEntry.node, endEntry.localOffset);
      var span = document.createElement("span");
      span.setAttribute("data-highlight-id", h.id);
      span.className = "highlight-" + h.color;
      if (h.hasNote) {
        span.classList.add("has-note");
      }
      range.surroundContents(span);
    } catch (e) {
      // Skip highlights that cross element boundaries
    }
  }
  buildOffsetMap();
}

function setHighlights(json) {
  highlights = JSON.parse(json);
  var contentEl = document.getElementById("content");
  var spans = contentEl.querySelectorAll("span[data-highlight-id]");
  for (var i = 0; i < spans.length; i++) {
    var parent = spans[i].parentNode;
    while (spans[i].firstChild) {
      parent.insertBefore(spans[i].firstChild, spans[i]);
    }
    parent.removeChild(spans[i]);
  }
  if (contentEl.normalize) contentEl.normalize();
  buildOffsetMap();
  applyHighlights();
}

function applyBookmarks() {
  var container = document.getElementById("content");
  var markers = container.querySelectorAll(".bookmark-marker");
  for (var i = 0; i < markers.length; i++) {
    markers[i].classList.remove("bookmark-marker");
    markers[i].removeAttribute("data-bookmark-id");
  }
  buildOffsetMap();

  for (var i = 0; i < bookmarks.length; i++) {
    var bm = bookmarks[i];
    var entry = findOffsetEntry(bm.anchorStart);
    if (!entry) continue;
    var parent = entry.node.parentElement;
    if (parent) {
      parent.classList.add("bookmark-marker");
      parent.setAttribute("data-bookmark-id", bm.id);
    }
  }
}

function setBookmarks(json) {
  bookmarks = JSON.parse(json);
  var container = document.getElementById("content");
  var markers = container.querySelectorAll(".bookmark-marker");
  for (var i = 0; i < markers.length; i++) {
    markers[i].classList.remove("bookmark-marker");
    markers[i].removeAttribute("data-bookmark-id");
  }
  buildOffsetMap();
  applyBookmarks();
}

function applySearchRange() {
  var container = document.getElementById("content");
  var existing = container.querySelectorAll(".search-result");
  for (var i = 0; i < existing.length; i++) {
    var parent = existing[i].parentNode;
    while (existing[i].firstChild) {
      parent.insertBefore(existing[i].firstChild, existing[i]);
    }
    parent.removeChild(existing[i]);
  }
  if (container.normalize) container.normalize();

  if (!searchRange) return;
  buildOffsetMap();

  var startEntry = findOffsetEntry(searchRange.start);
  var endEntry = findOffsetEntry(searchRange.end);
  if (!startEntry || !endEntry) return;

  try {
    var range = document.createRange();
    range.setStart(startEntry.node, startEntry.localOffset);
    range.setEnd(endEntry.node, endEntry.localOffset);
    var span = document.createElement("span");
    span.className = "search-result";
    range.surroundContents(span);
  } catch (e) {
    // Skip ranges that cross element boundaries
  }
  buildOffsetMap();
}

function setSearchRange(start, end) {
  searchRange = { start: start, end: end };
  applySearchRange();
}

function clearSearchRange() {
  searchRange = null;
  applySearchRange();
}

function clearSelection() {
  var sel = window.getSelection();
  if (sel) sel.removeAllRanges();
}

function setSelectionRange(start, end) {
  clearSelection();
  buildOffsetMap();
  var startEntry = findOffsetEntry(start);
  var endEntry = findOffsetEntry(end);
  if (!startEntry || !endEntry) return;
  var range = document.createRange();
  range.setStart(startEntry.node, startEntry.localOffset);
  range.setEnd(endEntry.node, endEntry.localOffset);
  var sel = window.getSelection();
  if (sel) sel.addRange(range);
}

function getScrollOffset() {
  return window.scrollY;
}

function getTotalHeight() {
  return document.body.scrollHeight;
}

function scrollToOffset(y) {
  window.scrollTo(0, y);
}

function scrollToCharOffset(offset) {
  buildOffsetMap();
  var entry = findOffsetEntry(offset);
  if (!entry) return;
  var range = document.createRange();
  range.setStart(entry.node, entry.localOffset);
  range.collapse(true);
  var rect = range.getBoundingClientRect();
  var targetY = window.scrollY + rect.top - window.innerHeight / 3;
  window.scrollTo(0, Math.max(0, targetY));
}

function getCharOffsetAtTop() {
  buildOffsetMap();
  var viewportTop = window.scrollY;
  for (var i = 0; i < offsetMap.length; i++) {
    if (!offsetMap[i].node.parentElement) continue;
    var range = document.createRange();
    range.setStart(offsetMap[i].node, 0);
    range.collapse(true);
    var rect = range.getBoundingClientRect();
    if (rect.top + window.scrollY >= viewportTop) {
      return offsetMap[i].start;
    }
  }
  return 0;
}

function setOriginalSegments(json) {
  var segments = JSON.parse(json);
  var container = document.getElementById("content");
  var html = "";
  for (var i = 0; i < segments.length; i++) {
    var seg = segments[i];
    html += '<div class="segment">';
    if (seg.timestamp) {
      html += '<div class="segment-timestamp" data-start-time="' + seg.startTime + '">' + escapeHtml(seg.timestamp) + '</div>';
    }
    html += '<div class="segment-text">' + escapeHtml(seg.text) + '</div>';
    html += '</div>';
  }
  container.innerHTML = html;
  buildOffsetMap();
  Bridge.onContentHeightChanged(document.body.scrollHeight);
}

// Selection change handler
document.addEventListener("selectionchange", function() {
  if (selectionChangeTimeout) clearTimeout(selectionChangeTimeout);
  selectionChangeTimeout = setTimeout(function() {
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) {
      Bridge.onSelectionChanged(-1, -1);
      return;
    }
    try {
      var range = sel.getRangeAt(0);
      buildOffsetMap();
      var startOffset = -1;
      var endOffset = -1;
      for (var i = 0; i < offsetMap.length; i++) {
        if (offsetMap[i].node === range.startContainer) {
          startOffset = offsetMap[i].start + range.startOffset;
        }
        if (offsetMap[i].node === range.endContainer) {
          endOffset = offsetMap[i].start + range.endOffset;
        }
      }
      Bridge.onSelectionChanged(startOffset, endOffset);
    } catch (e) {
      Bridge.onSelectionChanged(-1, -1);
    }
  }, 100);
});

// Click handler
document.addEventListener("click", function(e) {
  var target = e.target;

  // Check timestamp click
  if (target.classList.contains("segment-timestamp")) {
    var startTime = target.getAttribute("data-start-time");
    if (startTime) {
      Bridge.onOriginalTimestampTap(parseInt(startTime));
      return;
    }
  }

  // Check highlight tap
  var highlightEl = target.closest("[data-highlight-id]");
  if (highlightEl) {
    var highlightId = highlightEl.getAttribute("data-highlight-id");
    Bridge.onHighlightTapped(highlightId);
    return;
  }

  // Check bookmark tap
  var bookmarkEl = target.closest("[data-bookmark-id]");
  if (bookmarkEl) {
    var bookmarkId = bookmarkEl.getAttribute("data-bookmark-id");
    Bridge.onBookmarkTapped(parseFloat(bookmarkId));
    return;
  }

  // General tap zone
  var xFraction = e.clientX / window.innerWidth;
  var yFraction = e.clientY / window.innerHeight;
  Bridge.onTap(xFraction, yFraction);
});

// Scroll tracking
window.addEventListener("scroll", function() {
  if (scrollThrottleTimeout) return;
  scrollThrottleTimeout = setTimeout(function() {
    scrollThrottleTimeout = null;
    Bridge.onScrollProgress(window.scrollY, document.body.scrollHeight, window.innerHeight);
  }, 16);
});

// Resize observer for content height changes
if (typeof ResizeObserver !== "undefined") {
  new ResizeObserver(function() {
    Bridge.onContentHeightChanged(document.body.scrollHeight);
  }).observe(document.body);
}
