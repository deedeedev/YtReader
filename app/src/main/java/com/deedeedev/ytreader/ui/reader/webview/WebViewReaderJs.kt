package com.deedeedev.ytreader.ui.reader.webview

import android.webkit.WebView
import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.ui.reader.TextHighlight
import org.json.JSONArray
import org.json.JSONObject

internal object WebViewReaderJs {

    private fun String.escapeForJs(): String {
        return this
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\u0000", "\\0")
    }

    fun WebView.setContent(content: String) {
        val escaped = content.escapeForJs()
        evaluateJavascript("setContent('$escaped')", null)
    }

    fun WebView.setStyles(
        fontSize: Float,
        lineHeight: Float,
        fontFamily: String,
        textColor: String,
        bgColor: String
    ) {
        val jsFontFamily = when (fontFamily) {
            "Serif" -> "serif"
            "SansSerif" -> "sans-serif"
            "Monospace" -> "monospace"
            "Cursive" -> "cursive"
            else -> "sans-serif"
        }
        evaluateJavascript(
            "setStyles($fontSize, $lineHeight, '$jsFontFamily', '$textColor', '$bgColor')",
            null
        )
    }

    fun WebView.setHighlights(highlights: List<TextHighlight>) {
        val jsonArray = JSONArray()
        for (h in highlights) {
            val obj = JSONObject()
            obj.put("start", h.start)
            obj.put("end", h.end)
            obj.put("color", h.color.name.lowercase())
            obj.put("id", "h_${h.start}_${h.end}")
            obj.put("hasNote", h.note != null)
            jsonArray.put(obj)
        }
        val escaped = jsonArray.toString().escapeForJs()
        evaluateJavascript("setHighlights('$escaped')", null)
    }

    fun WebView.setBookmarks(bookmarks: List<BookmarkEntity>) {
        val jsonArray = JSONArray()
        for (bm in bookmarks) {
            val obj = JSONObject()
            obj.put("id", bm.id)
            obj.put("anchorStart", bm.anchorStart)
            obj.put("title", bm.title)
            jsonArray.put(obj)
        }
        val escaped = jsonArray.toString().escapeForJs()
        evaluateJavascript("setBookmarks('$escaped')", null)
    }

    fun WebView.setSearchRange(start: Int, end: Int) {
        evaluateJavascript("setSearchRange($start, $end)", null)
    }

    fun WebView.clearSearchRange() {
        evaluateJavascript("clearSearchRange()", null)
    }

    fun WebView.clearSelection() {
        evaluateJavascript("clearSelection()", null)
    }

    fun WebView.setSelectionRange(start: Int, end: Int) {
        evaluateJavascript("setSelectionRange($start, $end)", null)
    }

    fun WebView.getScrollOffset(callback: (Int) -> Unit) {
        evaluateJavascript("getScrollOffset()") { result ->
            val offset = result?.toIntOrNull() ?: 0
            callback(offset)
        }
    }

    fun WebView.getTotalHeight(callback: (Int) -> Unit) {
        evaluateJavascript("getTotalHeight()") { result ->
            val height = result?.toIntOrNull() ?: 0
            callback(height)
        }
    }

    fun WebView.scrollToOffset(y: Int) {
        evaluateJavascript("scrollToOffset($y)", null)
    }

    fun WebView.scrollToPercent(percent: Int) {
        evaluateJavascript("scrollToPercent($percent)", null)
    }

    fun WebView.scrollToCharOffset(offset: Int) {
        evaluateJavascript("scrollToCharOffset($offset)", null)
    }

    fun WebView.getCharOffsetAtTop(callback: (Int) -> Unit) {
        evaluateJavascript("getCharOffsetAtTop()") { result ->
            val offset = result?.toIntOrNull() ?: 0
            callback(offset)
        }
    }

    fun WebView.setOriginalSegments(segmentsJson: String) {
        val escaped = segmentsJson.escapeForJs()
        evaluateJavascript("setOriginalSegments('$escaped')", null)
    }

    fun WebView.setNotchHeight(topPx: Int, bottomPx: Int) {
        evaluateJavascript("setNotchHeight($topPx, $bottomPx)", null)
    }

    fun WebView.setEditMode(enabled: Boolean) {
        evaluateJavascript("setEditMode($enabled)", null)
    }

    fun WebView.getAllText(callback: (String) -> Unit) {
        evaluateJavascript("getAllText()") { result ->
            callback(result ?: "")
        }
    }

    fun WebView.getSelectedText(callback: (String) -> Unit) {
        evaluateJavascript("getSelectedText()") { result ->
            callback(result ?: "")
        }
    }

    fun WebView.removeEmptyLines() {
        evaluateJavascript("removeEmptyLines()", null)
    }

    fun WebView.trimWhitespace() {
        evaluateJavascript("trimWhitespace()", null)
    }

    fun WebView.normalizeSpacing() {
        evaluateJavascript("normalizeSpacing()", null)
    }

    fun WebView.capitalizeFirstLetter() {
        evaluateJavascript("capitalizeFirstLetter()", null)
    }

    fun WebView.replaceWithText(text: String, replaceAll: Boolean) {
        val escaped = text.escapeForJs()
        evaluateJavascript("replaceWithText('$escaped', $replaceAll)", null)
    }

    fun WebView.findNext(searchText: String, caseSensitive: Boolean, callback: (Int) -> Unit) {
        val escaped = searchText.escapeForJs()
        evaluateJavascript("findNext('$escaped', $caseSensitive)") { result ->
            val count = result?.toIntOrNull() ?: 0
            callback(count)
        }
    }

    fun WebView.findPrevious(searchText: String, caseSensitive: Boolean, callback: (Int) -> Unit) {
        val escaped = searchText.escapeForJs()
        evaluateJavascript("findPrevious('$escaped', $caseSensitive)") { result ->
            val count = result?.toIntOrNull() ?: 0
            callback(count)
        }
    }

    fun WebView.replaceSingle(searchText: String, replaceText: String, caseSensitive: Boolean, callback: (Int) -> Unit) {
        val escapedSearch = searchText.escapeForJs()
        val escapedReplace = replaceText.escapeForJs()
        evaluateJavascript("replaceSingle('$escapedSearch', '$escapedReplace', $caseSensitive)") { result ->
            val count = result?.toIntOrNull() ?: 0
            callback(count)
        }
    }

    fun WebView.replaceAll(searchText: String, replaceText: String, caseSensitive: Boolean, callback: (Int) -> Unit) {
        val escapedSearch = searchText.escapeForJs()
        val escapedReplace = replaceText.escapeForJs()
        evaluateJavascript("replaceAll('$escapedSearch', '$escapedReplace', $caseSensitive)") { result ->
            val count = result?.toIntOrNull() ?: 0
            callback(count)
        }
    }

    fun WebView.getMatchCount(searchText: String, caseSensitive: Boolean, callback: (Int) -> Unit) {
        val escaped = searchText.escapeForJs()
        evaluateJavascript("getMatchCount('$escaped', $caseSensitive)") { result ->
            val count = result?.toIntOrNull() ?: 0
            callback(count)
        }
    }

    fun WebView.clearFindHighlights() {
        evaluateJavascript("clearFindHighlights()", null)
    }

    private fun WebView.evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null) {
        evaluateJavascript(script) { result ->
            callback?.invoke(result)
        }
    }
}
