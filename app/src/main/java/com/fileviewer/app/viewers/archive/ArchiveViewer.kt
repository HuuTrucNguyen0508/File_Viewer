package com.fileviewer.app.viewers.archive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.ArchiveFormat
import com.fileviewer.app.source.ArchiveAccess
import com.fileviewer.app.source.ArchiveEntrySource
import com.fileviewer.app.source.ArchiveListing
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.Limits
import com.fileviewer.app.source.SeekableSource
import com.fileviewer.app.ui.components.FileRow
import com.fileviewer.app.ui.components.FolderTile
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.ui.components.NameTile
import com.fileviewer.app.ui.formatDate
import com.fileviewer.app.ui.formatSize
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewer.WithSeekable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Browse an archive as folders; tapping a file opens it in its own viewer. */
@Composable
fun ArchiveViewer(source: FileSource, format: ArchiveFormat, onOpen: (FileSource) -> Unit, onSwitch: (ViewerId) -> Unit) {
    if (ArchiveAccess.needsSeekable(format)) {
        WithSeekable(source) { s -> Listing(source, format, s, onOpen, onSwitch) }
    } else {
        Listing(source, format, null, onOpen, onSwitch)
    }
}

@Composable
private fun Listing(source: FileSource, format: ArchiveFormat, seekable: SeekableSource?, onOpen: (FileSource) -> Unit, onSwitch: (ViewerId) -> Unit) {
    val listing by produceState<Result<ArchiveListing>?>(null, source, seekable) {
        value = runInterruptible(Dispatchers.IO) { runCatching { ArchiveAccess.list(format, source, seekable) } }
    }
    val result = listing ?: return Loading()
    result.onFailure {
        Message(
            "This archive couldn't be read",
            detail = it.message,
            actions = listOf("Show info" to { onSwitch(ViewerId.Info) }, "Show hex" to { onSwitch(ViewerId.Hex) }),
        )
    }
    result.onSuccess { Tree(source, format, it, onOpen) }
}

@Composable
private fun Tree(source: FileSource, format: ArchiveFormat, listing: ArchiveListing, onOpen: (FileSource) -> Unit) {
    val tree = remember(listing) { ArchiveTree(listing.entries) }
    var path by rememberSaveable(source.key) { mutableStateOf("") }
    var locked by remember { mutableStateOf<String?>(null) }
    val node = tree.find(path) ?: tree.root
    BackHandler(enabled = path.isNotEmpty()) { path = path.substringBeforeLast('/', "") }

    Column(Modifier.fillMaxSize()) {
        Crumbs(source.name, path) { path = it }
        val notes = listOfNotNull(
            "This is a single compressed file.".takeIf { listing.singleStream },
            "Only the first ${Limits.MAX_ARCHIVE_ENTRIES} entries are listed.".takeIf { listing.truncated },
            locked?.let { "“$it” is password-protected, so it can't be opened." },
        )
        notes.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Fv.palette.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        val children = remember(node) { node.sortedChildren() }
        if (children.isEmpty()) {
            Message("This folder is empty")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(children, key = { it.path }) { child ->
                val entry = child.entry
                if (child.isDirectory) {
                    FileRow(
                        name = child.name,
                        detail = "${child.children.size} items",
                        tile = { FolderTile() },
                        onClick = { path = child.path },
                    )
                } else {
                    FileRow(
                        name = child.name,
                        detail = listOfNotNull(
                            formatSize(entry?.size),
                            entry?.compressedSize?.takeIf { entry.size != null && it < entry.size }?.let { "${formatSize(it)} packed" },
                        ).joinToString(", "),
                        trailing = formatDate(entry?.lastModified),
                        dimmed = entry?.encrypted == true,
                        tile = {
                            Box {
                                NameTile(child.name)
                                if (entry?.encrypted == true) {
                                    Icon(Icons.Outlined.Lock, contentDescription = "Password-protected", tint = Fv.palette.danger,
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(1.dp))
                                }
                            }
                        },
                        onClick = {
                            if (entry == null) return@FileRow
                            if (entry.encrypted) locked = child.name else onOpen(ArchiveEntrySource(source, format, entry))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Crumbs(archiveName: String, path: String, onNavigate: (String) -> Unit) {
    val parts = if (path.isEmpty()) emptyList() else path.split('/')
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onNavigate("") }) { Text(archiveName, maxLines = 1) }
        parts.forEachIndexed { i, part ->
            Text("/", color = Fv.palette.muted)
            TextButton(onClick = { onNavigate(parts.take(i + 1).joinToString("/")) }) { Text(part, maxLines = 1) }
        }
    }
}
