package com.fileviewer.app.browser

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fileviewer.app.ui.components.FileRow
import com.fileviewer.app.ui.components.FolderTile
import com.fileviewer.app.ui.components.NameTile
import com.fileviewer.app.ui.formatDate
import com.fileviewer.app.ui.theme.Fv
import java.io.File

@Composable
fun HomeScreen(
    recents: RecentsStore,
    onFolder: (File) -> Unit,
    onOpenPath: (File) -> Unit,
    onOpenUri: (Uri, persisted: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StorageAccess.hasAllFilesAccess()) }
    var items by remember { mutableStateOf(recents.all()) }
    LifecycleResumeEffect(Unit) {
        granted = StorageAccess.hasAllFilesAccess()
        items = recents.all()
        onPauseOrDispose { }
    }
    val locations = remember(granted) { StorageAccess.locations(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            onOpenUri(uri, persisted)
        }
    }
    val pick = { picker.launch(arrayOf("*/*")) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
        item { ScreenTitle("File Viewer", Modifier.padding(top = 24.dp)) }
        if (!granted) item { AccessPanel(onAllow = { context.startActivity(StorageAccess.requestIntent(context)) }, onPick = pick) }
        item { SectionHeader("Storage") }
        items(locations, key = { "loc:" + it.dir.path }) { loc ->
            FileRow(
                name = loc.label,
                detail = loc.dir.path,
                tile = {
                    FolderTile(icon = when (loc.label) {
                        "Internal storage" -> Icons.Outlined.PhoneAndroid
                        "Downloads" -> Icons.Outlined.Download
                        else -> Icons.Outlined.SdCard
                    })
                },
                dimmed = !granted,
                onClick = { if (granted) onFolder(loc.dir) else context.startActivity(StorageAccess.requestIntent(context)) },
            )
        }
        item {
            FileRow(
                name = "Other apps and cloud",
                detail = "Pick a file with the system file picker",
                tile = { FolderTile(icon = Icons.Outlined.Cloud) },
                onClick = pick,
            )
        }
        if (items.isNotEmpty()) {
            item { SectionHeader("Recent", "Clear" to { items.forEach { recents.remove(it.value) }; items = emptyList() }) }
            items(items, key = { "recent:" + it.value }) { r ->
                FileRow(
                    name = r.name,
                    detail = if (r.isUri) "From another app" else File(r.value).parent.orEmpty(),
                    trailing = formatDate(r.openedAt),
                    tile = { NameTile(r.name) },
                    onClick = { if (r.isUri) onOpenUri(r.value.toUri(), true) else onOpenPath(File(r.value)) },
                )
            }
        }
        item { Column(Modifier.padding(bottom = 32.dp)) {} }
    }
}

@Composable
private fun AccessPanel(onAllow: () -> Unit, onPick: () -> Unit) {
    val p = Fv.palette
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(p.surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text("Allow access to all files", style = MaterialTheme.typography.titleMedium)
        Text(
            "Browsing folders needs the “All files access” permission. Without it you can still open files from the system picker or from other apps.",
            style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row {
            Button(onClick = onAllow) { Text("Allow access") }
            TextButton(onClick = onPick, modifier = Modifier.padding(start = 8.dp)) { Text("Pick a file") }
        }
    }
}
