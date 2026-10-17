package com.fileviewer.app.viewers.code

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.Limits
import com.fileviewer.app.source.SeekableSource
import com.fileviewer.app.source.readFully
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.formatSize
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.MonoBody
import com.fileviewer.app.viewer.WithSeekable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/** Plain text for files too large for the editor: decoded chunk by chunk as they scroll into view. */
@Composable
fun PagedTextViewer(source: FileSource, charset: Charset?) {
    WithSeekable(source) { s -> PagedTextBody(s, charset) }
}

private class Chunks(val starts: LongArray, val charset: Charset)

@Composable
private fun PagedTextBody(src: SeekableSource, preferred: Charset?) {
    val chunks by produceState<Chunks?>(null, src) {
        value = withContext(Dispatchers.IO) {
            val sample = ByteArray(minOf(src.size, 64L * 1024).toInt()).also { src.readFully(0, it) }
            val cs = preferred ?: TextLoader.detectCharset(sample)
            Chunks(PagedText.chunkStarts(src, cs), cs)
        }
    }
    val c = chunks ?: return Loading()
    Column(Modifier.fillMaxSize()) {
        Text(
            "This file is over ${formatSize(Limits.MAX_EDITOR_TEXT_BYTES)}, so it shows as plain ${c.charset.name()} text without highlighting or find.",
            style = MaterialTheme.typography.bodySmall,
            color = Fv.palette.muted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(c.starts.size) { i ->
                    val start = c.starts[i]
                    val end = if (i + 1 < c.starts.size) c.starts[i + 1] else src.size
                    val text by produceState<String?>(null, i) {
                        value = withContext(Dispatchers.IO) {
                            val buf = ByteArray((end - start).toInt())
                            val n = src.readFully(start, buf)
                            String(buf, 0, n, c.charset).removeSuffix("\n")
                        }
                    }
                    Text(text ?: "", style = MonoBody, color = Fv.palette.ink, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
