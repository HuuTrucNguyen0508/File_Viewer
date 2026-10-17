package com.fileviewer.app.viewers.data.json

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fileviewer.app.detect.DataFormat
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewers.code.CodeViewer
import com.fileviewer.app.viewers.data.DataBar
import com.fileviewer.app.source.Limits
import com.fileviewer.app.viewers.data.DataNote
import com.fileviewer.app.viewers.data.WithLoadedText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

private sealed interface Parsed {
    data class Tree(val doc: JsonDoc) : Parsed
    data class Invalid(val error: JsonSyntaxException) : Parsed
    data object TooLarge : Parsed
}

/**
 * JSON, JSON Lines and GeoJSON as a collapsible tree. Invalid input shows where the first problem is,
 * with the highlighted source underneath so the file can still be read.
 */
@Composable
fun JsonViewer(source: FileSource, kind: FileKind.Data, onSwitch: (ViewerId) -> Unit) {
    WithLoadedText(source, onSwitch) { loaded ->
        val lines = kind.format == DataFormat.JSONL
        val parsed by produceState<Parsed?>(null, loaded) {
            value = runInterruptible(Dispatchers.Default) {
                try {
                    if (loaded.truncated) Parsed.TooLarge else Parsed.Tree(JsonParser.parse(loaded.text, lines))
                } catch (e: JsonSyntaxException) {
                    if (loaded.truncated) Parsed.TooLarge else Parsed.Invalid(e)
                }
            }
        }
        when (val p = parsed) {
            null -> Loading()
            is Parsed.Tree -> TreeScreen(p.doc, loaded.truncated)
            is Parsed.Invalid -> Column(Modifier.fillMaxSize()) {
                DataNote(
                    "This isn't valid ${if (lines) "JSON Lines" else "JSON"}: ${p.error.reason} at line ${p.error.line}, column ${p.error.column}.",
                    listOf("The source is shown below."),
                    tone = Fv.palette.danger,
                )
                Column(Modifier.weight(1f)) { CodeViewer(source, kind, highlight = true) }
            }
            Parsed.TooLarge -> Column(Modifier.fillMaxSize()) {
                DataNote(
                    "This file is larger than ${Limits.MAX_DATA_TEXT_BYTES shr 20} MB, too large to draw as a tree.",
                    listOf("The source is shown below."),
                    tone = Fv.palette.danger,
                )
                Column(Modifier.weight(1f)) { CodeViewer(source, kind, highlight = true) }
            }
        }
    }
}

@Composable
private fun TreeScreen(doc: JsonDoc, bytesCut: Boolean) {
    val small = doc.nodes.size <= 400
    var expanded by remember(doc) { mutableStateOf<Set<Int>>(if (small) doc.containersUpTo(2) else doc.containersUpTo(0)) }
    val rows = remember(doc, expanded) { doc.visible(expanded) }
    Column(Modifier.fillMaxSize()) {
        val notes = buildList {
            if (doc.truncated) add("Only the first %,d values are shown.".format(Limits.MAX_JSON_NODES))
            if (bytesCut) add("Only the first ${Limits.MAX_DATA_TEXT_BYTES shr 20} MB of the file was read.")
        }
        if (notes.isNotEmpty()) DataNote(notes.first(), notes.drop(1))
        DataBar {
            val root = doc.nodes.firstOrNull()
            Text(
                summary(doc, root),
                style = MaterialTheme.typography.bodySmall, color = Fv.palette.muted, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { expanded = doc.allContainers() }) { Text("Expand all") }
            TextButton(onClick = { expanded = emptySet() }) { Text("Collapse") }
        }
        JsonTree(
            doc, rows, expanded,
            onToggle = { i -> expanded = if (i in expanded) expanded - i else expanded + i },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

private fun summary(doc: JsonDoc, root: JsonNode?): String = when {
    root == null -> "Empty"
    doc.lines -> "%,d %s".format(root.childCount, if (root.childCount == 1) "line" else "lines")
    root.type == JsonType.Object -> "Object, ${root.childCount} ${if (root.childCount == 1) "key" else "keys"}"
    root.type == JsonType.Array -> "Array, ${root.childCount} ${if (root.childCount == 1) "item" else "items"}"
    else -> "Single value"
}
