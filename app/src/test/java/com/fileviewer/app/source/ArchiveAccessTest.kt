package com.fileviewer.app.source

import com.fileviewer.app.BytesSource
import com.fileviewer.app.detect.ArchiveFormat
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

class ArchiveAccessTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun tar(out: OutputStream, vararg files: Pair<String, String>) = TarArchiveOutputStream(out).use { t ->
        t.putArchiveEntry(TarArchiveEntry("src/")); t.closeArchiveEntry()
        files.forEach { (name, body) ->
            val data = body.toByteArray()
            t.putArchiveEntry(TarArchiveEntry(name).apply { size = data.size.toLong() })
            t.write(data); t.closeArchiveEntry()
        }
    }

    private fun zipFile(build: (ZipArchiveOutputStream) -> Unit): File =
        File(tmp.root, "t${System.nanoTime()}.zip").also { f -> ZipArchiveOutputStream(f).use(build) }

    private fun ZipArchiveOutputStream.add(name: String, body: String) {
        putArchiveEntry(ZipArchiveEntry(name)); write(body.toByteArray()); closeArchiveEntry()
    }

    @Test fun zipListsEntriesAndOpensOne() {
        val f = zipFile { it.add("docs/readme.txt", "hello zip"); it.add("main.kt", "fun main() {}") }
        val src = LocalFileSource(f)
        val listing = src.seekableOrNull().use { ArchiveAccess.list(ArchiveFormat.ZIP, src, it) }
        assertEquals(listOf("docs/readme.txt", "main.kt"), listing.entries.map { it.path })
        assertEquals(9L, listing.entries[0].size)
        assertFalse(listing.truncated)
        val text = ArchiveAccess.openEntry(ArchiveFormat.ZIP, src, src.seekableOrNull(), "docs/readme.txt").use { String(it.readBytes()) }
        assertEquals("hello zip", text)
    }

    @Test fun encryptedZipEntryIsListedAsLockedAndRefusesToOpen() {
        // Writers refuse to emit encrypted entries, so set the encryption flag bit by hand
        // in the first local header (offset 6) and the first central directory header (offset 8).
        val f = zipFile { it.add("secret.txt", "abcd"); it.add("open.txt", "ok") }
        val b = f.readBytes()
        b[6] = (b[6].toInt() or 1).toByte()
        val central = (0 until b.size - 4).first { b[it] == 0x50.toByte() && b[it + 1] == 0x4B.toByte() && b[it + 2] == 1.toByte() && b[it + 3] == 2.toByte() }
        b[central + 8] = (b[central + 8].toInt() or 1).toByte()
        f.writeBytes(b)
        val src = LocalFileSource(f)
        val listing = src.seekableOrNull().use { ArchiveAccess.list(ArchiveFormat.ZIP, src, it) }
        assertEquals(listOf(true, false), listing.entries.map { it.encrypted })
        assertThrows(ArchiveUnsupportedException::class.java) {
            ArchiveAccess.openEntry(ArchiveFormat.ZIP, src, src.seekableOrNull(), "secret.txt")
        }
    }

    @Test fun tarGzListsThroughCompressionAndOpensEntry() {
        val bytes = ByteArrayOutputStream().also { tar(GZIPOutputStream(it), "src/a.py" to "print(1)", "src/b.py" to "print(2)") }.toByteArray()
        val src = BytesSource("code.tar.gz", bytes)
        val listing = ArchiveAccess.list(ArchiveFormat.GZIP, src, null)
        assertEquals(listOf("src/", "src/a.py", "src/b.py"), listing.entries.map { it.path })
        assertTrue(listing.entries[0].isDirectory)
        assertFalse(listing.singleStream)
        assertEquals("print(2)", ArchiveAccess.openEntry(ArchiveFormat.GZIP, src, null, "src/b.py").use { String(it.readBytes()) })
    }

    @Test fun plainTarAndXzTar() {
        val plain = ByteArrayOutputStream().also { tar(it, "src/x.txt" to "x") }.toByteArray()
        assertEquals(2, ArchiveAccess.list(ArchiveFormat.TAR, BytesSource("a.tar", plain), null).entries.size)
        val xz = ByteArrayOutputStream().also { tar(XZCompressorOutputStream(it), "src/y.txt" to "yy") }.toByteArray()
        val listing = ArchiveAccess.list(ArchiveFormat.XZ, BytesSource("a.tar.xz", xz), null)
        assertEquals(2L, listing.entries.last().size)
    }

    @Test fun singleGzipIsOneEntryNamedWithoutSuffix() {
        val gz = ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write("log line\n".toByteArray()) } }.toByteArray()
        val src = BytesSource("server.log.gz", gz)
        val listing = ArchiveAccess.list(ArchiveFormat.GZIP, src, null)
        assertTrue(listing.singleStream)
        assertEquals(listOf("server.log"), listing.entries.map { it.path })
        assertEquals("log line\n", ArchiveAccess.openEntry(ArchiveFormat.GZIP, src, null, "server.log").use { String(it.readBytes()) })
    }

    @Test fun sevenZipNeedsSeekableAndLists() {
        val f = File(tmp.root, "a.7z")
        SevenZOutputFile(f).use { z ->
            val e = z.createArchiveEntry(File(tmp.root, "dummy").apply { writeText("") }, "notes/n.md")
            z.putArchiveEntry(e); z.write("# n".toByteArray()); z.closeArchiveEntry()
        }
        val src = LocalFileSource(f)
        assertTrue(ArchiveAccess.needsSeekable(ArchiveFormat.SEVEN_Z))
        val listing = src.seekableOrNull().use { ArchiveAccess.list(ArchiveFormat.SEVEN_Z, src, it) }
        assertEquals(listOf("notes/n.md"), listing.entries.map { it.path })
        assertEquals("# n", ArchiveAccess.openEntry(ArchiveFormat.SEVEN_Z, src, src.seekableOrNull(), "notes/n.md").use { String(it.readBytes()) })
    }

    @Test fun rarIsADocumentedGap() {
        val e = assertThrows(ArchiveUnsupportedException::class.java) {
            ArchiveAccess.list(ArchiveFormat.RAR, BytesSource("a.rar", ByteArray(10)), null)
        }
        assertTrue(e.message!!.startsWith("RAR archives can't be opened"))
    }

    @Test fun entrySourceReadsThroughArchiveAndTracksDepth() {
        val f = zipFile { it.add("inner/data.txt", "nested") }
        val cache = MaterializeCache(tmp.newFolder("cache"))
        val src = LocalFileSource(f)
        val entry = src.seekableOrNull().use { ArchiveAccess.list(ArchiveFormat.ZIP, src, it) }.entries.single()
        val entrySource = ArchiveEntrySource(src, ArchiveFormat.ZIP, entry, cache)
        assertEquals("data.txt", entrySource.name)
        assertEquals(1, entrySource.archiveDepth)
        assertEquals("nested", entrySource.openStream().use { String(it.readBytes()) })
        assertEquals(null, entrySource.seekableOrNull())
    }

    @Test fun invalidCompressedHeaderClosesSource() {
        var closed = false
        val src = object : FileSource by BytesSource("bad.gz", byteArrayOf(1, 2, 3)) {
            override fun openStream() = object : java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
                override fun close() { closed = true; super.close() }
            }
        }
        assertThrows(java.io.IOException::class.java) { ArchiveAccess.list(ArchiveFormat.GZIP, src, null) }
        assertTrue("Failed decompressor construction must close the source", closed)
    }

    @Test fun truncatedTarEntryLookupClosesSource() {
        val bytes = ByteArrayOutputStream().also { tar(it, "first.txt" to "body") }.toByteArray().copyOf(700)
        var closed = false
        val src = object : FileSource by BytesSource("bad.tar", bytes) {
            override fun openStream() = object : java.io.ByteArrayInputStream(bytes) {
                override fun close() { closed = true; super.close() }
            }
        }
        assertThrows(java.io.IOException::class.java) { ArchiveAccess.openEntry(ArchiveFormat.TAR, src, null, "missing") }
        assertTrue("Failed entry lookup must close the source", closed)
    }

    @Test fun innerNames() {
        assertEquals("a.txt", ArchiveAccess.innerName("a.txt.gz", ArchiveFormat.GZIP))
        assertEquals("dump.out", ArchiveAccess.innerName("dump", ArchiveFormat.XZ))
    }
}
