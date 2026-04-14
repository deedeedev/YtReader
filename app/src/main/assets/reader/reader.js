"use strict";

let highlights = [];
let bookmarks = [];
let searchRange = null;
let offsetMap = [];
let selectionChangeTimeout = null;
let scrollThrottleTimeout = null;

// Edit mode state
let findSearchText = "";
let findMatchRanges = [];
let findCurrentMatchIndex = -1;

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
  container.textContent = html;
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

function scrollToPercent(percent) {
  if (percent <= 0 || percent >= 100) return;
  var totalHeight = document.body.scrollHeight;
  if (totalHeight <= 0) return;
  var targetY = Math.round((totalHeight * percent) / 100);
  window.scrollTo(0, Math.min(targetY, totalHeight - 1));
}

function scrollToCharOffset(offset) {
  buildOffsetMap();
  var entry = findOffsetEntry(offset);
  if (!entry) return;
  var range = document.createRange();
  range.setStart(entry.node, entry.localOffset);
  range.collapse(true);
  var rect = range.getBoundingClientRect();
  var targetY = window.scrollY + rect.top;
  window.scrollTo(0, Math.max(0, targetY));
}

function getCharOffsetAtTop() {
  buildOffsetMap();
  var viewportTop = window.scrollY;
  for (var i = 0; i < offsetMap.length; i++) {
    if (!offsetMap[i].node.parentElement) continue;
    var node = offsetMap[i].node;
    var nodeStart = offsetMap[i].start;
    var textLen = node.textContent.length;
    var range = document.createRange();
    range.setStart(node, 0);
    range.collapse(true);
    var rect = range.getBoundingClientRect();
    var charTop = rect.top + window.scrollY;
    if (charTop >= viewportTop) {
      return nodeStart;
    }
    var lo = 0, hi = textLen - 1;
    while (lo <= hi) {
      var mid = (lo + hi) >> 1;
      range.setStart(node, mid);
      range.collapse(true);
      rect = range.getBoundingClientRect();
      charTop = rect.top + window.scrollY;
      if (charTop < viewportTop) {
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    if (lo < textLen) {
      return nodeStart + lo;
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
    Bridge.onVisibleCharOffset(getCharOffsetAtTop());
  }, 16);
});

// Resize observer for content height changes
if (typeof ResizeObserver !== "undefined") {
  new ResizeObserver(function() {
    Bridge.onContentHeightChanged(document.body.scrollHeight);
  }).observe(document.body);
}

// ============================================
// Edit Mode Functions
// ============================================

function setEditMode(enabled) {
  var content = document.getElementById("content");
  if (enabled) {
    content.setAttribute("contenteditable", "true");
    content.classList.add("edit-mode");
    content.addEventListener("input", handleEditInput);
  } else {
    content.removeAttribute("contenteditable");
    content.classList.remove("edit-mode");
    content.removeEventListener("input", handleEditInput);
  }
}

function handleEditInput() {
  var content = document.getElementById("content");
  Bridge.onContentTextChanged(content.innerText);
}

function getAllText() {
  var content = document.getElementById("content");
  return content.innerText;
}

function getSelectedText() {
  var sel = window.getSelection();
  if (sel && sel.rangeCount > 0) {
    return sel.toString();
  }
  return "";
}

function removeEmptyLines() {
  var content = document.getElementById("content");
  var text = content.innerText;
  var lines = text.split(/\n/);
  var filtered = lines.filter(function(line) {
    return !/^\s*$/.test(line);
  });
  content.innerText = filtered.join("\n");
  buildOffsetMap();
  Bridge.onContentHeightChanged(document.body.scrollHeight);
}

function trimWhitespace() {
  var content = document.getElementById("content");
  var text = content.innerText;
  var lines = text.split(/\n/);
  var trimmed = lines.map(function(line) {
    return line.trim();
  });
  content.innerText = trimmed.join("\n");
  buildOffsetMap();
  Bridge.onContentHeightChanged(document.body.scrollHeight);
}

function normalizeSpacing() {
  var content = document.getElementById("content");
  var text = content.innerText;
  var lines = text.split(/\n/);
  var normalized = lines.map(function(line) {
    return line.replace(/\s+/g, " ").trim();
  });
  content.innerText = normalized.join("\n");
  buildOffsetMap();
  Bridge.onContentHeightChanged(document.body.scrollHeight);
}

function capitalizeFirstLetter() {
  var content = document.getElementById("content");
  var text = content.innerText;
  var lines = text.split(/\n/);
  var capitalized = lines.map(function(line) {
    return line.replace(/(^\s*|[.!?]\s+)([a-z])/g, function(match, p1, p2) {
      return p1 + p2.toUpperCase();
    });
  });
  content.innerText = capitalized.join("\n");
  buildOffsetMap();
  Bridge.onContentHeightChanged(document.body.scrollHeight);
}

function replaceWithText(text, replaceAllFlag) {
  var content = document.getElementById("content");
  var sel = window.getSelection();
  if (sel && sel.rangeCount > 0 && !sel.isCollapsed) {
    var range = sel.getRangeAt(0);
    range.deleteContents();
    range.insertNode(document.createTextNode(text));
    sel.removeAllRanges();
  } else if (replaceAllFlag) {
    content.innerText = text;
  }
  buildOffsetMap();
  Bridge.onContentHeightChanged(document.body.scrollHeight);
}

// ============================================
// Find and Replace Functions
// ============================================

function findNext(searchText, caseSensitive) {
  findSearchText = searchText;
  var content = document.getElementById("content");
  var flags = caseSensitive ? "g" : "gi";
  var regex = new RegExp(escapeRegex(searchText), flags);
  var text = content.innerText;
  findMatchRanges = [];
  
  var match;
  while ((match = regex.exec(text)) !== null) {
    findMatchRanges.push({ start: match.index, end: match.index + match[0].length });
    if (match[0].length === 0) break;
  }
  
  if (findMatchRanges.length === 0) {
    return 0;
  }
  
  findCurrentMatchIndex = 0;
  highlightFindMatch(0);
  return findMatchRanges.length;
}

function findPrevious(searchText, caseSensitive) {
  findSearchText = searchText;
  var content = document.getElementById("content");
  var flags = caseSensitive ? "g" : "gi";
  var regex = new RegExp(escapeRegex(searchText), flags);
  var text = content.innerText;
  findMatchRanges = [];
  
  var match;
  while ((match = regex.exec(text)) !== null) {
    findMatchRanges.push({ start: match.index, end: match.index + match[0].length });
    if (match[0].length === 0) break;
  }
  
  if (findMatchRanges.length === 0) {
    return 0;
  }
  
  findCurrentMatchIndex = findMatchRanges.length - 1;
  highlightFindMatch(findCurrentMatchIndex);
  return findMatchRanges.length;
}

function replaceSingle(searchText, replaceText, caseSensitive) {
  if (findCurrentMatchIndex < 0 || findCurrentMatchIndex >= findMatchRanges.length) {
    return false;
  }
  
  var content = document.getElementById("content");
  var text = content.innerText;
  var range = findMatchRanges[findCurrentMatchIndex];
  var before = text.substring(0, range.start);
  var after = text.substring(range.end);
  content.innerText = before + replaceText + after;
  
  buildOffsetMap();
  Bridge.onContentHeightChanged(document.body.scrollHeight);
  
  if (findSearchText === searchText) {
    var flags = caseSensitive ? "g" : "gi";
    var regex = new RegExp(escapeRegex(findSearchText), flags);
    var newText = content.innerText;
    findMatchRanges = [];
    var match;
    while ((match = regex.exec(newText)) !== null) {
      findMatchRanges.push({ start: match.index, end: match.index + match[0].length });
      if (match[0].length === 0) break;
    }
    findCurrentMatchIndex = Math.min(findCurrentMatchIndex, findMatchRanges.length - 1);
    if (findMatchRanges.length > 0) {
      highlightFindMatch(findCurrentMatchIndex);
    }
  }
  
  return findMatchRanges.length;
}

function replaceAll(searchText, replaceText, caseSensitive) {
  var content = document.getElementById("content");
  var flags = caseSensitive ? "g" : "gi";
  var regex = new RegExp(escapeRegex(searchText), flags);
  content.innerText = content.innerText.replace(regex, replaceText);
  
  buildOffsetMap();
  Bridge.onContentHeightChanged(document.body.scrollHeight);
  
  findMatchRanges = [];
  findCurrentMatchIndex = -1;
  
  return 0;
}

function getMatchCount(searchText, caseSensitive) {
  var content = document.getElementById("content");
  var flags = caseSensitive ? "g" : "gi";
  var regex = new RegExp(escapeRegex(searchText), flags);
  var text = content.innerText;
  var matches = text.match(regex);
  return matches ? matches.length : 0;
}

function clearFindHighlights() {
  var content = document.getElementById("content");
  var existing = content.querySelectorAll(".find-match");
  for (var i = 0; i < existing.length; i++) {
    var parent = existing[i].parentNode;
    while (existing[i].firstChild) {
      parent.insertBefore(existing[i].firstChild, existing[i]);
    }
    parent.removeChild(existing[i]);
  }
  if (content.normalize) content.normalize();
  findMatchRanges = [];
  findCurrentMatchIndex = -1;
}

function highlightFindMatch(index) {
  clearFindHighlights();
  
  if (index < 0 || index >= findMatchRanges.length) return;
  
  var content = document.getElementById("content");
  buildOffsetMap();
  
  var range = findMatchRanges[index];
  var startEntry = findOffsetEntry(range.start);
  var endEntry = findOffsetEntry(range.end);
  if (!startEntry || !endEntry) return;
  
  try {
    var domRange = document.createRange();
    domRange.setStart(startEntry.node, startEntry.localOffset);
    domRange.setEnd(endEntry.node, endEntry.localOffset);
    var span = document.createElement("span");
    span.className = "find-match";
    domRange.surroundContents(span);
    
    var rect = span.getBoundingClientRect();
  var targetY = window.scrollY + rect.top;
    window.scrollTo(0, Math.max(0, targetY));
  } catch (e) {
    // Skip
  }
  buildOffsetMap();
}

function escapeRegex(string) {
  return string.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
