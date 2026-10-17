package com.fileviewer.app.viewers.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.detect.ImageFormat
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.ui.formatDateTime
import com.fileviewer.app.ui.formatSize
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.MonoBody
import com.fileviewer.app.viewer.ShareActions
import com.fileviewer.app.viewers.media.MediaProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

@Composable
fun InfoViewer(source: FileSource, kind: FileKind) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        SelectionContainer {
            Column {
                Section("File")
                Fact("Name", source.name)
                Fact("Kind", kind.label)
                Fact("Size", source.size?.let { "${formatSize(it)} (${NumberFormat.getIntegerInstance().format(it)} bytes)" } ?: "Not reported")
                Fact("MIME type", ShareActions.mimeOf(source))
                if (kind is FileKind.Text && kind.charset != null) Fact("Encoding", kind.charset.name())
                Fact("Modified", formatDateTime(source.lastModified))
                Fact("Location", source.displayPath, mono = true)
            }
        }
        HashSection(source)
        when (kind) {
            is FileKind.Image -> if (kind.format != ImageFormat.SVG) ImageSection(source)
            is FileKind.Media -> MediaSection(source)
            else -> Unit
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Fv.palette.ink, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

@Composable
private fun Fact(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted, modifier = Modifier.width(112.dp))
        Text(value, style = if (mono) MonoBody else MaterialTheme.typography.bodyMedium, color = Fv.palette.ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HashSection(source: FileSource) {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var hashed by remember { mutableLongStateOf(0L) }
    var result by rememberSaveable(source.key) { mutableStateOf<Map<String, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Section("Checksums")
    val r = result
    when {
        r != null -> SelectionContainer { Column { r.forEach { (alg, hex) -> Fact(alg, hex, mono = true) } } }
        job != null -> {
            val total = source.size
            Text("Reading ${formatSize(hashed)}" + (total?.let { " of ${formatSize(it)}" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted)
            if (total != null && total > 0) {
                LinearProgressIndicator(progress = { (hashed.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
            TextButton(onClick = { job?.cancel() }) { Text("Stop") }
        }
        else -> {
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Fv.palette.danger) }
            OutlinedButton(onClick = {
                error = null
                hashed = 0L
                job = scope.launch {
                    try {
                        result = withContext(Dispatchers.IO) { source.openStream().use { Hashes.compute(it) { n -> hashed = n } } }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = "Couldn't read the file: ${e.message}"
                    } finally {
                        job = null
                    }
                }
            }) { Text("Compute MD5, SHA-1, SHA-256") }
        }
    }
}

@Composable
private fun ImageSection(source: FileSource) {
    val facts by produceState<List<Pair<String, String>>?>(null, source) { value = withContext(Dispatchers.IO) { ImageFacts.read(source) } }
    val f = facts ?: return
    if (f.isEmpty()) return
    Section("Image")
    SelectionContainer { Column { f.forEach { (k, v) -> Fact(k, v) } } }
}

@Composable
private fun MediaSection(source: FileSource) {
    val context = LocalContext.current
    val facts by produceState<Result<MediaProbe.Facts>?>(null, source) {
        value = withContext(Dispatchers.IO) { runCatching { MediaProbe.probe(context, source) } }
    }
    val f = facts ?: return
    Section("Media")
    f.onFailure { Text("The platform couldn't read this container: ${it.message}", style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted) }
    f.onSuccess { facts ->
        SelectionContainer {
            Column {
                facts.tags.forEach { (k, v) -> Fact(k, v) }
                facts.tracks.forEachIndexed { i, t -> Fact("Track ${i + 1}", t) }
            }
        }
    }
}
