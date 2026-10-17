package com.fileviewer.app.browser

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fileviewer.app.AppState
import com.fileviewer.app.ui.components.FileRow
import com.fileviewer.app.ui.components.FolderTile
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.ui.components.NameTile
import com.fileviewer.app.ui.formatDate
import com.fileviewer.app.ui.formatSize
import com.fileviewer.app.ui.theme.Fv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FolderScreen(dir: File, state: AppState, onBack: () -> Unit, onFolder: (File) -> Unit, onOpen: (File) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(dir) { refresh++; onPauseOrDispose { } }
    // Outer null: still loading. Inner null: the folder can't be read.
    val listing by produceState<Result<List<File>?>?>(null, dir, state.sortBy, state.descending, state.showHidden, refresh) {
        value = withContext(Dispatchers.IO) {
            Result.success(if (dir.canRead()) FolderListing.list(dir, state.sortBy, state.descending, state.showHidden) else null)
        }
    }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
        item { FolderBar(state, onBack) }
        item { ScreenTitle(dir.name.ifEmpty { dir.path }) }
        item { Breadcrumbs(dir, onFolder) }
        item { HorizontalDivider(color = Fv.palette.rule, modifier = Modifier.padding(top = 4.dp)) }
        val loaded = listing
        val files = loaded?.getOrNull()
        when {
            loaded == null -> item { Box(Modifier.fillMaxWidth().heightIn(min = 240.dp)) { Loading() } }
            files == null -> item { Message("This folder can't be read", detail = "Check that All files access is allowed.") }
            files.isEmpty() -> item { Message("This folder is empty") }
            else -> items(files, key = { it.path }) { f ->
                if (f.isDirectory) {
                    FileRow(
                        name = f.name, detail = formatDate(f.lastModified()),
                        tile = { FolderTile() }, onClick = { onFolder(f) },
                    )
                } else {
                    FileRow(
                        name = f.name, detail = formatSize(f.length()), trailing = formatDate(f.lastModified()),
                        tile = { NameTile(f.name) }, onClick = { onOpen(f) },
                    )
                }
            }
        }
        item { Box(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) }
    }
}

@Composable
private fun FolderBar(state: AppState, onBack: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
        Box(Modifier.weight(1f))
        Box {
            TextButton(onClick = { menu = true }) {
                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(state.sortBy.label)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                SortBy.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        leadingIcon = { if (state.sortBy == s) Icon(Icons.Outlined.Check, null) },
                        onClick = { state.sortBy = s; menu = false },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Reverse order") },
                    leadingIcon = { if (state.descending) Icon(Icons.Outlined.Check, null) },
                    onClick = { state.descending = !state.descending; menu = false },
                )
                DropdownMenuItem(
                    text = { Text("Show hidden files") },
                    leadingIcon = { if (state.showHidden) Icon(Icons.Outlined.Check, null) },
                    onClick = { state.showHidden = !state.showHidden; menu = false },
                )
            }
        }
    }
}

@Composable
private fun Breadcrumbs(dir: File, onFolder: (File) -> Unit) {
    val chain = remember(dir) { generateSequence(dir) { it.parentFile }.toList().reversed().drop(1) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        chain.forEachIndexed { i, f ->
            if (i > 0) Text("/", color = Fv.palette.muted)
            TextButton(onClick = { onFolder(f) }, enabled = f != dir) { Text(f.name, maxLines = 1) }
        }
    }
}
