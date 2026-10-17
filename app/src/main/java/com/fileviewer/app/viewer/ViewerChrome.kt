package com.fileviewer.app.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.Family
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.ui.components.NameTile
import com.fileviewer.app.ui.formatSize
import com.fileviewer.app.ui.theme.Fv
import kotlinx.coroutines.launch

@Composable
fun ViewerTopBar(source: FileSource, kind: FileKind?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
        NameTile(source.name, size = 36.dp, family = kind?.family)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
            Text(source.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(
                listOfNotNull(kind?.label, source.size?.let(::formatSize)).joinToString(", "),
                style = MaterialTheme.typography.bodySmall, color = Fv.palette.muted, maxLines = 1,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) },
                    onClick = { menu = false; scope.launch { runCatching { ShareActions.share(context, ShareActions.uriFor(context, source), ShareActions.mimeOf(source)) } } },
                )
                DropdownMenuItem(
                    text = { Text("Open with another app") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                    onClick = { menu = false; scope.launch { runCatching { ShareActions.openWith(context, ShareActions.uriFor(context, source), ShareActions.mimeOf(source)) } } },
                )
                DropdownMenuItem(
                    text = { Text("Copy path") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                    onClick = { menu = false; ShareActions.copyPath(context, source) },
                )
            }
        }
    }
}

/**
 * The viewer chips. The selected chip takes the file family's hue, the same hue as the
 * file's tile, so the switcher reads as "ways of looking at this kind of file".
 */
@Composable
fun ViewerSwitcher(viewers: List<ViewerId>, selected: ViewerId, family: Family, onSelect: (ViewerId) -> Unit) {
    val p = Fv.palette
    val hue = p.family(family)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        viewers.forEach { id ->
            val on = id == selected
            val shape = RoundedCornerShape(8.dp)
            Text(
                id.label,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    on && !p.dark -> p.surface
                    on -> hue
                    else -> p.ink
                },
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (on) Modifier.background(if (p.dark) hue.copy(alpha = 0.18f) else hue, shape)
                        else Modifier.border(1.dp, p.rule, shape),
                    )
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(id) })
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
