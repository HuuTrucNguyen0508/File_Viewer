package com.fileviewer.app.viewer

import android.os.Build
import com.fileviewer.app.detect.ArchiveFormat
import com.fileviewer.app.detect.DataFormat
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.detect.FontFormat
import com.fileviewer.app.detect.ImageFormat
import com.fileviewer.app.detect.sourceLanguage
import com.fileviewer.app.source.Limits

/** Every viewer the switcher can offer. Labels are what the chips say. */
enum class ViewerId(val label: String) {
    Code("Code"), Text("Text"), Image("Image"), Pdf("Pages"), Media("Play"), Preview("Preview"), Archive("Contents"), Hex("Hex"), Info("Info"),
    Font("Specimen"), Apk("App"), Cert("Certificate"),
    Rendered("Rendered"), Table("Table"), Tree("Tree"), Database("Database"),
}

/**
 * Maps a [FileKind] to its ordered viewers: primary first, then Text, Hex, and Info, which every
 * file gets so a misdetected file is never trapped. Phase-2 viewers are added here by the integrator.
 */
object ViewerRegistry {

    fun viewersFor(kind: FileKind, archiveDepth: Int = 0, sdkInt: Int = Build.VERSION.SDK_INT): List<ViewerId> {
        val primary: List<ViewerId> = when (kind) {
            is FileKind.Text -> listOf(if (kind.language != null) ViewerId.Code else ViewerId.Text)
            is FileKind.Data -> when (kind.format) {
                DataFormat.MARKDOWN, DataFormat.HTML -> listOf(ViewerId.Rendered, ViewerId.Code)
                DataFormat.JSON, DataFormat.JSONL -> listOf(ViewerId.Tree, ViewerId.Code)
                DataFormat.CSV, DataFormat.TSV -> listOf(ViewerId.Table)
                DataFormat.SQLITE -> listOf(ViewerId.Database)
                DataFormat.XML, DataFormat.YAML -> listOf(ViewerId.Code)
            }
            is FileKind.Image -> when {
                kind.format == ImageFormat.SVG -> listOf(ViewerId.Image, ViewerId.Code)
                imageDecodable(kind.format, sdkInt) -> listOf(ViewerId.Image)
                else -> listOf(ViewerId.Info)
            }
            FileKind.Pdf -> listOf(ViewerId.Pdf)
            is FileKind.Media -> listOf(ViewerId.Media)
            is FileKind.Archive -> when {
                kind.format == ArchiveFormat.RAR -> listOf(ViewerId.Info)
                archiveDepth >= Limits.MAX_ARCHIVE_DEPTH -> listOf(ViewerId.Info)
                kind.subtype == "APK" -> listOf(ViewerId.Apk, ViewerId.Archive)
                else -> listOf(ViewerId.Archive)
            }
            is FileKind.Document -> when {
                !kind.format.zipBased -> listOf(ViewerId.Preview)
                archiveDepth < Limits.MAX_ARCHIVE_DEPTH -> listOf(ViewerId.Preview, ViewerId.Archive)
                else -> listOf(ViewerId.Info)
            }
            is FileKind.Font -> if (kind.format == FontFormat.WOFF2) listOf(ViewerId.Info) else listOf(ViewerId.Font)
            FileKind.Certificate -> listOf(ViewerId.Cert)
            is FileKind.Binary -> listOf(ViewerId.Hex)
        }
        val textual = kind.sourceLanguage != null || ViewerId.Text in primary
        return (primary + listOfNotNull(ViewerId.Text.takeUnless { textual }) + ViewerId.Hex + ViewerId.Info).distinct()
    }

    /** A plain statement about a known gap, shown above the viewer; null when there is none. */
    fun noteFor(kind: FileKind, archiveDepth: Int = 0, sdkInt: Int = Build.VERSION.SDK_INT): String? = when {
        kind is FileKind.Archive && kind.format == ArchiveFormat.RAR ->
            "RAR archives can't be opened yet. File info and raw bytes are below."
        (kind is FileKind.Archive || (kind is FileKind.Document && kind.format.zipBased)) && archiveDepth >= Limits.MAX_ARCHIVE_DEPTH ->
            "This archive is nested ${Limits.MAX_ARCHIVE_DEPTH} levels deep, so it opens as raw bytes only."
        kind is FileKind.Image && kind.format == ImageFormat.AVIF && sdkInt < 31 ->
            "AVIF images need Android 12 or later to display."
        kind is FileKind.Image && !imageDecodable(kind.format, sdkInt) ->
            "${kind.format.display} images can't be displayed yet."
        kind is FileKind.Font && kind.format == FontFormat.WOFF2 ->
            "WOFF2 fonts can't be previewed on Android. File info and raw bytes are below."
        else -> null
    }

    private fun imageDecodable(f: ImageFormat, sdkInt: Int) = when (f) {
        ImageFormat.TIFF, ImageFormat.PSD -> false
        ImageFormat.AVIF -> sdkInt >= 31
        else -> true
    }
}
