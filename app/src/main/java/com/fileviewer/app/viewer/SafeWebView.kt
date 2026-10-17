package com.fileviewer.app.viewer

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * The only WebView factory: no JavaScript, no network, no file or content-provider access,
 * no navigation. Inline `data:` URLs work. Anything requested under [LOCAL_ORIGIN] is answered
 * by the caller's [resolver] (images inside an EPUB, bundled fonts); every other request gets
 * an empty response. The app also has no INTERNET permission. Frozen contract for phase 2.
 */
object SafeWebView {
    const val LOCAL_ORIGIN = "https://fileviewer.local/"

    fun create(context: Context, resolver: ((path: String) -> WebResourceResponse?)? = null): WebView =
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = false
                javaScriptCanOpenWindowsAutomatically = false
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkLoads = true
                domStorageEnabled = false
                setGeolocationEnabled(false)
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                builtInZoomControls = true
                displayZoomControls = false
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    if (url.startsWith("data:")) return null
                    if (url.startsWith(LOCAL_ORIGIN)) resolver?.invoke(url.removePrefix(LOCAL_ORIGIN))?.let { return it }
                    return WebResourceResponse("text/plain", "utf-8", 404, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
            }
        }

    /** Loads a complete HTML document with [LOCAL_ORIGIN] as its base, so relative URLs go to the resolver. */
    fun load(webView: WebView, html: String) =
        webView.loadDataWithBaseURL(LOCAL_ORIGIN, html, "text/html", "utf-8", null)
}
