package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.LimitExceededException
import com.fileviewer.app.source.LimitedInputStream
import com.fileviewer.app.source.BoundedZip
import com.fileviewer.app.source.Limits
import com.fileviewer.app.source.readFully
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.Closeable
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.SeekableByteChannel

/** A problem worth showing the user as is. */
class DocumentException(message: String) : IOException(message)

/**
 * The parts of a zipped document (OOXML, ODF, EPUB). Every read is bounded per part by
 * [Limits.MAX_DOC_PART_BYTES] and, between [resetBudget] calls, in total by [Limits.MAX_DOC_TOTAL_BYTES].
 * Reads are serialized, so the WebView's image requests can share it with a running parse.
 */
class DocPackage private constructor(private val zip: ZipFile) : Closeable {
    private val lock = Any()
    private var budget = Limits.MAX_DOC_TOTAL_BYTES

    /** Starts a new operation (a whole preview, or one EPUB chapter) with a full read budget. */
    fun resetBudget() = synchronized(lock) { budget = Limits.MAX_DOC_TOTAL_BYTES }

    fun has(path: String): Boolean = entry(path)?.isDirectory == false

    /** Parses one part with [block]; the stream is closed afterwards. Throws when the part is missing. */
    fun <T> read(path: String, block: (InputStream) -> T): T = synchronized(lock) {
        val e = entry(path)?.takeUnless { it.isDirectory } ?: throw DocumentException("The file is missing its part $path.")
        open(e, Limits.MAX_DOC_PART_BYTES, counted = true).use(block)
    }

    fun <T> readIfPresent(path: String, block: (InputStream) -> T): T? = if (has(path)) read(path, block) else null

    /** A whole part as bytes, for small XML files that need pre-processing. */
    fun bytes(path: String): ByteArray = read(path) { it.readBytes() }

    /** An embedded image's bytes, or null when it is missing, unreadable or too large. Not counted against the budget. */
    fun image(path: String): ByteArray? = synchronized(lock) {
        val e = entry(path)?.takeUnless { it.isDirectory } ?: return null
        if (e.size > Limits.MAX_DOC_IMAGE_BYTES) return null
        runCatching { open(e, Limits.MAX_DOC_IMAGE_BYTES, counted = false).use { it.readBytes() } }.getOrNull()
    }

    override fun close() = zip.close()

    private fun entry(path: String): ZipArchiveEntry? = zip.getEntry(path) ?: zip.getEntry("/$path")

    private fun open(e: ZipArchiveEntry, limit: Long, counted: Boolean): InputStream {
        if (e.generalPurposeBit.usesEncryption()) throw DocumentException("This file is password-protected.")
        if (!zip.canReadEntryData(e)) throw DocumentException("Part ${e.name} uses a compression method the app can't read.")
        val raw = LimitedInputStream(zip.getInputStream(e), limit, e.name)
        return if (counted) BudgetStream(raw) else raw
    }

    private inner class BudgetStream(input: InputStream) : FilterInputStream(input) {
        override fun read(): Int = super.read().also { if (it >= 0) spend(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) spend(it.toLong()) }
        private fun spend(n: Long) {
            budget -= n
            if (budget < 0) throw LimitExceededException("This document's unpacked content", Limits.MAX_DOC_TOTAL_BYTES)
        }
    }

    companion object {
        /** Takes ownership of [channel]. */
        fun open(channel: SeekableByteChannel): DocPackage {
            val zip = try {
                BoundedZip.open(channel)
            } catch (e: LimitExceededException) {
                throw e
            } catch (e: IOException) {
                channel.close()
                throw DocumentException("This file isn't a valid document package: ${e.message}")
            }
            var count = 0
            for (ignored in zip.entries) if (++count > Limits.MAX_ARCHIVE_ENTRIES) {
                zip.close()
                throw LimitExceededException("Document entry count", Limits.MAX_ARCHIVE_ENTRIES.toLong())
            }
            return DocPackage(zip)
        }

        const val ENCRYPTED = "This file is encrypted. Password-protected Office files can't be previewed."

        /** True for an OLE2 compound file, which is how Office stores encrypted DOCX/XLSX/PPTX. */
        fun looksEncryptedOffice(head: InputStream): Boolean {
            val b = ByteArray(8)
            return head.readFully(b) == 8 && b.contentEquals(OLE2_MAGIC)
        }

        private val OLE2_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
    }
}
