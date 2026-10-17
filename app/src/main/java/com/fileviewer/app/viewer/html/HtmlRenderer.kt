package com.fileviewer.app.viewer.html

import android.content.Context
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.fileviewer.app.R
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.viewer.SafeWebView

/**
 * Shared offline HTML renderer: wraps a fragment in the bundled stylesheet (light or dark)
 * and shows it in a [SafeWebView]. Frozen contract for phase-2 viewers.
 */
object HtmlPage {
    private var css: String? = null

    fun wrap(context: Context, fragment: String, dark: Boolean, title: String = ""): String {
        val style = css ?: context.assets.open("html/base.css").bufferedReader().use { it.readText() }.also { css = it }
        val cls = if (dark) "dark" else "light"
        return "<!DOCTYPE html><html class=\"$cls\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<title>${escape(title)}</title><style>$style</style></head><body>$fragment</body></html>"
    }

    /** Serves the bundled fonts referenced by base.css; chain your own paths after it. */
    fun fontResolver(context: Context): (String) -> WebResourceResponse? = { path ->
        val res = when (path) {
            "fonts/plex.ttf" -> R.font.plex_sans
            "fonts/mono.ttf" -> R.font.jetbrains_mono
            else -> null
        }
        res?.let { WebResourceResponse("font/ttf", null, context.resources.openRawResource(it)) }
    }

    fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '<' -> append("&lt;"); '>' -> append("&gt;"); '&' -> append("&amp;"); '"' -> append("&quot;"); '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}

/** Renders [fragment] with the shared stylesheet. [resolver] answers extra paths (images inside a document). */
@Composable
fun HtmlFragmentView(
    fragment: String,
    modifier: Modifier = Modifier,
    title: String = "",
    resolver: ((String) -> WebResourceResponse?)? = null,
) {
    val dark = Fv.palette.dark
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val fonts = HtmlPage.fontResolver(ctx)
            SafeWebView.create(ctx) { path -> fonts(path) ?: resolver?.invoke(path) }
        },
        update = { web: WebView -> SafeWebView.load(web, HtmlPage.wrap(web.context, fragment, dark, title)) },
        onRelease = { it.destroy() },
    )
}
