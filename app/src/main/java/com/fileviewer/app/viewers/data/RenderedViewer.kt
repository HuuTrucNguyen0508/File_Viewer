package com.fileviewer.app.viewers.data

import androidx.compose.runtime.Composable
import com.fileviewer.app.detect.DataFormat
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewers.data.markdown.MarkdownViewer

/** The "Rendered" view for Markdown and HTML. */
@Composable
fun RenderedViewer(source: FileSource, format: DataFormat, onSwitch: (ViewerId) -> Unit) {
    when (format) {
        DataFormat.MARKDOWN -> MarkdownViewer(source, onSwitch)
        else -> HtmlPageViewer(source, onSwitch)
    }
}
