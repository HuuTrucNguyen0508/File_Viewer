package com.fileviewer.app.detect

import com.fileviewer.app.BytesSource
import com.fileviewer.app.bytes
import com.fileviewer.app.source.LocalFileSource
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileTypeDetectorTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun detect(name: String, content: ByteArray, mime: String? = null) =
        FileTypeDetector.detect(BytesSource(name, content, mime))

    private fun detectFile(name: String, content: ByteArray): FileKind =
        FileTypeDetector.detect(LocalFileSource(tmp.newFile(name).apply { writeBytes(content) }))

    private val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13) + "IHDR".toByteArray()
    private val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10) + "JFIF".toByteArray()

    private fun ftyp(major: String, vararg compatible: String): ByteArray {
        val body = major.toByteArray() + bytes(0, 0, 0, 0) + compatible.joinToString("").toByteArray()
        val size = 8 + body.size
        return bytes(0, 0, 0, size) + "ftyp".toByteArray() + body + ByteArray(32)
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { bos ->
        ZipOutputStream(bos).use { z -> entries.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
    }.toByteArray()

    @Test fun images() {
        assertEquals(FileKind.Image(ImageFormat.PNG), detect("a.png", png))
        assertEquals(FileKind.Image(ImageFormat.JPEG), detect("a.jpg", jpeg))
        assertEquals(FileKind.Image(ImageFormat.GIF), detect("a.gif", "GIF89a\u0001\u0000".toByteArray()))
        assertEquals(FileKind.Image(ImageFormat.WEBP), detect("a.webp", "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
        assertEquals(FileKind.Image(ImageFormat.HEIF), detect("a.heic", ftyp("heic", "mif1", "heic")))
        assertEquals(FileKind.Image(ImageFormat.AVIF), detect("a.avif", ftyp("avif", "mif1", "avif")))
        assertEquals(FileKind.Image(ImageFormat.AVIF), detect("x", ftyp("mif1", "mif1", "avif")))
    }

    @Test fun media() {
        assertEquals(FileKind.Media(MediaFormat.MP4), detect("v.mp4", ftyp("isom", "isom", "mp41")))
        assertEquals(FileKind.Media(MediaFormat.MOV), detect("v.mov", ftyp("qt  ", "qt  ")))
        assertEquals(FileKind.Media(MediaFormat.M4A), detect("song.m4a", ftyp("isom", "isom")))
        assertEquals(FileKind.Media(MediaFormat.MP3), detect("s.mp3", "ID3\u0004\u0000".toByteArray() + ByteArray(20)))
        assertEquals(FileKind.Media(MediaFormat.MP3), detect("frames", bytes(0xFF, 0xFB, 0x90, 0x64) + ByteArray(100)))
        assertEquals(FileKind.Media(MediaFormat.FLAC), detect("s.flac", "fLaC".toByteArray() + ByteArray(30)))
        assertEquals(FileKind.Media(MediaFormat.WEBM), detect("v", bytes(0x1A, 0x45, 0xDF, 0xA3) + "B\u0082\u0084webm".toByteArray()))
        assertEquals(FileKind.Media(MediaFormat.OPUS), detect("a.ogg", "OggS".toByteArray() + ByteArray(24) + "OpusHead".toByteArray()))
        val ts = ByteArray(564).also { it[0] = 0x47; it[188] = 0x47; it[376] = 0x47 }
        assertEquals(FileKind.Media(MediaFormat.MPEG_TS), detect("clip.ts", ts))
    }

    @Test fun pdfAndContainers() {
        assertEquals(FileKind.Pdf, detect("doc.pdf", "%PDF-1.7\n%âãÏÓ\n".toByteArray()))
        assertEquals(FileKind.Archive(ArchiveFormat.GZIP), detect("a.gz", bytes(0x1F, 0x8B, 8, 0) + ByteArray(20)))
        assertEquals(FileKind.Archive(ArchiveFormat.SEVEN_Z), detect("a.7z", bytes(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C, 0, 4)))
        assertEquals(FileKind.Archive(ArchiveFormat.RAR), detect("a.rar", "Rar!\u001A\u0007\u0001\u0000".toByteArray()))
        assertEquals(FileKind.Archive(ArchiveFormat.XZ), detect("a.xz", bytes(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0)))
        assertEquals(FileKind.Data(DataFormat.SQLITE), detect("app.db", "SQLite format 3\u0000".toByteArray() + ByteArray(84)))
        assertEquals(FileKind.Binary("ELF executable"), detect("ls", bytes(0x7F, 0x45, 0x4C, 0x46, 2, 1, 1)))
        assertEquals(FileKind.Binary("Java class file"), detect("A.class", bytes(0xCA, 0xFE, 0xBA, 0xBE, 0, 0, 0, 61)))
        assertEquals(FileKind.Font(FontFormat.OTF), detect("f.otf", "OTTO".toByteArray() + bytes(0, 10, 0, 0)))
    }

    @Test fun tarDetectedByUstarAtOffset257() {
        val tar = ByteArrayOutputStream().also { bos ->
            TarArchiveOutputStream(bos).use { t ->
                val data = "hello".toByteArray()
                t.putArchiveEntry(TarArchiveEntry("hello.txt").apply { size = data.size.toLong() })
                t.write(data); t.closeArchiveEntry()
            }
        }.toByteArray()
        assertEquals(FileKind.Archive(ArchiveFormat.TAR), detect("noext", tar))
    }

    @Test fun zipSubtypesFromCentralDirectoryWhenSeekable() {
        val docx = zipOf("[Content_Types].xml" to "<Types/>", "word/document.xml" to "<w:document/>")
        assertEquals(FileKind.Document(DocumentFormat.DOCX), detectFile("report.zip", docx))
        val epub = zipOf("mimetype" to "application/epub+zip", "OEBPS/content.opf" to "<package/>")
        assertEquals(FileKind.Document(DocumentFormat.EPUB), detectFile("book", epub))
        val apk = zipOf("AndroidManifest.xml" to "x", "classes.dex" to "dex")
        assertEquals(FileKind.Archive(ArchiveFormat.ZIP, "APK"), detectFile("app.bin", apk))
        assertEquals(FileKind.Archive(ArchiveFormat.ZIP), detectFile("plain.zip", zipOf("a.txt" to "a")))
    }

    @Test fun zipSubtypeFallsBackToExtensionForStreams() {
        val docx = zipOf("[Content_Types].xml" to "<Types/>", "word/document.xml" to "<w:document/>")
        assertEquals(FileKind.Document(DocumentFormat.DOCX), detect("report.docx", docx))
        assertEquals(FileKind.Archive(ArchiveFormat.ZIP), detect("report", docx))
    }

    @Test fun codeByExtensionAndName() {
        val kt = "fun main() = println(\"hi\")\n".toByteArray()
        assertEquals("kotlin", (detect("Main.kt", kt) as FileKind.Text).language)
        assertEquals("makefile", (detect("Makefile", "all:\n\techo hi\n".toByteArray()) as FileKind.Text).language)
        assertEquals("dockerfile", (detect("Dockerfile", "FROM alpine\n".toByteArray()) as FileKind.Text).language)
        assertEquals("shellscript", (detect(".bashrc", "alias ll='ls -l'\n".toByteArray()) as FileKind.Text).language)
        assertEquals(FileKind.Data(DataFormat.MARKDOWN), detect("README.md", "# Title\n".toByteArray()))
    }

    @Test fun extensionlessTextUsesContent() {
        assertEquals("python", (detect("script", "#!/usr/bin/env python3\nprint(1)\n".toByteArray()) as FileKind.Text).language)
        assertEquals(FileKind.Image(ImageFormat.SVG), detect("icon", "<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray()))
        assertEquals(FileKind.Data(DataFormat.HTML), detect("page", "<!DOCTYPE html><html></html>".toByteArray()))
        assertEquals(FileKind.Data(DataFormat.JSON), detect("data", "{\"a\": 1}".toByteArray()))
        val plain = detect("notes", "just some words\n".toByteArray())
        assertEquals(FileKind.Text(null, "Plain text", Charsets.UTF_8), plain)
    }

    @Test fun misnamedFilesFollowContent() {
        assertEquals(FileKind.Image(ImageFormat.PNG), detect("photo.txt", png))
        assertEquals(FileKind.Pdf, detect("invoice.jpg", "%PDF-1.4\n".toByteArray()))
        assertEquals(FileKind.Binary(), detect("data.txt", bytes(1, 2, 3, 0, 0, 0, 0xFE, 0x10, 0x00, 0x99)))
        assertEquals(FileKind.Text(null, "Plain text", Charsets.UTF_8), detect("notes.pdf", "hello, not a pdf\n".toByteArray()))
    }

    @Test fun unknownBinaryFallsBack() {
        assertEquals(FileKind.Binary(), detect("blob", ByteArray(512) { (it * 7).toByte() }))
        assertEquals(FileKind.Binary("Binary data"), detect("firmware.bin", ByteArray(64) { (it * 31).toByte() }))
    }

    @Test fun utf16WithAndWithoutBom() {
        val le = "hello world, this is text".toByteArray(Charsets.UTF_16LE)
        val withBom = detect("x.txt", bytes(0xFF, 0xFE) + le) as FileKind.Text
        assertEquals(Charsets.UTF_16LE, withBom.charset)
        val noBom = detect("noext", le) as FileKind.Text
        assertEquals(Charsets.UTF_16LE, noBom.charset)
        val be = detect("noext", "hello world big endian".toByteArray(Charsets.UTF_16BE)) as FileKind.Text
        assertEquals(Charsets.UTF_16BE, be.charset)
    }

    @Test fun truncatedUtf8SampleIsStillText() {
        val text = "é".repeat(3000).toByteArray() // 6000 bytes; the 4096-byte sample ends on a full char
        val oddCut = ("a" + "é".repeat(3000)).toByteArray() // sample ends mid-sequence
        assertEquals(Charsets.UTF_8, (detect("a.txt", text) as FileKind.Text).charset)
        val kind = detect("noext", oddCut) as FileKind.Text
        assertEquals(Charsets.UTF_8, kind.charset)
    }

    @Test fun mimeHintUsedOnlyWhenNameAndMagicAreSilent() {
        assertEquals(FileKind.Data(DataFormat.JSON), detect("download", "[1, 2, 3]".toByteArray(), "application/json"))
        assertEquals(FileKind.Archive(ArchiveFormat.ZIP, "APK"), detect("download", ByteArray(8) { 0x55 }, "application/vnd.android.package-archive"))
    }

    @Test fun gzipTarballAndEmptyFile() {
        val gz = ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write("x".toByteArray()) } }.toByteArray()
        assertEquals(FileKind.Archive(ArchiveFormat.GZIP), detect("a.tar.gz", gz))
        assertTrue(detect("empty.txt", ByteArray(0)) is FileKind.Text)
    }
}
