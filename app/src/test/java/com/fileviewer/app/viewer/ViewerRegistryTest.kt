package com.fileviewer.app.viewer

import com.fileviewer.app.detect.ArchiveFormat
import com.fileviewer.app.detect.CodeLanguages
import com.fileviewer.app.detect.DocumentFormat
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.detect.ImageFormat
import com.fileviewer.app.detect.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerRegistryTest {
    private fun viewers(kind: FileKind, depth: Int = 0, sdk: Int = 34) = ViewerRegistry.viewersFor(kind, depth, sdk)

    @Test fun everyKindEndsWithHexAndInfoAndCanBeReadAsText() {
        val kinds = listOf(
            CodeLanguages.text("kotlin"), FileKind.Text(null, "Plain text"), FileKind.Pdf,
            FileKind.Image(ImageFormat.PNG), FileKind.Media(MediaFormat.MP4), FileKind.Archive(ArchiveFormat.RAR),
            FileKind.Document(DocumentFormat.DOCX), FileKind.Binary(),
        )
        for (k in kinds) {
            val v = viewers(k)
            assertTrue("$k: $v", ViewerId.Hex in v && ViewerId.Info in v)
            assertTrue("$k: $v", ViewerId.Text in v || ViewerId.Code in v)
        }
    }

    @Test fun primaryViewers() {
        assertEquals(listOf(ViewerId.Code, ViewerId.Hex, ViewerId.Info), viewers(CodeLanguages.text("rust")))
        assertEquals(listOf(ViewerId.Pdf, ViewerId.Text, ViewerId.Hex, ViewerId.Info), viewers(FileKind.Pdf))
        assertEquals(listOf(ViewerId.Image, ViewerId.Code, ViewerId.Hex, ViewerId.Info), viewers(FileKind.Image(ImageFormat.SVG)))
        assertEquals(listOf(ViewerId.Hex, ViewerId.Text, ViewerId.Info), viewers(FileKind.Binary()))
        assertEquals(listOf(ViewerId.Preview, ViewerId.Archive, ViewerId.Text, ViewerId.Hex, ViewerId.Info), viewers(FileKind.Document(DocumentFormat.EPUB)))
        assertEquals(listOf(ViewerId.Preview, ViewerId.Text, ViewerId.Hex, ViewerId.Info), viewers(FileKind.Document(DocumentFormat.RTF)))
        assertEquals(listOf(ViewerId.Preview, ViewerId.Text, ViewerId.Hex, ViewerId.Info), viewers(FileKind.Document(DocumentFormat.DOC)))
        assertEquals(ViewerId.Info, viewers(FileKind.Document(DocumentFormat.DOCX), depth = 4).first())
    }

    @Test fun gapsFallBackToInfoWithANote() {
        val rar = FileKind.Archive(ArchiveFormat.RAR)
        assertEquals(ViewerId.Info, viewers(rar).first())
        assertNotNull(ViewerRegistry.noteFor(rar))
        val avif = FileKind.Image(ImageFormat.AVIF)
        assertEquals(ViewerId.Info, viewers(avif, sdk = 30).first())
        assertEquals(ViewerId.Image, viewers(avif, sdk = 31).first())
        val deepZip = FileKind.Archive(ArchiveFormat.ZIP)
        assertEquals(ViewerId.Info, viewers(deepZip, depth = 4).first())
        assertNotNull(ViewerRegistry.noteFor(deepZip, archiveDepth = 4))
    }
}
