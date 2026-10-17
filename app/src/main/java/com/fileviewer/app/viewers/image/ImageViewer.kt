package com.fileviewer.app.viewers.image

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.fileviewer.app.detect.ExtensionTable
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.detect.ImageFormat
import com.fileviewer.app.source.ContentUriSource
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.Limits
import com.fileviewer.app.source.LocalFileSource
import com.fileviewer.app.source.materialize
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.viewer.ViewerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.io.File

/**
 * Zoomable image. Local files page through the other images in their folder;
 * [onCurrentChange] reports the image on screen so the title and Info follow it.
 */
@Composable
fun ImageViewer(source: FileSource, onCurrentChange: (FileSource) -> Unit, onSwitch: (ViewerId) -> Unit) {
    val local = source as? LocalFileSource
    if (local == null) {
        SingleImage(source, onSwitch)
        return
    }
    val siblings by produceState<List<File>?>(null, local.file) {
        value = withContext(Dispatchers.IO) { siblingImages(local.file) }
    }
    val files = siblings ?: return Loading()
    val start = files.indexOf(local.file).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { files.size }
    LaunchedEffect(pager.settledPage) {
        val file = files.getOrNull(pager.settledPage)
        if (file != null && file != local.file) onCurrentChange(LocalFileSource(file))
    }
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { files[it].path }) { page ->
        ZoomableImage(files[page], files[page].name, onSwitch)
    }
}

@Composable
private fun SingleImage(source: FileSource, onSwitch: (ViewerId) -> Unit) {
    val model by produceState<Any?>(null, source) {
        value = when (source) {
            is ContentUriSource -> source.uri
            else -> withContext(Dispatchers.IO) { runCatching { source.materialize().use { it.file } }.getOrNull() ?: Uri.EMPTY }
        }
    }
    val m = model ?: return Loading()
    ZoomableImage(m, source.name, onSwitch)
}

@Composable
private fun ZoomableImage(model: Any, description: String, onSwitch: (ViewerId) -> Unit) {
    val context = LocalContext.current
    var failed by remember(model) { mutableStateOf(false) }
    if (failed) {
        Message(
            "This image couldn't be decoded",
            detail = "The format may be unsupported on this device, or the file is damaged.",
            actions = listOf("Show info" to { onSwitch(ViewerId.Info) }, "Show hex" to { onSwitch(ViewerId.Hex) }),
        )
        return
    }
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .size(Size(Limits.MAX_IMAGE_DECODE_PX, Limits.MAX_IMAGE_DECODE_PX))
            .listener(onError = { _, _ -> failed = true })
            .build()
    }
    Box(Modifier.fillMaxSize()) {
        ZoomableAsyncImage(model = request, contentDescription = description, modifier = Modifier.fillMaxSize())
    }
}

/** Images the viewer can display in [file]'s folder, sorted by name. */
private fun siblingImages(file: File): List<File> {
    val all = file.parentFile?.listFiles()?.filter { f ->
        f.isFile && !f.isHidden && (ExtensionTable.byName(f.name) as? FileKind.Image)?.format.let {
            it != null && it != ImageFormat.TIFF && it != ImageFormat.PSD
        }
    }.orEmpty()
    val sorted = all.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    return if (file in sorted) sorted else listOf(file)
}
