package com.fileviewer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fileviewer.app.ui.formatSize
import com.fileviewer.app.ui.theme.Fv

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Shown while a stream-only source is copied into the cache. */
@Composable
fun CopyProgress(copied: Long, total: Long?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Preparing file for random access", style = MaterialTheme.typography.titleMedium)
        Text(
            if (total != null && total > 0) "${formatSize(copied)} of ${formatSize(total)}" else formatSize(copied),
            style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        val fraction = if (total != null && total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else null
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth())
        } else {
            LinearProgressIndicator(Modifier.widthIn(max = 320.dp).fillMaxWidth())
        }
    }
}

/** A failure or gap: what happened, and what the user can do instead. */
@Composable
fun Message(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted, modifier = Modifier.padding(top = 6.dp).widthIn(max = 420.dp))
        }
        if (actions.isNotEmpty()) {
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { (label, action) -> TextButton(onClick = action) { Text(label) } }
            }
        }
    }
}
