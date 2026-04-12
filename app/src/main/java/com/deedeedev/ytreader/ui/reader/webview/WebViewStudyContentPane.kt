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
import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.ui.reader.ReaderTapPosition
import com.deedeedev.ytreader.ui.reader.SelectionRange
import com.deedeedev.ytreader.ui.reader.TextHighlight

@Composable
internal fun WebViewStudyContentPane(
    readOnlyContent: String,
    highlights: List<TextHighlight>,
    bookmarks: List<BookmarkEntity>,
    activeStudySearchRange: SelectionRange?,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    fontSize: Float,
    lineHeightMultiplier: Float,
    fontFamilyName: String,
    readerTextColor: Int,
    readerBackgroundColor: Int,
    initialScrollPercent: Int = 0,
    isEditMode: Boolean = false,
    onReaderTap: (ReaderTapPosition) -> Unit,
    onSelectionRangeChanged: (Int, Int) -> Unit,
    onHighlightTapped: (TextHighlight?) -> Unit,
    onBookmarkTapped: (BookmarkEntity) -> Unit,
    onScrollProgress: (scrollY: Int, totalHeight: Int, viewportHeight: Int) -> Unit,
    onWebViewReady: (WebView) -> Unit,
    onWebViewDestroyed: () -> Unit,
    onEditTextChanged: (String) -> Unit = {},
    onRemoveEmptyLines: (() -> Unit)? = null,
    onTrimWhitespace: (() -> Unit)? = null,
    onNormalizeSpacing: (() -> Unit)? = null,
    onCapitalizeFirstLetter: (() -> Unit)? = null,
    onReplaceWithText: ((String, Boolean) -> Unit)? = null,
    onGetAllText: (() -> String)? = null,
    onGetSelectedText: (() -> String)? = null,
    onFindNext: ((String, Boolean, (Int) -> Unit) -> Unit)? = null,
    onFindPrevious: ((String, Boolean, (Int) -> Unit) -> Unit)? = null,
    onReplaceSingle: ((String, String, Boolean, (Int) -> Unit) -> Unit)? = null,
    onReplaceAll: ((String, String, Boolean, (Int) -> Unit) -> Unit)? = null,
    onGetMatchCount: ((String, Boolean, (Int) -> Unit) -> Unit)? = null,
    onClearFindHighlights: (() -> Unit)? = null
) {
    val bridge = remember { WebViewReaderBridge() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isWebViewReady by remember { mutableStateOf(false) }
    var lastContent by remember { mutableStateOf("") }
    var lastHighlights by remember { mutableStateOf(emptyList<TextHighlight>()) }
    var lastBookmarks by remember { mutableStateOf(emptyList<BookmarkEntity>()) }
    var lastSearchRange by remember { mutableStateOf<SelectionRange?>(null) }
    var hasAppliedInitialScroll by remember { mutableStateOf(false) }
    var webViewTotalHeight by remember { mutableStateOf(0) }

    val memoizedOnReaderTap by rememberUpdatedState(onReaderTap)
    val memoizedOnSelectionRangeChanged by rememberUpdatedState(onSelectionRangeChanged)
    val memoizedOnHighlightTapped by rememberUpdatedState(onHighlightTapped)
    val memoizedOnBookmarkTapped by rememberUpdatedState(onBookmarkTapped)
    val memoizedOnScrollProgress by rememberUpdatedState(onScrollProgress)
    val memoizedOnWebViewReady by rememberUpdatedState(onWebViewReady)
    val memoizedOnEditTextChanged by rememberUpdatedState(onEditTextChanged)

    DisposableEffect(bridge) {
        bridge.onSelectionChanged = { start, end ->
            val normalizedStart = minOf(start, end)
            val normalizedEnd = maxOf(start, end)
            if (normalizedStart >= 0 && normalizedStart < normalizedEnd) {
                memoizedOnSelectionRangeChanged(normalizedStart, normalizedEnd)
            }
        }

        bridge.onHighlightTapped = { highlightId ->
            val highlight = lastHighlights.find { "h_${it.start}_${it.end}" == highlightId }
            memoizedOnHighlightTapped(highlight)
        }

        bridge.onBookmarkTapped = { bookmarkId ->
            val bookmark = lastBookmarks.find { it.id == bookmarkId }
            if (bookmark != null) {
                memoizedOnBookmarkTapped(bookmark)
            }
        }

        bridge.onTap = { xFraction, yFraction ->
            val tapPosition = ReaderTapPosition(xFraction, yFraction)
            memoizedOnReaderTap(tapPosition)
        }

        bridge.onScrollProgress = { scrollY, totalHeight, viewportHeight ->
            webViewTotalHeight = totalHeight
            memoizedOnScrollProgress(scrollY, totalHeight, viewportHeight)
        }

        bridge.onContentHeightChanged = { _ -> }

        bridge.onContentTextChanged = { text ->
            memoizedOnEditTextChanged(text)
        }

        onDispose {
            bridge.onSelectionChanged = null
            bridge.onHighlightTapped = null
            bridge.onBookmarkTapped = null
            bridge.onTap = null
            bridge.onScrollProgress = null
            bridge.onContentHeightChanged = null
            bridge.onContentTextChanged = null
        }
    }

    WebViewReaderPane(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
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

    LaunchedEffect(isWebViewReady, readOnlyContent) {
        val wv = webView ?: return@LaunchedEffect
        if (!isWebViewReady) return@LaunchedEffect
        if (readOnlyContent == lastContent) return@LaunchedEffect
        lastContent = readOnlyContent
        with(WebViewReaderJs) {
            wv.setContent(readOnlyContent)
        }
    }

    LaunchedEffect(isWebViewReady, fontSize, lineHeightMultiplier, fontFamilyName, readerTextColor, readerBackgroundColor) {
        val wv = webView ?: return@LaunchedEffect
        if (!isWebViewReady) return@LaunchedEffect
        val textColorHex = String.format("#%06X", 0xFFFFFF and readerTextColor)
        val bgColorHex = if (readerBackgroundColor == 0 || readerBackgroundColor == -1) "#ffffff" else String.format("#%06X", 0xFFFFFF and readerBackgroundColor)
        with(WebViewReaderJs) {
            wv.setStyles(fontSize, lineHeightMultiplier, fontFamilyName, textColorHex, bgColorHex)
        }
    }

    LaunchedEffect(isWebViewReady, highlights) {
        val wv = webView ?: return@LaunchedEffect
        if (!isWebViewReady) return@LaunchedEffect
        if (highlights == lastHighlights) return@LaunchedEffect
        lastHighlights = highlights
        with(WebViewReaderJs) {
            wv.setHighlights(highlights)
        }
    }

    LaunchedEffect(isWebViewReady, bookmarks) {
        val wv = webView ?: return@LaunchedEffect
        if (!isWebViewReady) return@LaunchedEffect
        if (bookmarks == lastBookmarks) return@LaunchedEffect
        lastBookmarks = bookmarks
        with(WebViewReaderJs) {
            wv.setBookmarks(bookmarks)
        }
    }

    LaunchedEffect(isWebViewReady, activeStudySearchRange) {
        val wv = webView ?: return@LaunchedEffect
        if (!isWebViewReady) return@LaunchedEffect
        if (activeStudySearchRange == lastSearchRange) return@LaunchedEffect
        lastSearchRange = activeStudySearchRange
        with(WebViewReaderJs) {
            if (activeStudySearchRange != null) {
                wv.setSearchRange(activeStudySearchRange.start, activeStudySearchRange.end)
            } else {
                wv.clearSearchRange()
            }
        }
    }

    LaunchedEffect(isWebViewReady, webViewTotalHeight, initialScrollPercent) {
        if (!isWebViewReady || hasAppliedInitialScroll) return@LaunchedEffect
        val wv = webView ?: return@LaunchedEffect
        val totalHeight = webViewTotalHeight
        if (totalHeight <= 0) return@LaunchedEffect
        val targetScroll = if (initialScrollPercent > 0 && initialScrollPercent < 100) {
            ((totalHeight * initialScrollPercent) / 100).coerceIn(0, totalHeight - 1)
        } else 0
        if (targetScroll > 0) {
            with(WebViewReaderJs) {
                wv.scrollToOffset(targetScroll)
            }
        }
        hasAppliedInitialScroll = true
    }

    LaunchedEffect(isWebViewReady, isEditMode) {
        val wv = webView ?: return@LaunchedEffect
        if (!isWebViewReady) return@LaunchedEffect
        with(WebViewReaderJs) {
            wv.setEditMode(isEditMode)
        }
    }
}
