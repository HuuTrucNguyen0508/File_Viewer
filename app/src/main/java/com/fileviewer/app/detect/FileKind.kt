package com.fileviewer.app.detect

import java.nio.charset.Charset

/** Broad family; drives the tile color and the default viewer. */
enum class Family { Code, Text, Image, Document, Media, Archive, Data, Font, Binary }

/** What a file is. Owned by the integrator; phase-2 owners request additions. */
sealed interface FileKind {
    val family: Family
    /** Short human label for the Info view and the viewer subtitle, e.g. "Kotlin", "PNG image". */
    val label: String

    /** Source code or plain text. [language] is a bundled grammar id, or null for plain text. */
    data class Text(val language: String?, override val label: String, val charset: Charset? = null) : FileKind {
        override val family get() = if (language != null) Family.Code else Family.Text
    }

    data class Image(val format: ImageFormat) : FileKind {
        override val family get() = Family.Image
        override val label get() = "${format.display} image"
    }

    data object Pdf : FileKind {
        override val family get() = Family.Document
        override val label get() = "PDF document"
    }

    data class Media(val format: MediaFormat) : FileKind {
        override val family get() = Family.Media
        override val label get() = format.display
    }

    /** [subtype] names a ZIP flavor shown to the user (JAR, APK). */
    data class Archive(val format: ArchiveFormat, val subtype: String? = null) : FileKind {
        override val family get() = Family.Archive
        override val label get() = subtype?.let { "$it package" } ?: "${format.display} archive"
    }

    data class Document(val format: DocumentFormat) : FileKind {
        override val family get() = Family.Document
        override val label get() = format.display
    }

    /** Structured data. Text formats carry the grammar used for their source view. */
    data class Data(val format: DataFormat) : FileKind {
        override val family get() = Family.Data
        override val label get() = format.display
    }

    data class Font(val format: FontFormat) : FileKind {
        override val family get() = Family.Font
        override val label get() = "${format.display} font"
    }

    /** Recognized or unknown binary with no dedicated viewer. */
    data class Binary(override val label: String = "Binary data") : FileKind {
        override val family get() = Family.Binary
    }
}

enum class ImageFormat(val display: String) {
    PNG("PNG"), JPEG("JPEG"), GIF("GIF"), WEBP("WebP"), HEIF("HEIF"), AVIF("AVIF"), BMP("BMP"),
    ICO("ICO"), SVG("SVG"), TIFF("TIFF"), PSD("Photoshop"),
}

enum class MediaFormat(val display: String, val video: Boolean) {
    MP4("MP4 video", true), MOV("QuickTime video", true), MKV("Matroska video", true),
    WEBM("WebM video", true), THREE_GP("3GP video", true), AVI("AVI video", true),
    MPEG_TS("MPEG transport stream", true), MPEG_PS("MPEG program stream", true), FLV("Flash video", true),
    WMV("Windows Media", true), MP3("MP3 audio", false), M4A("MPEG-4 audio", false),
    AAC("AAC audio", false), FLAC("FLAC audio", false), OGG("Ogg audio", false),
    OPUS("Opus audio", false), WAV("WAV audio", false), AMR("AMR audio", false),
}

enum class ArchiveFormat(val display: String) {
    ZIP("ZIP"), TAR("TAR"), GZIP("Gzip"), BZIP2("Bzip2"), XZ("XZ"), SEVEN_Z("7z"), RAR("RAR"), AR("ar"),
}

enum class DocumentFormat(val display: String, val zipBased: Boolean) {
    DOCX("Word document", true), XLSX("Excel spreadsheet", true), PPTX("PowerPoint presentation", true),
    ODT("OpenDocument text", true), ODS("OpenDocument spreadsheet", true), ODP("OpenDocument presentation", true),
    EPUB("EPUB book", true), RTF("Rich Text document", false),
    DOC("Legacy Word document", false), XLS("Legacy Excel spreadsheet", false), PPT("Legacy PowerPoint", false),
}

/** [language] is the grammar used for the source view, or null when the format is binary. */
enum class DataFormat(val display: String, val language: String?) {
    MARKDOWN("Markdown", "markdown"), HTML("HTML", "html"), JSON("JSON", "json"),
    CSV("CSV table", null), TSV("TSV table", null), XML("XML", "xml"), YAML("YAML", "yaml"),
    SQLITE("SQLite database", null),
}

enum class FontFormat(val display: String) { TTF("TrueType"), OTF("OpenType"), WOFF("WOFF"), WOFF2("WOFF2"), TTC("TrueType collection") }

/** True when the kind's bytes are meant to be read as text. */
val FileKind.isTextual: Boolean
    get() = when (this) {
        is FileKind.Text -> true
        is FileKind.Data -> format != DataFormat.SQLITE
        is FileKind.Image -> format == ImageFormat.SVG
        is FileKind.Document -> format == DocumentFormat.RTF
        else -> false
    }

/** Grammar for viewing this kind as source, if any. */
val FileKind.sourceLanguage: String?
    get() = when (this) {
        is FileKind.Text -> language
        is FileKind.Data -> format.language
        is FileKind.Image -> if (format == ImageFormat.SVG) "xml" else null
        else -> null
    }
