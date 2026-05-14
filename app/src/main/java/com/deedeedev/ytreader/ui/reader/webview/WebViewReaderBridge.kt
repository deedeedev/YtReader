package com.deedeedev.ytreader.ui.reader.webview

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface

internal class WebViewReaderBridge {
    private val mainHandler = Handler(Looper.getMainLooper())

    var onSelectionChanged: ((start: Int, end: Int) -> Unit)? = null
    var onHighlightTapped: ((highlightId: String?) -> Unit)? = null
    var onBookmarkTapped: ((bookmarkId: Long) -> Unit)? = null
    var onTap: ((xFraction: Float, yFraction: Float) -> Unit)? = null
    var onScrollProgress: ((scrollY: Int, totalHeight: Int, viewportHeight: Int) -> Unit)? = null
    var onContentHeightChanged: ((height: Int) -> Unit)? = null
    var onVisibleCharOffset: ((offset: Int) -> Unit)? = null
    var onOriginalTimestampTap: ((startTimeMs: Long) -> Unit)? = null
    var onContentTextChanged: ((text: String) -> Unit)? = null

    @JavascriptInterface
    fun onSelectionChanged(start: Int, end: Int) {
        mainHandler.post {
            onSelectionChanged?.invoke(start, end)
        }
    }

    @JavascriptInterface
    fun onHighlightTapped(highlightId: String?) {
        mainHandler.post {
            onHighlightTapped?.invoke(highlightId)
        }
    }

    @JavascriptInterface
    fun onBookmarkTapped(bookmarkId: Long) {
        mainHandler.post {
            onBookmarkTapped?.invoke(bookmarkId)
        }
    }

    @JavascriptInterface
    fun onTap(xFraction: Float, yFraction: Float) {
        mainHandler.post {
            onTap?.invoke(xFraction, yFraction)
        }
    }

    @JavascriptInterface
    fun onScrollProgress(scrollY: Int, totalHeight: Int, viewportHeight: Int) {
        mainHandler.post {
            onScrollProgress?.invoke(scrollY, totalHeight, viewportHeight)
        }
    }

    @JavascriptInterface
    fun onContentHeightChanged(height: Int) {
        mainHandler.post {
            onContentHeightChanged?.invoke(height)
        }
    }

    @JavascriptInterface
    fun onVisibleCharOffset(offset: Int) {
        mainHandler.post {
            onVisibleCharOffset?.invoke(offset)
        }
    }

    @JavascriptInterface
    fun onOriginalTimestampTap(startTimeMs: Long) {
        mainHandler.post {
            onOriginalTimestampTap?.invoke(startTimeMs)
        }
    }

    @JavascriptInterface
    fun onContentTextChanged(text: String) {
        mainHandler.post {
            onContentTextChanged?.invoke(text)
        }
    }

    @JavascriptInterface
    fun debugLog(msg: String) {
        Log.d("PosRestoreJS", msg)
    }
}
