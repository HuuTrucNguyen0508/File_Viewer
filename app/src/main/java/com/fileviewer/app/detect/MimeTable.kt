package com.fileviewer.app.detect

/** MIME hints from providers; used only when magic bytes and the name say nothing. */
object MimeTable {
    fun kindFor(mime: String?): FileKind? {
        val m = mime?.lowercase()?.substringBefore(';')?.trim() ?: return null
        exact[m]?.let { return it }
        return when {
            m.startsWith("text/") -> FileKind.Text(null, "Plain text")
            m.startsWith("image/") -> null // magic bytes decide the image format
            m.startsWith("audio/") || m.startsWith("video/") -> null
            m.endsWith("+json") -> FileKind.Data(DataFormat.JSON)
            m.endsWith("+xml") -> FileKind.Data(DataFormat.XML)
            else -> null
        }
    }

    private val exact = mapOf(
        "application/pdf" to FileKind.Pdf,
        "image/svg+xml" to FileKind.Image(ImageFormat.SVG),
        "application/json" to FileKind.Data(DataFormat.JSON),
        "application/xml" to FileKind.Data(DataFormat.XML),
        "text/xml" to FileKind.Data(DataFormat.XML),
        "text/html" to FileKind.Data(DataFormat.HTML),
        "text/markdown" to FileKind.Data(DataFormat.MARKDOWN),
        "text/csv" to FileKind.Data(DataFormat.CSV),
        "text/tab-separated-values" to FileKind.Data(DataFormat.TSV),
        "application/yaml" to FileKind.Data(DataFormat.YAML),
        "text/yaml" to FileKind.Data(DataFormat.YAML),
        "application/javascript" to CodeLanguages.text("javascript"),
        "text/javascript" to CodeLanguages.text("javascript"),
        "application/x-sh" to CodeLanguages.text("shellscript"),
        "text/x-python" to CodeLanguages.text("python"),
        "text/x-java" to CodeLanguages.text("java"),
        "text/x-kotlin" to CodeLanguages.text("kotlin"),
        "text/css" to CodeLanguages.text("css"),
        "application/zip" to FileKind.Archive(ArchiveFormat.ZIP),
        "application/vnd.android.package-archive" to FileKind.Archive(ArchiveFormat.ZIP, "APK"),
        "application/java-archive" to FileKind.Archive(ArchiveFormat.ZIP, "JAR"),
        "application/x-tar" to FileKind.Archive(ArchiveFormat.TAR),
        "application/gzip" to FileKind.Archive(ArchiveFormat.GZIP),
        "application/x-7z-compressed" to FileKind.Archive(ArchiveFormat.SEVEN_Z),
        "application/vnd.rar" to FileKind.Archive(ArchiveFormat.RAR),
        "application/x-rar-compressed" to FileKind.Archive(ArchiveFormat.RAR),
        "application/epub+zip" to FileKind.Document(DocumentFormat.EPUB),
        "application/rtf" to FileKind.Document(DocumentFormat.RTF),
        "application/vnd.sqlite3" to FileKind.Data(DataFormat.SQLITE),
        "application/x-sqlite3" to FileKind.Data(DataFormat.SQLITE),
    )
}
