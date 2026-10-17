package com.fileviewer.app.viewers.data.json

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.Family
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.MonoBody
import com.fileviewer.app.viewers.data.DataClipboard

private val Indent = 16.dp

/**
 * The visible rows of [doc], one per line. Tap a container to open or close it; hold any row for its path.
 * Indent guides run down from each open container, like the rules on drafting film.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JsonTree(doc: JsonDoc, rows: IntArray, expanded: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberJsonColors()
    val context = LocalContext.current
    var menuFor by remember(doc) { mutableStateOf<Int?>(null) }
    LazyColumn(modifier) {
        items(rows.size, key = { rows[it] }) { r ->
            val i = rows[r]
            val node = doc.nodes[i]
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .guides(node.depth, colors.guide)
                        .combinedClickable(
                            onClick = { if (node.isContainer && node.childCount > 0) onToggle(i) },
                            onLongClick = { menuFor = i },
                        )
                        .padding(start = Indent * node.depth + 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (node.isContainer && node.childCount > 0) {
                            Icon(
                                if (i in expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                                contentDescription = if (i in expanded) "Collapse" else "Expand",
                                tint = colors.muted, modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Text(line(node, colors, i in expanded), style = MonoBody, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = menuFor == i, onDismissRequest = { menuFor = null }) {
                    DropdownMenuItem(text = { Text("Copy path") }, onClick = { menuFor = null; DataClipboard.copy(context, "JSON path", doc.path(i)) })
                    if (!node.isContainer) {
                        DropdownMenuItem(text = { Text("Copy value") }, onClick = { menuFor = null; DataClipboard.copy(context, "JSON value", node.text) })
                    }
                }
            }
        }
    }
}

private class JsonColors(val ink: Color, val muted: Color, val string: Color, val number: Color, val bool: Color, val guide: Color)

@Composable
private fun rememberJsonColors(): JsonColors {
    val p = Fv.palette
    return JsonColors(
        ink = p.ink, muted = p.muted, string = p.family(Family.Image), number = p.accent,
        bool = p.family(Family.Media), guide = p.rule,
    )
}

private fun Modifier.guides(depth: Int, color: Color): Modifier = drawBehind {
    val step = Indent.toPx()
    for (d in 1..depth) {
        val x = step * d
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
    }
}

private fun line(node: JsonNode, c: JsonColors, open: Boolean): AnnotatedString = buildAnnotatedString {
    node.label?.let { label ->
        withStyle(SpanStyle(color = c.ink)) { append(label) }
        withStyle(SpanStyle(color = c.muted)) { append(": ") }
    }
    when (node.type) {
        JsonType.Object -> withStyle(SpanStyle(color = c.muted)) { append(if (open || node.childCount == 0) "{${node.childCount}}" else "{${node.childCount}} \u2026") }
        JsonType.Array -> withStyle(SpanStyle(color = c.muted)) { append(if (open || node.childCount == 0) "[${node.childCount}]" else "[${node.childCount}] \u2026") }
        JsonType.String -> withStyle(SpanStyle(color = c.string)) { append("\"${node.text}\"" + if (node.clipped) "\u2026" else "") }
        JsonType.Number -> withStyle(SpanStyle(color = c.number)) { append(node.text) }
        JsonType.Bool -> withStyle(SpanStyle(color = c.bool)) { append(node.text) }
        JsonType.Null -> withStyle(SpanStyle(color = c.muted)) { append("null") }
    }
}
