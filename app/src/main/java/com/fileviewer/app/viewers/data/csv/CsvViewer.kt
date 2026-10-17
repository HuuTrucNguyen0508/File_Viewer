package com.fileviewer.app.viewers.data.csv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.DataFormat
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewers.data.DataBar
import com.fileviewer.app.source.Limits
import com.fileviewer.app.viewers.data.DataNote
import com.fileviewer.app.viewers.data.LoadedText
import com.fileviewer.app.viewers.data.WithLoadedText
import com.fileviewer.app.viewers.data.table.DataTable
import com.fileviewer.app.viewers.documents.SheetGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** CSV, TSV and other delimited text as a table. The delimiter is guessed and can be changed. */
@Composable
fun CsvViewer(source: FileSource, format: DataFormat, onSwitch: (ViewerId) -> Unit) {
    WithLoadedText(source, onSwitch) { loaded -> Table(source, format, loaded, onSwitch) }
}

@Composable
private fun Table(source: FileSource, format: DataFormat, loaded: LoadedText, onSwitch: (ViewerId) -> Unit) {
    val preferred = if (format == DataFormat.TSV) '\t' else source.name.substringAfterLast('.').lowercase().let { if (it == "psv") '|' else null }
    val guessed = remember(loaded) { CsvSniffer.delimiter(loaded.text, preferred) }
    var chosen by rememberSaveable(source.key) { mutableStateOf<String?>(null) }
    val delimiter = chosen?.single() ?: guessed
    val table by produceState<CsvTable?>(null, loaded, delimiter) {
        value = runInterruptible(Dispatchers.Default) { CsvParser.parse(loaded.text, delimiter) }
    }
    var headerPref by rememberSaveable(source.key) { mutableStateOf<Boolean?>(null) }

    val t = table
    if (t == null) return Loading()
    if (t.rows.isEmpty()) return Message("This file has no rows", actions = listOf("Show text" to { onSwitch(ViewerId.Text) }))

    val header = headerPref ?: remember(t) { CsvSniffer.looksLikeHeader(t.rows) }
    val columns = remember(t, header) {
        if (header) List(t.columns) { t.rows[0].getOrElse(it) { "" } } else List(t.columns) { SheetGrid.columnName(it) }
    }
    val body = if (header) t.rows.subList(1, t.rows.size) else t.rows

    Column(Modifier.fillMaxSize()) {
        val notes = buildList {
            if (loaded.truncated) add("Only the first ${Limits.MAX_DATA_TEXT_BYTES shr 20} MB of the file was read.")
            if (t.truncatedRows) add("Only the first %,d rows are shown.".format(Limits.MAX_TABLE_ROWS))
            if (t.truncatedColumns) add("Only the first ${Limits.MAX_TABLE_COLS} columns are shown.")
            if (t.truncatedCells) add("Cells longer than ${Limits.MAX_CELL_CHARS} characters are cut.")
        }
        if (notes.isNotEmpty()) DataNote(notes.first(), notes.drop(1))
        TableBar(
            summary = "%,d %s, %d %s".format(
                body.size, if (body.size == 1) "row" else "rows", t.columns, if (t.columns == 1) "column" else "columns",
            ),
            delimiter = delimiter,
            onDelimiter = { chosen = it.toString() },
            header = header,
            onHeader = { headerPref = it },
        )
        DataTable(columns, body, firstNumber = if (header) 2 else 1, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun TableBar(summary: String, delimiter: Char, onDelimiter: (Char) -> Unit, header: Boolean, onHeader: (Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    DataBar {
        Text(summary, style = MaterialTheme.typography.bodySmall, color = Fv.palette.muted, modifier = Modifier.weight(1f))
        androidx.compose.foundation.layout.Box {
            TextButton(onClick = { menu = true }) { Text("${CsvSniffer.nameOf(delimiter)} delimited") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                CsvSniffer.choices.forEach { c ->
                    DropdownMenuItem(text = { Text(CsvSniffer.nameOf(c)) }, onClick = { menu = false; onDelimiter(c) })
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("Header", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = header, onCheckedChange = onHeader)
    }
}
