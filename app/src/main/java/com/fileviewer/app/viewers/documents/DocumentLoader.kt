package com.fileviewer.app.viewers.documents

import com.fileviewer.app.detect.DocumentFormat
import com.fileviewer.app.source.LimitedInputStream
import com.fileviewer.app.source.Limits
import java.io.InputStream

/** Picks the parser for a document format. Blocking I/O; call off the main thread. */
object DocumentLoader {

    /** Zipped formats (OOXML, ODF, EPUB). */
    fun load(format: DocumentFormat, pkg: DocPackage): DocPreview {
        pkg.resetBudget()
        return when (format) {
            DocumentFormat.DOCX -> DocxParser.parse(pkg)
            DocumentFormat.XLSX -> XlsxParser.parse(pkg)
            DocumentFormat.PPTX -> PptxParser.parse(pkg)
            DocumentFormat.ODT, DocumentFormat.ODS, DocumentFormat.ODP -> OdfParser.parse(pkg, format)
            DocumentFormat.EPUB -> EpubParser.parse(pkg)
            else -> throw IllegalArgumentException("$format is not a zipped document")
        }
    }

    /** RTF and the legacy binary formats, read as a stream. Closes [input]. */
    fun load(format: DocumentFormat, input: InputStream): DocPreview = input.use {
        when (format) {
            DocumentFormat.RTF -> {
                val bytes = LimitedInputStream(it, Limits.MAX_DOC_PART_BYTES, "RTF document").readBytes()
                RtfParser.parse(String(bytes, Charsets.ISO_8859_1))
            }
            DocumentFormat.DOC, DocumentFormat.XLS, DocumentFormat.PPT -> LegacyText.extract(it)
            else -> throw IllegalArgumentException("$format is a zipped document")
        }
    }
}
