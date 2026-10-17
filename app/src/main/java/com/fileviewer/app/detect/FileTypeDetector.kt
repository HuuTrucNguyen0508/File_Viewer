package com.fileviewer.app.detect

import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.Limits
import com.fileviewer.app.source.SeekableSource
import com.fileviewer.app.source.readFully
import java.io.IOException

/**
 * Detection order: magic bytes, then file name/extension, then MIME hint, then content sniff.
 * Text kinds found by name or MIME must also sniff as text, so a binary named `.txt` is binary.
 * Blocking I/O; call off the main thread.
 */
object FileTypeDetector {

    fun detect(source: FileSource): FileKind {
        val header = ByteArray(Limits.HEADER_BYTES)
        val n = try {
            source.openStream().use { it.readFully(header) }
        } catch (_: IOException) {
            return ExtensionTable.byName(source.name) ?: MimeTable.kindFor(source.mimeHint) ?: FileKind.Binary()
        }
        val truncated = n == header.size && (source.size?.let { it > n } ?: true)
        return detect(source.name, source.mimeHint, header, n, truncated, source::seekableOrNull)
    }

    fun detect(
        name: String,
        mime: String?,
        header: ByteArray,
        length: Int,
        truncated: Boolean,
        seekable: () -> SeekableSource? = { null },
    ): FileKind {
        val byName = ExtensionTable.byName(name)
        Magic.detect(header, length)?.let { return refineMagic(it, byName, seekable) }

        val sniff = TextSniffer.sniff(header, length, truncated)
        if (byName != null) {
            if (byName.isTextual) return if (sniff.isText) withCharset(byName, sniff) else FileKind.Binary()
            // A binary-type name with no matching signature but clearly textual content is text.
            if (!(sniff.isText && length > 0)) return byName
        }
        MimeTable.kindFor(mime)?.let { k ->
            if (!k.isTextual) return k
            if (sniff.isText) return withCharset(contentTextKind(header, length, sniff, k), sniff)
        }
        if (sniff.isText) return withCharset(contentTextKind(header, length, sniff, null), sniff)
        return FileKind.Binary()
    }

    private fun refineMagic(magic: FileKind, byName: FileKind?, seekable: () -> SeekableSource?): FileKind = when {
        magic is FileKind.Archive && magic.format == ArchiveFormat.ZIP -> {
            val inspected = runCatching { seekable()?.use { ZipInspector.refine(it) } }.getOrNull()
            when {
                inspected != null && inspected != magic -> inspected
                byName.isZipBased() -> byName!!
                else -> inspected ?: magic
            }
        }
        magic is FileKind.Binary && magic.label == Magic.OLE2_LABEL ->
            byName?.takeIf { it is FileKind.Document || it is FileKind.Binary } ?: magic
        magic == FileKind.Media(MediaFormat.MP4) && byName == FileKind.Media(MediaFormat.M4A) -> byName
        else -> magic
    }

    private fun FileKind?.isZipBased(): Boolean = when (this) {
        is FileKind.Archive -> format == ArchiveFormat.ZIP
        is FileKind.Document -> format.zipBased
        else -> false
    }

    private fun withCharset(kind: FileKind, sniff: TextSniff): FileKind =
        if (kind is FileKind.Text) kind.copy(charset = sniff.charset) else kind

    /** Recognizes text with no useful name: shebangs, XML, SVG, HTML, JSON, diffs, PEM. */
    private fun contentTextKind(header: ByteArray, length: Int, sniff: TextSniff, fallback: FileKind?): FileKind {
        val text = String(header, sniff.bomLength, length - sniff.bomLength, sniff.charset).trimStart()
        val lower = text.take(512).lowercase()
        return when {
            text.startsWith("#!") -> shebang(text.lineSequence().first()) ?: FileKind.Text(null, "Script")
            lower.startsWith("<?xml") -> if ("<svg" in lower) FileKind.Image(ImageFormat.SVG) else FileKind.Data(DataFormat.XML)
            lower.startsWith("<svg") -> FileKind.Image(ImageFormat.SVG)
            lower.startsWith("<!doctype html") || lower.startsWith("<html") -> FileKind.Data(DataFormat.HTML)
            text.startsWith("{\"") || text.startsWith("[{") || text.startsWith("{\n") || text.startsWith("[\n") ->
                FileKind.Data(DataFormat.JSON)
            text.startsWith("diff --git") || text.startsWith("--- a/") || text.startsWith("Index: ") ->
                CodeLanguages.text("diff")
            text.startsWith("-----BEGIN ") -> FileKind.Text(null, "PEM text")
            else -> fallback ?: FileKind.Text(null, "Plain text")
        }
    }

    private fun shebang(line: String): FileKind? {
        val parts = line.removePrefix("#!").trim().split(Regex("\\s+"))
        val program = parts.firstOrNull()?.substringAfterLast('/') ?: return null
        val interpreter = if (program == "env") parts.drop(1).firstOrNull { !it.startsWith("-") } else program
        val id = when {
            interpreter == null -> null
            interpreter.startsWith("python") -> "python"
            interpreter in setOf("sh", "bash", "zsh", "ksh", "dash", "ash", "fish") -> "shellscript"
            interpreter in setOf("node", "nodejs", "deno", "bun") -> "javascript"
            interpreter.startsWith("ruby") -> "ruby"
            interpreter.startsWith("perl") -> "perl"
            interpreter.startsWith("php") -> "php"
            interpreter.startsWith("lua") -> "lua"
            interpreter == "Rscript" -> "r"
            interpreter == "julia" -> "julia"
            interpreter == "pwsh" -> "powershell"
            interpreter == "groovy" -> "groovy"
            interpreter == "kotlin" || interpreter == "kscript" -> "kotlin"
            interpreter == "make" -> "makefile"
            else -> null
        }
        return id?.let { CodeLanguages.text(it) }
    }
}
