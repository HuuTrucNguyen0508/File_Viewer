package com.fileviewer.app.detect

import com.fileviewer.app.source.Limits
import com.fileviewer.app.source.SeekableSource
import org.apache.commons.compress.archivers.zip.ZipFile

/**
 * Refines a ZIP into OOXML, ODF, EPUB, APK, or JAR by looking up a bounded set of marker
 * entries in the central directory. Needs a seekable source; callers skip it for streams.
 */
object ZipInspector {

    fun refine(seekable: SeekableSource): FileKind {
        val zip = try {
            ZipFile.builder().setSeekableByteChannel(seekable.channel()).get()
        } catch (_: Exception) {
            return FileKind.Archive(ArchiveFormat.ZIP)
        }
        return zip.use { z ->
            var lookups = 0
            fun has(name: String): Boolean {
                if (lookups++ >= Limits.ZIP_INSPECT_ENTRIES) return false
                return z.getEntry(name) != null
            }
            val mimetype = z.getEntry("mimetype")?.takeIf { it.size in 1..200 }?.let { e ->
                z.getInputStream(e).use { String(it.readNBytes(200)).trim() }
            }
            when {
                mimetype == "application/epub+zip" -> FileKind.Document(DocumentFormat.EPUB)
                mimetype == "application/vnd.oasis.opendocument.text" -> FileKind.Document(DocumentFormat.ODT)
                mimetype == "application/vnd.oasis.opendocument.spreadsheet" -> FileKind.Document(DocumentFormat.ODS)
                mimetype == "application/vnd.oasis.opendocument.presentation" -> FileKind.Document(DocumentFormat.ODP)
                has("[Content_Types].xml") && has("word/document.xml") -> FileKind.Document(DocumentFormat.DOCX)
                has("[Content_Types].xml") && has("xl/workbook.xml") -> FileKind.Document(DocumentFormat.XLSX)
                has("[Content_Types].xml") && has("ppt/presentation.xml") -> FileKind.Document(DocumentFormat.PPTX)
                has("AndroidManifest.xml") && has("classes.dex") -> FileKind.Archive(ArchiveFormat.ZIP, "APK")
                has("AndroidManifest.xml") -> FileKind.Archive(ArchiveFormat.ZIP, "APK")
                has("META-INF/MANIFEST.MF") -> FileKind.Archive(ArchiveFormat.ZIP, "JAR")
                else -> FileKind.Archive(ArchiveFormat.ZIP)
            }
        }
    }
}
