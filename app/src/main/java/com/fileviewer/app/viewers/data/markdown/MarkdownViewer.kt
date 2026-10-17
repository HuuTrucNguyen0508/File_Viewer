package com.fileviewer.app.viewers.data.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewer.html.HtmlFragmentView
import com.fileviewer.app.viewers.data.DataNote
import com.fileviewer.app.viewers.data.SiblingFiles
import com.fileviewer.app.viewers.data.WithLoadedText
import com.fileviewer.app.viewers.data.truncationNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Markdown rendered with the shared page stylesheet. Images load from the file's own folder only. */
@Composable
fun MarkdownViewer(source: FileSource, onSwitch: (ViewerId) -> Unit) {
    WithLoadedText(source, onSwitch) { loaded ->
        val html by produceState<String?>(null, loaded) {
            value = withContext(Dispatchers.Default) { MarkdownHtml.render(loaded.text) }
        }
        val resolver = remember(source) { SiblingFiles.resolver(source) }
        Column(Modifier.fillMaxSize()) {
            DataNote("Links are shown but don't open. HTML written inside the file appears as text.", truncationNote(loaded.truncated))
            val fragment = html
            if (fragment == null) Loading()
            else HtmlFragmentView(fragment, Modifier.fillMaxWidth().weight(1f), source.name, resolver)
        }
    }
}
