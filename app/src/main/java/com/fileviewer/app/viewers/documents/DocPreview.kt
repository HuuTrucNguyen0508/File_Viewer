package com.fileviewer.app.viewers.documents

/** What a document preview shows. Parsers build these; the viewer only renders them. */
sealed interface DocPreview {
    /** Notes shown under the Preview banner (truncation, skipped parts). */
    val notes: List<String>
}

/** One HTML page: a text document, or every slide of a presentation as cards. */
data class PagePreview(val html: String, val kind: PageKind, override val notes: List<String> = emptyList()) : DocPreview

enum class PageKind {
    Text,
    Slides,

    /** Printable runs pulled out of a legacy binary file. */
    ExtractedText,
}

/** A workbook: one HTML table per sheet, chosen with tabs. */
data class SheetsPreview(val sheets: List<Sheet>, override val notes: List<String> = emptyList()) : DocPreview

data class Sheet(val name: String, val html: String, val truncated: Boolean)

/** An EPUB: chapters are read from the package on demand. */
data class BookPreview(val title: String?, val chapters: List<Chapter>, override val notes: List<String> = emptyList()) : DocPreview

/** A spine item. [path] is the chapter's entry in the package. */
data class Chapter(val title: String?, val path: String)

/** A slide or presentation page as parsed, before it becomes a card. */
data class SlideContent(val title: String?, val bodyHtml: String)

/** Truncation note shared by every parser. */
const val CUT_NOTE = "This document is long, so the preview stops partway through."
