package com.fileviewer.app.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.detect.FileTypeDetector
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.theme.Fv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Top bar, viewer switcher, gap note, and the selected viewer. Detection runs here once per file.
 * [source] may change in place when the image viewer pages to a neighbor.
 */
@Composable
fun ViewerScreen(initial: FileSource, onBack: () -> Unit, onOpen: (FileSource) -> Unit) {
    var source by remember(initial) { mutableStateOf(initial) }
    val kind by produceState<FileKind?>(null, source) {
        value = withContext(Dispatchers.IO) { FileTypeDetector.detect(source) }
    }
    // The image pager keeps its own state; paging must not rebuild it.
    var pagerAnchor by remember(initial) { mutableStateOf(initial) }
    var selected by remember(initial) { mutableStateOf<ViewerId?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ViewerTopBar(source, kind, onBack)
        val k = kind
        if (k == null) {
            Loading()
            return@Column
        }
        val viewers = remember(k, source) { ViewerRegistry.viewersFor(k, source.archiveDepth) }
        val current = selected?.takeIf { it in viewers } ?: viewers.first()
        ViewerSwitcher(viewers, current, k.family) { selected = it }
        ViewerRegistry.noteFor(k, source.archiveDepth)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Fv.palette.muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        }
        HorizontalDivider(color = Fv.palette.rule)
        val shownSource = if (current == ViewerId.Image) pagerAnchor else source
        key(current, shownSource) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                ViewerHost(
                    id = current,
                    source = shownSource,
                    kind = k,
                    onOpen = onOpen,
                    onSwitch = { selected = it },
                    onCurrentChange = { source = it },
                )
            }
        }
        // Leaving the image view re-anchors the pager on the image the user paged to.
        SideEffect { if (current != ViewerId.Image && pagerAnchor != source) pagerAnchor = source }
    }
}
