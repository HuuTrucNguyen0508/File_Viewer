package com.fileviewer.app.viewers.documents

import android.webkit.WebResourceResponse
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fileviewer.app.detect.DocumentFormat
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.SeekableSource
import com.fileviewer.app.source.readFully
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewer.WithSeekable
import com.fileviewer.app.viewer.html.HtmlFragmentView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayInputStream
import kotlin.coroutines.cancellation.CancellationException

private sealed interface Loaded {
    data class Ready(val preview: DocPreview, val pkg: DocPackage?) : Loaded
    data class Failed(val message: String) : Loaded
}

/** The Preview viewer for Office, OpenDocument, EPUB, RTF and legacy Office files. */
@Composable
fun DocumentViewer(source: FileSource, format: DocumentFormat, onSwitch: (ViewerId) -> Unit) {
    if (format.zipBased) {
        WithSeekable(source) { s -> Preview(source, format, s, onSwitch) }
    } else {
        Preview(source, format, null, onSwitch)
    }
}

@Composable
private fun Preview(source: FileSource, format: DocumentFormat, seekable: SeekableSource?, onSwitch: (ViewerId) -> Unit) {
    val state by produceState<Loaded?>(null, source, seekable) {
        var pkg: DocPackage? = null
        try {
            val preview = runInterruptible(Dispatchers.IO) {
                if (seekable == null) {
                    DocumentLoader.load(format, source.openStream())
                } else {
                    val head = ByteArray(8)
                    if (seekable.readFully(0, head) == 8 && DocPackage.looksEncryptedOffice(ByteArrayInputStream(head))) {
                        throw DocumentException(DocPackage.ENCRYPTED)
                    }
                    DocumentLoader.load(format, DocPackage.open(seekable.channel()).also { pkg = it })
                }
            }
            value = Loaded.Ready(preview, pkg)
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value = Loaded.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            pkg?.close()
        }
    }
    when (val s = state) {
        null -> Loading()
        is Loaded.Failed -> Message(
            "This document couldn't be previewed",
            detail = s.message,
            actions = listOfNotNull(
                ("Show contents" to { onSwitch(ViewerId.Archive) }).takeIf { format.zipBased },
                "Show text" to { onSwitch(ViewerId.Text) },
                "Show info" to { onSwitch(ViewerId.Info) },
            ),
        )
        is Loaded.Ready -> Ready(source, s.preview, s.pkg)
    }
}

@Composable
private fun Ready(source: FileSource, preview: DocPreview, pkg: DocPackage?) {
    val resolver = remember(pkg) { pkg?.let(::imageResolver) }
    Column(Modifier.fillMaxSize()) {
        PreviewBanner(bannerText(preview), preview.notes)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (preview) {
                is PagePreview -> HtmlFragmentView(DocCss.page(preview.kind, preview.html), Modifier.fillMaxSize(), source.name, resolver)
                is SheetsPreview -> Sheets(source, preview, resolver)
                is BookPreview -> if (pkg != null) BookReader(source.key, preview, pkg, resolver)
            }
        }
    }
}

@Composable
private fun Sheets(source: FileSource, preview: SheetsPreview, resolver: ((String) -> WebResourceResponse?)?) {
    if (preview.sheets.isEmpty()) return Message("This workbook has no sheets")
    var index by rememberSaveable(source.key) { mutableIntStateOf(0) }
    val sheet = preview.sheets[index.coerceIn(0, preview.sheets.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        HtmlFragmentView(DocCss.sheet(sheet.html), Modifier.weight(1f).fillMaxWidth(), sheet.name, resolver)
        if (preview.sheets.size > 1) SheetTabs(preview.sheets.map { it.name }, index) { index = it }
    }
}

private fun bannerText(preview: DocPreview): String = when (preview) {
    is PagePreview -> when (preview.kind) {
        PageKind.Text -> "Preview of the text, tables and images. Layout and fonts differ from the original."
        PageKind.Slides -> "Preview of each slide's text and pictures, in order. Slide layouts aren't reproduced."
        PageKind.ExtractedText -> "Best-effort text found inside this older binary file. Formatting is lost and some stray words may appear."
    }
    is SheetsPreview -> "Preview of cell values. Formulas, cell formatting and charts aren't shown."
    is BookPreview -> "Preview of the book's text and images in reading order, without the book's own styling."
}

/** Serves images referenced by the preview HTML from the open package. Runs on a WebView thread. */
private fun imageResolver(pkg: DocPackage): (String) -> WebResourceResponse? = { url ->
    val path = PackagePaths.entryOf(url)
    val mime = path?.let(PackagePaths::imageMime)
    if (path == null || mime == null) null
    else pkg.image(path)?.let { WebResourceResponse(mime, null, ByteArrayInputStream(it)) }
}
