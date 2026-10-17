package com.fileviewer.app.viewers.data.table

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fileviewer.app.detect.Family
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.MonoBody

private val RowHeight = 36.dp
private const val MIN_CHARS = 4
private const val MAX_CHARS = 32
private const val SAMPLE_ROWS = 200

/**
 * A grid for delimited text and database rows. The header row and the row numbers stay put;
 * everything else scrolls both ways. A tapped row opens in full, since cells are cut to one line.
 * A null cell is a database NULL and is shown as such, apart from an empty string.
 *
 * @param firstNumber the number printed for `rows[0]`
 */
@Composable
fun DataTable(
    columns: List<String>,
    rows: List<List<String?>>,
    firstNumber: Int,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val p = Fv.palette
    val density = LocalDensity.current
    val widths = remember(columns, rows, density) {
        with(density) {
            columns.indices.map { c ->
                var chars = columns[c].length
                for (r in 0 until minOf(rows.size, SAMPLE_ROWS)) chars = maxOf(chars, rows[r].getOrNull(c)?.length ?: 4)
                (chars.coerceIn(MIN_CHARS, MAX_CHARS) * 7.3f).sp.toDp() + 24.dp
            }
        }
    }
    val numberWidth = remember(rows.size, firstNumber, density) {
        with(density) { (firstNumber + rows.size).toString().length.coerceAtLeast(2).times(7.3f).sp.toDp() + 24.dp }
    }
    val hScroll = rememberScrollState()
    var open by remember(rows) { mutableStateOf<Int?>(null) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(RowHeight).background(p.surface), verticalAlignment = Alignment.CenterVertically) {
            Gutter("#", numberWidth, p.muted)
            Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                columns.forEachIndexed { i, name -> Cell(name, widths[i], bold = true, color = p.ink) }
            }
        }
        HorizontalDivider(thickness = 2.dp, color = p.family(Family.Data))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            itemsIndexed(rows) { index, row ->
                Row(
                    Modifier.fillMaxWidth().height(RowHeight).clickable { open = index },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Gutter((firstNumber + index).toString(), numberWidth, p.muted)
                    Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                        columns.indices.forEach { c ->
                            val value = row.getOrNull(c)
                            Cell(value ?: "NULL", widths[c], bold = false, color = if (value == null) p.muted else p.ink)
                        }
                    }
                }
                HorizontalDivider(color = p.rule)
            }
        }
    }
    open?.let { i ->
        RowDialog(number = firstNumber + i, columns = columns, row = rows[i], onDismiss = { open = null })
    }
}

/** Row numbers: a fixed column with a rule on its right edge, like the margin of ruled paper. */
@Composable
private fun Gutter(text: String, width: Dp, color: Color) {
    Row(Modifier.width(width).fillMaxHeight()) {
        Text(
            text, style = MonoBody, color = color, textAlign = TextAlign.End, maxLines = 1,
            modifier = Modifier.weight(1f).padding(end = 8.dp).align(Alignment.CenterVertically),
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(Fv.palette.rule))
    }
}

@Composable
private fun Cell(text: String, width: Dp, bold: Boolean, color: Color) {
    Text(
        text,
        style = MonoBody,
        fontWeight = if (bold) FontWeight.Bold else null,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 12.dp),
    )
}
