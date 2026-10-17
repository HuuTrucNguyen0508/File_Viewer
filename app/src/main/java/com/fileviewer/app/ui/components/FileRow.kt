package com.fileviewer.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fileviewer.app.ui.theme.Fv

/** One row in the browser, recents, and archive lists: tile, name, and a muted detail line. */
@Composable
fun FileRow(
    name: String,
    detail: String,
    tile: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    dimmed: Boolean = false,
) {
    val p = Fv.palette
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tile()
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dimmed) p.muted else p.ink,
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis,
            )
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
