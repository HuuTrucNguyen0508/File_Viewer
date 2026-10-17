package com.fileviewer.app.viewers.data

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.viewer.SafeWebView
import com.fileviewer.app.viewer.ViewerId

/**
 * An HTML file shown as its author wrote it, in the sandboxed [SafeWebView]: no scripts, no network.
 * Styles and images load only when they sit in the same folder as the page (or below it).
 */
@Composable
fun HtmlPageViewer(source: FileSource, onSwitch: (ViewerId) -> Unit) {
    WithLoadedText(source, onSwitch) { loaded ->
        val resolver = remember(source) { SiblingFiles.resolver(source) }
        Column(Modifier.fillMaxSize()) {
            DataNote(
                "Scripts are turned off and nothing loads from the internet. Pages that need scripts may look incomplete.",
                truncationNote(loaded.truncated),
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    SafeWebView.create(ctx, resolver).apply {
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                    }
                },
                update = { web ->
                    if (web.tag != loaded) {
                        web.tag = loaded
                        SafeWebView.load(web, loaded.text)
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
