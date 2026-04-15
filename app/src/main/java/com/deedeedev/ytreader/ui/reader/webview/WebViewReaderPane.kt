package com.deedeedev.ytreader.ui.reader.webview

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebViewReaderPane(
    modifier: Modifier = Modifier,
    bridge: WebViewReaderBridge,
    initialBackgroundColor: Int,
    statusBarHeightPx: Int,
    navBarHeightPx: Int,
    onViewCreated: (WebView) -> Unit,
    onWebViewDestroyed: () -> Unit
) {
    val rememberedOnCreated by rememberUpdatedState(onViewCreated)
    val rememberedOnDestroyed by rememberUpdatedState(onWebViewDestroyed)
    val notchTop by rememberUpdatedState(statusBarHeightPx)
    val notchBottom by rememberUpdatedState(navBarHeightPx)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                fitsSystemWindows = false
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    textZoom = 100
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                setBackgroundColor(initialBackgroundColor)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (view != null) {
                            with(WebViewReaderJs) {
                                view.setNotchHeight(notchTop, notchBottom)
                            }
                            rememberedOnCreated(view)
                        }
                    }
                }
                webChromeClient = WebChromeClient()
                addJavascriptInterface(bridge, "Bridge")
                loadUrl("file:///android_asset/reader/reader.html")
            }
        },
        update = { webView ->
        },
        onRelease = {
            rememberedOnDestroyed()
        }
    )
}
