package com.fileviewer.app.viewer

import androidx.compose.runtime.Composable
import com.fileviewer.app.detect.ArchiveFormat
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.viewers.archive.ArchiveViewer
import com.fileviewer.app.viewers.code.CodeViewer
import com.fileviewer.app.viewers.data.RenderedViewer
import com.fileviewer.app.viewers.data.csv.CsvViewer
import com.fileviewer.app.viewers.data.json.JsonViewer
import com.fileviewer.app.viewers.data.sqlite.SqliteViewer
import com.fileviewer.app.viewers.documents.DocumentViewer
import com.fileviewer.app.viewers.hex.HexViewer
import com.fileviewer.app.viewers.image.ImageViewer
import com.fileviewer.app.viewers.info.InfoViewer
import com.fileviewer.app.viewers.media.MediaViewer
import com.fileviewer.app.viewers.misc.apk.ApkViewer
import com.fileviewer.app.viewers.misc.cert.CertViewer
import com.fileviewer.app.viewers.misc.font.FontViewer
import com.fileviewer.app.viewers.pdf.PdfViewer

/** Shows [id] for [source]. The single place a [ViewerId] meets its composable. */
@Composable
fun ViewerHost(
    id: ViewerId,
    source: FileSource,
    kind: FileKind,
    onOpen: (FileSource) -> Unit,
    onSwitch: (ViewerId) -> Unit,
    onCurrentChange: (FileSource) -> Unit,
) {
    when (id) {
        ViewerId.Code -> CodeViewer(source, kind, highlight = true)
        ViewerId.Text -> CodeViewer(source, kind, highlight = false)
        ViewerId.Image -> ImageViewer(source, onCurrentChange, onSwitch)
        ViewerId.Pdf -> PdfViewer(source, onSwitch)
        ViewerId.Media -> MediaViewer(source, kind as FileKind.Media, onSwitch)
        ViewerId.Preview -> DocumentViewer(source, (kind as FileKind.Document).format, onSwitch)
        ViewerId.Archive -> ArchiveViewer(source, archiveFormat(kind), onOpen, onSwitch)
        ViewerId.Hex -> HexViewer(source)
        ViewerId.Info -> InfoViewer(source, kind)
        ViewerId.Font -> FontViewer(source, kind as FileKind.Font, onSwitch)
        ViewerId.Apk -> ApkViewer(source, onSwitch)
        ViewerId.Cert -> CertViewer(source, onSwitch)
        ViewerId.Rendered -> RenderedViewer(source, (kind as FileKind.Data).format, onSwitch)
        ViewerId.Table -> CsvViewer(source, (kind as FileKind.Data).format, onSwitch)
        ViewerId.Tree -> JsonViewer(source, kind as FileKind.Data, onSwitch)
        ViewerId.Database -> SqliteViewer(source, onSwitch)
    }
}

/** Zip-based documents (DOCX, EPUB...) browse as ZIP. */
private fun archiveFormat(kind: FileKind): ArchiveFormat = when (kind) {
    is FileKind.Archive -> kind.format
    else -> ArchiveFormat.ZIP
}
