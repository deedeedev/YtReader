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

    private fun WebView.evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null) {
        evaluateJavascript(script) { result ->
            callback?.invoke(result)
        }
    }
}
