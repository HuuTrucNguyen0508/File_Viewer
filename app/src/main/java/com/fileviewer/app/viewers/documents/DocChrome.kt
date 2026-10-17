package com.fileviewer.app.viewers.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.Family
import com.fileviewer.app.ui.theme.Fv

/**
 * The line that says this is a preview, not the original layout. A short bar in the
 * Document hue ties it to the file's tile and chip; notes (truncation) follow in muted ink.
 */
@Composable
fun PreviewBanner(text: String, notes: List<String>) {
    val p = Fv.palette
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(p.family(Family.Document)))
        Column(Modifier.padding(start = 10.dp)) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = p.ink)
            notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
    HorizontalDivider(color = p.rule)
}

/**
 * Workbook tabs along the bottom edge, where spreadsheet apps keep them. The active tab
 * lifts onto the page color with a Document-hue edge, like a tab pulled from a binder.
 */
@Composable
fun SheetTabs(names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = Fv.palette
    HorizontalDivider(color = p.rule)
    Row(
        Modifier.fillMaxWidth().background(p.paper).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        names.forEachIndexed { i, name ->
            val on = i == selected
            Column(
                Modifier
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) })
                    .background(if (on) p.surface else p.paper)
                    .heightIn(min = 48.dp)
                    .widthIn(max = 220.dp)
                    .width(IntrinsicSize.Max),
            ) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) p.family(Family.Document) else p.paper))
                Box(Modifier.padding(horizontal = 14.dp).heightIn(min = 46.dp), contentAlignment = Alignment.Center) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) p.ink else p.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
