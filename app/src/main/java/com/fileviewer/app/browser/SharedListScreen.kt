package com.fileviewer.app.browser

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.ui.components.FileRow
import com.fileviewer.app.ui.components.NameTile
import com.fileviewer.app.ui.formatSize

/** Several files shared at once. */
@Composable
fun SharedListScreen(sources: List<FileSource>, onBack: () -> Unit, onOpen: (FileSource) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            }
        }
        item { ScreenTitle("${sources.size} shared files") }
        items(sources, key = { it.key }) { s ->
            FileRow(name = s.name, detail = formatSize(s.size), tile = { NameTile(s.name) }, onClick = { onOpen(s) })
        }
    }
}
