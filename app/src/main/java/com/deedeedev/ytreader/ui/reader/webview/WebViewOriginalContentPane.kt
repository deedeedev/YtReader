package com.deedeedev.ytreader.ui.reader.webview

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.domain.SubtitleSegment
import com.deedeedev.ytreader.ui.reader.ReaderTapPosition
import com.deedeedev.ytreader.ui.reader.formatTime
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun WebViewOriginalContentPane(
    originalSegments: List<SubtitleSegment>,
    originalFallbackText: String,
    showTimestamps: Boolean,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    fontSize: Float,
    lineHeightMultiplier: Float,
    fontFamilyName: String,
    readerTextColor: Int,
    readerBackgroundColor: Int,
    onReaderTap: (ReaderTapPosition) -> Unit,
    onOriginalTimestampTap: (Long) -> Unit,
    onScrollProgress: (scrollY: Int, totalHeight: Int, viewportHeight: Int) -> Unit,
    onWebViewReady: (WebView) -> Unit,
    onWebViewDestroyed: () -> Unit
) {
    val bridge = remember { WebViewReaderBridge() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isWebViewReady by remember { mutableStateOf(false) }
    var lastSegmentsHash by remember { mutableStateOf(0) }
    var lastFallbackText by remember { mutableStateOf("") }

    val memoizedOnReaderTap by rememberUpdatedState(onReaderTap)
    val memoizedOnTimestampTap by rememberUpdatedState(onOriginalTimestampTap)
    val memoizedOnScrollProgress by rememberUpdatedState(onScrollProgress)
    val memoizedOnWebViewReady by rememberUpdatedState(onWebViewReady)

    DisposableEffect(bridge) {
        bridge.onTap = { xFraction, yFraction ->
            memoizedOnReaderTap(ReaderTapPosition(xFraction, yFraction))
        }
        bridge.onScrollProgress = { scrollY, totalHeight, viewportHeight ->
            memoizedOnScrollProgress(scrollY, totalHeight, viewportHeight)
        }
        bridge.onContentHeightChanged = { _ -> }
        bridge.onOriginalTimestampTap = { startTimeMs ->
            memoizedOnTimestampTap(startTimeMs)
        }
        bridge.onSelectionChanged = { _, _ -> }
        bridge.onHighlightTapped = { _ -> }
        bridge.onBookmarkTapped = { _ -> }

        onDispose {
            bridge.onTap = null
            bridge.onScrollProgress = null
            bridge.onContentHeightChanged = null
            bridge.onOriginalTimestampTap = null
            bridge.onSelectionChanged = null
            bridge.onHighlightTapped = null
            bridge.onBookmarkTapped = null
        }
    }

    WebViewReaderPane(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = topContentPadding,
                bottom = bottomContentPadding
            ),
        bridge = bridge,
        onViewCreated = { wv ->
            webView = wv
            isWebViewReady = true
            memoizedOnWebViewReady(wv)
        },
        onWebViewDestroyed = {
            webView = null
            isWebViewReady = false
            onWebViewDestroyed()
        }
    )

    LaunchedEffect(isWebViewReady, fontSize, lineHeightMultiplier, fontFamilyName, readerTextColor, readerBackgroundColor) {
        val wv = webView ?: return@LaunchedEffect
        if (!isWebViewReady) return@LaunchedEffect
        val textColorHex = String.format("#%06X", 0xFFFFFF and readerTextColor)
        val bgColorHex = if (readerBackgroundColor == 0 || readerBackgroundColor == -1) "#ffffff" else String.format("#%06X", 0xFFFFFF and readerBackgroundColor)
        with(WebViewReaderJs) {
            wv.setStyles(fontSize, lineHeightMultiplier, fontFamilyName, textColorHex, bgColorHex)
        }
    }

    LaunchedEffect(isWebViewReady, originalSegments, originalFallbackText, showTimestamps) {
        val wv = webView ?: return@LaunchedEffect
        if (!isWebViewReady) return@LaunchedEffect

        val currentHash = originalSegments.hashCode() + showTimestamps.hashCode()
        if (currentHash == lastSegmentsHash && originalFallbackText == lastFallbackText) return@LaunchedEffect
        lastSegmentsHash = currentHash
        lastFallbackText = originalFallbackText

        with(WebViewReaderJs) {
            if (originalSegments.isNotEmpty()) {
                val jsonArray = JSONArray()
                for (seg in originalSegments) {
                    val obj = JSONObject()
                    obj.put("text", seg.text)
                    obj.put("timestamp", if (showTimestamps) formatTime(seg.startTime) else "")
                    obj.put("startTime", seg.startTime)
                    jsonArray.put(obj)
                }
                wv.setOriginalSegments(jsonArray.toString())
            } else {
                wv.setContent(originalFallbackText)
            }
        }
    }
}
