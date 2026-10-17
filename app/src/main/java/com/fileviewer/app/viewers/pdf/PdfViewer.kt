package com.fileviewer.app.viewers.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.SeekableSource
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewer.WithSeekable
import com.fileviewer.app.viewers.code.NumberDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_ZOOM = 4f

@Composable
fun PdfViewer(source: FileSource, onSwitch: (ViewerId) -> Unit) {
    WithSeekable(source, requireDescriptor = true) { PdfBody(it, onSwitch) }
}

@Composable
private fun PdfBody(src: SeekableSource, onSwitch: (ViewerId) -> Unit) {
    val doc by produceState<Result<PdfDocument>?>(null, src) {
        value = withContext(Dispatchers.IO) {
            runCatching { PdfDocument.open(src.openFileDescriptor() ?: error("No file descriptor")) }
        }
    }
    val opened = doc?.getOrNull()
    DisposableEffect(opened) { onDispose { opened?.close() } }
    when {
        doc == null -> Loading()
        opened == null -> {
            val err = doc!!.exceptionOrNull()
            Message(
                if (err is SecurityException) "This PDF is password-protected" else "This PDF couldn't be opened",
                detail = if (err is SecurityException) "Password-protected PDFs aren't supported yet." else err?.message,
                actions = listOf("Show info" to { onSwitch(ViewerId.Info) }, "Show hex" to { onSwitch(ViewerId.Hex) }),
            )
        }
        opened.pageCount == 0 -> Message("This PDF has no pages")
        else -> Pages(opened)
    }
}

@Composable
private fun Pages(doc: PdfDocument) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var gesture by remember { mutableFloatStateOf(1f) }
    var askPage by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val current by remember { derivedStateOf { list.firstVisibleItemIndex + 1 } }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Fv.palette.paper).pinchToZoom(
            onScale = { f, _ -> gesture = (gesture * f).coerceIn(1f / zoom, MAX_ZOOM / zoom) },
            onEnd = { zoom = (zoom * gesture).coerceIn(1f, MAX_ZOOM); gesture = 1f },
        ),
    ) {
        val pageWidth = maxWidth * zoom
        val widthPx = with(LocalDensity.current) { pageWidth.roundToPx() }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = gesture; scaleY = gesture; transformOrigin = TransformOrigin(0.5f, 0f)
            },
        ) {
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.width(pageWidth).fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(doc.pageCount) { i -> Page(doc, i, widthPx) }
                }
            }
        }
        Text(
            "$current / ${doc.pageCount}",
            style = MaterialTheme.typography.labelLarge,
            color = Fv.palette.paper,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .background(Fv.palette.ink.copy(alpha = 0.82f), RoundedCornerShape(50))
                .clickable { askPage = true }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
    if (askPage) {
        NumberDialog(
            title = "Go to page",
            label = "Page (1 to ${doc.pageCount})",
            keyboardType = KeyboardType.Number,
            parse = { s -> s.trim().toLongOrNull()?.takeIf { it in 1..doc.pageCount.toLong() } },
            onDismiss = { askPage = false },
            onGo = { p -> askPage = false; scope.launch { list.scrollToItem((p - 1).toInt()) } },
        )
    }
}

@Composable
private fun Page(doc: PdfDocument, index: Int, widthPx: Int) {
    // Keep showing the previous rendering (scaled) while a sharper one renders after a zoom.
    var shown by remember { mutableStateOf<Bitmap?>(null) }
    val fresh by produceState(doc.cached(index, widthPx), index, widthPx) {
        if (value == null) value = runCatching { doc.render(index, widthPx) }.getOrNull()
    }
    fresh?.let { shown = it }
    Box(Modifier.fillMaxWidth().aspectRatio(doc.aspects[index]).background(androidx.compose.ui.graphics.Color.White)) {
        shown?.let {
            Image(it.asImageBitmap(), contentDescription = "Page ${index + 1}", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
    }
}
