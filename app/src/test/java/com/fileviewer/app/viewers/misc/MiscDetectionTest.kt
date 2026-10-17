package com.fileviewer.app.viewers.misc

import com.fileviewer.app.BytesSource
import com.fileviewer.app.bytes
import com.fileviewer.app.detect.ArchiveFormat
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.detect.FileTypeDetector
import com.fileviewer.app.detect.FontFormat
import com.fileviewer.app.detect.CodeLanguages
import com.fileviewer.app.source.LocalFileSource
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewer.ViewerRegistry
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MiscDetectionTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun detect(name: String, content: ByteArray) = FileTypeDetector.detect(BytesSource(name, content))

    private fun resource(name: String) = javaClass.getResourceAsStream("/certs/$name")!!.use { it.readBytes() }

    private val latin = FontFixtures.format4(0x20..0x7E)
    private val names = FontFixtures.nameTable(*FontFixtures.englishName)
    private val cmap = FontFixtures.cmapTable(Triple(3, 1, latin))

    @Test fun fontMagicsWinOverNames() {
        assertEquals(FileKind.Font(FontFormat.TTF), detect("blob", FontFixtures.font(name = names, cmap = cmap)))
        assertEquals(FileKind.Font(FontFormat.OTF), detect("blob", FontFixtures.font("OTTO", names, cmap)))
        assertEquals(FileKind.Font(FontFormat.TTC), detect("blob", FontFixtures.collection(names to cmap)))
        assertEquals(FileKind.Font(FontFormat.WOFF), detect("blob", "wOFF".toByteArray() + bytes(0, 1, 0, 0) + ByteArray(40)))
        assertEquals(FileKind.Font(FontFormat.WOFF2), detect("blob", "wOF2".toByteArray() + bytes(0, 1, 0, 0) + ByteArray(40)))
        assertEquals(FileKind.Font(FontFormat.TTF), detect("renamed.dat", FontFixtures.font(name = names, cmap = cmap)))
    }

    @Test fun certificateExtensionsAndContent() {
        assertEquals(FileKind.Certificate, detect("site.crt", resource("chain.pem")))
        assertEquals(FileKind.Certificate, detect("site.der", resource("leaf.der")))
        assertEquals(FileKind.Certificate, detect("bundle.p7b", ByteArray(40) { (it * 7).toByte() }))
        assertEquals(FileKind.Certificate, detect("no-extension", resource("chain.pem")))
        assertEquals(FileKind.Certificate, detect("id.pem", "-----BEGIN PRIVATE KEY-----\nQUJD\n-----END PRIVATE KEY-----\n".toByteArray()))
    }

    @Test fun pemWithLeadingTextIsStillACertificateWhenNamedAsOne() {
        val text = "Bag Attributes\n    friendlyName: site\n".toByteArray() + resource("root.pem")
        assertEquals(FileKind.Certificate, detect("site.pem", text))
    }

    @Test fun unrelatedPemBlocksStayText() {
        val kind = detect("note", "-----BEGIN PGP SIGNED MESSAGE-----\nHash: SHA256\n\nhi\n".toByteArray())
        assertEquals(FileKind.Text(null, "PEM text"), (kind as FileKind.Text).copy(charset = null))
    }

    @Test fun textNamedCertButNotPemStaysText() {
        assertNotNull((detect("notes.crt", "just some words about certificates".toByteArray()) as? FileKind.Text))
    }

    @Test fun apkIsDetectedInsideAZip() {
        val zip = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry("AndroidManifest.xml")); z.write(ByteArray(8)); z.closeEntry()
                z.putNextEntry(ZipEntry("classes.dex")); z.write(ByteArray(8)); z.closeEntry()
            }
        }.toByteArray()
        val kind = FileTypeDetector.detect(LocalFileSource(tmp.newFile("app.apk").apply { writeBytes(zip) }))
        assertEquals(FileKind.Archive(ArchiveFormat.ZIP, "APK"), kind)
    }

    @Test fun viewersForTheNewKinds() {
        fun viewers(kind: FileKind, depth: Int = 0) = ViewerRegistry.viewersFor(kind, depth, 34)
        assertEquals(listOf(ViewerId.Apk, ViewerId.Archive, ViewerId.Text, ViewerId.Hex, ViewerId.Info), viewers(FileKind.Archive(ArchiveFormat.ZIP, "APK")))
        assertEquals(ViewerId.Info, viewers(FileKind.Archive(ArchiveFormat.ZIP, "APK"), depth = 4).first())
        assertEquals(ViewerId.Archive, viewers(FileKind.Archive(ArchiveFormat.ZIP, "APK bundle")).first())
        assertEquals(listOf(ViewerId.Font, ViewerId.Text, ViewerId.Hex, ViewerId.Info), viewers(FileKind.Font(FontFormat.TTF)))
        assertEquals(ViewerId.Font, viewers(FileKind.Font(FontFormat.WOFF)).first())
        assertEquals(listOf(ViewerId.Info, ViewerId.Text, ViewerId.Hex), viewers(FileKind.Font(FontFormat.WOFF2)))
        assertEquals(listOf(ViewerId.Cert, ViewerId.Text, ViewerId.Hex, ViewerId.Info), viewers(FileKind.Certificate))
    }

    @Test fun onlyWoff2NeedsANote() {
        assertNotNull(ViewerRegistry.noteFor(FileKind.Font(FontFormat.WOFF2)))
        assertNull(ViewerRegistry.noteFor(FileKind.Font(FontFormat.TTF)))
        assertNull(ViewerRegistry.noteFor(FileKind.Certificate))
        assertNull(ViewerRegistry.noteFor(CodeLanguages.text("kotlin")))
    }
}
