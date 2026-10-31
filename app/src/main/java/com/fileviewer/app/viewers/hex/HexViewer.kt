package com.fileviewer.app.viewers.hex

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import com.fileviewer.app.source.Limits
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.Family
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.SeekableSource
import com.fileviewer.app.ui.formatSize
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.MonoBody
import com.fileviewer.app.viewer.WithSeekable
import com.fileviewer.app.viewers.code.NumberDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Offset / hex / ASCII rows over random-access 64 KB pages. Works on files of any size. */
@Composable
fun HexViewer(source: FileSource) {
    WithSeekable(source) { HexBody(it) }
}

@Composable
private fun HexBody(src: SeekableSource) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 16 bytes per row when it fits, else 8: offset + hex + ASCII is about 4 chars per byte plus the offset.
        val charWidth = rememberTextMeasurer().measure("0", MonoBody).size.width
        val digits = remember(src) { maxOf(8, (src.size.toString(16).length + 1) / 2 * 2) }
        val availablePx = constraints.maxWidth - with(LocalDensity.current) { 24.dp.roundToPx() }
        val bytesPerRow = if ((4 * 16 + digits + 5) * charWidth <= availablePx) 16 else 8
        HexRows(src, bytesPerRow, digits)
    }
}

@Composable
private fun HexRows(src: SeekableSource, bytesPerRow: Int, digits: Int) {
    val paging = remember(src, bytesPerRow) { HexPaging(src.size, bytesPerRow) }
    val cache = remember(paging) { HexPageCache(src, paging) }
    var windowStart by remember(paging) { mutableLongStateOf(0L) }
    var targetOffset by remember(paging) { mutableStateOf<Long?>(null) }
    val list = remember(paging, windowStart) { LazyListState() }
    LaunchedEffect(paging, windowStart, targetOffset) {
        targetOffset?.let { list.scrollToItem(paging.indexFor(it)); targetOffset = null }
    }
    var askOffset by remember { mutableStateOf(false) }
    val firstOffset by remember(paging, list, windowStart) { derivedStateOf { paging.rowOffset(windowStart + list.firstVisibleItemIndex.toLong()) } }
    val rows = paging.windowRows(windowStart)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${HexPaging.formatOffset(firstOffset, digits)} of ${formatSize(src.size)}",
                style = MonoBody, color = Fv.palette.muted, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { askOffset = true }) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Go to offset")
            }
        }
        if (paging.rowCount > Limits.HEX_WINDOW_ROWS) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = windowStart > 0, onClick = {
                    windowStart = paging.previousWindow(windowStart); targetOffset = paging.rowOffset(windowStart)
                }) { Text("Previous") }
                Text("${HexPaging.formatOffset(paging.rowOffset(windowStart), digits)}–${HexPaging.formatOffset(paging.windowLastByte(windowStart), digits)}",
                    style = MaterialTheme.typography.bodySmall, color = Fv.palette.muted, modifier = Modifier.weight(1f))
                TextButton(enabled = paging.nextWindow(windowStart) != windowStart, onClick = {
                    windowStart = paging.nextWindow(windowStart); targetOffset = paging.rowOffset(windowStart)
                }) { Text("Next") }
            }
        }
        if (rows == 0) {
            Text("This file is empty.", style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted, modifier = Modifier.padding(16.dp))
            return@Column
        }
        Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(rows) { i -> HexRow(windowStart + i.toLong(), paging, cache, digits) }
            }
        }
    }
    if (askOffset) {
        NumberDialog(
            title = "Go to offset",
            label = "Offset (hex like 0x1F4, or decimal)",
            keyboardType = KeyboardType.Ascii,
            parse = HexPaging::parseOffset,
            onDismiss = { askOffset = false },
            onGo = { offset ->
                askOffset = false
                windowStart = paging.windowStartFor(offset)
                targetOffset = offset
            },
        )
    }
}

@Composable
private fun HexRow(row: Long, paging: HexPaging, cache: HexPageCache, digits: Int) {
    val page = paging.pageOf(row)
    val bytes by produceState(cache.cached(page), page) {
        if (value == null) value = withContext(Dispatchers.IO) { runCatching { cache.page(page) }.getOrNull() }
    }
    val p = Fv.palette
    Row {
        Text(HexPaging.formatOffset(paging.rowOffset(row), digits), style = MonoBody, color = p.muted)
        val b = bytes
        if (b != null) {
            val from = paging.indexInPage(row)
            val len = minOf(paging.rowLength(row), b.size - from).coerceAtLeast(0)
            Text(HexPaging.formatHex(b, from, len, paging.bytesPerRow), style = MonoBody, color = p.ink, modifier = Modifier.padding(start = 12.dp))
            Text(HexPaging.formatAscii(b, from, len), style = MonoBody, color = p.family(Family.Code), modifier = Modifier.padding(start = 12.dp))
        }
    }
}
