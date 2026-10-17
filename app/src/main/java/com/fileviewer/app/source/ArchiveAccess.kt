package com.fileviewer.app.source

import com.fileviewer.app.detect.ArchiveFormat
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

data class ArchiveEntry(
    /** Full path inside the archive, '/'-separated, without a leading slash. Display only, never a filesystem path. */
    val path: String,
    val isDirectory: Boolean,
    val size: Long?,
    val compressedSize: Long?,
    val lastModified: Long?,
    /** Listed but unreadable without a password. */
    val encrypted: Boolean = false,
)

data class ArchiveListing(
    val entries: List<ArchiveEntry>,
    /** True when [Limits.MAX_ARCHIVE_ENTRIES] cut the listing short. */
    val truncated: Boolean,
    /** A lone compressed file (e.g. notes.txt.gz) rather than a container. */
    val singleStream: Boolean = false,
)

class ArchiveUnsupportedException(message: String) : IOException(message)

/**
 * Lists and opens archive entries. ZIP and 7z need a [SeekableSource] (central directory, random access);
 * TAR, ar and the single-stream compressors read the source as a stream. Frozen contract for phase 2.
 * Blocking I/O.
 */
object ArchiveAccess {

    fun needsSeekable(format: ArchiveFormat) = format == ArchiveFormat.ZIP || format == ArchiveFormat.SEVEN_Z

    /** Lists entries. [seekable] is required for [needsSeekable] formats and is not closed here. */
    fun list(format: ArchiveFormat, source: FileSource, seekable: SeekableSource?): ArchiveListing = when (format) {
        ArchiveFormat.ZIP -> zip(requireSeekable(seekable)).use { z ->
            collect(z.entries.asSequence().map { e ->
                ArchiveEntry(
                    path = e.name.trimStart('/'),
                    isDirectory = e.isDirectory,
                    size = e.size.takeIf { it >= 0 },
                    compressedSize = e.compressedSize.takeIf { it >= 0 },
                    lastModified = e.time.takeIf { it > 0 },
                    encrypted = e.generalPurposeBit.usesEncryption(),
                )
            })
        }
        ArchiveFormat.SEVEN_Z -> sevenZ(requireSeekable(seekable)).use { z ->
            collect(z.entries.asSequence().map { e ->
                ArchiveEntry(
                    path = e.name.trimStart('/'),
                    isDirectory = e.isDirectory,
                    size = if (e.isDirectory) null else e.size,
                    compressedSize = null,
                    lastModified = if (e.hasLastModifiedDate) e.lastModifiedTime.toMillis() else null,
                    encrypted = e.isEncrypted(),
                )
            })
        }
        ArchiveFormat.TAR, ArchiveFormat.AR -> streamArchive(format, source.openStream()).use { collectStream(it) }
        ArchiveFormat.GZIP, ArchiveFormat.BZIP2, ArchiveFormat.XZ -> {
            val decompressed = decompressed(format, source)
            decompressed.use {
                if (startsWithTar(it)) {
                    collectStream(TarArchiveInputStream(it))
                } else {
                    ArchiveListing(listOf(ArchiveEntry(innerName(source.name, format), false, null, source.size, null)), false, true)
                }
            }
        }
        ArchiveFormat.RAR -> throw ArchiveUnsupportedException("RAR archives can't be opened: there is no RAR reader in this app yet.")
    }

    /**
     * Opens one entry's bytes. Takes ownership of [seekable] and closes it with the returned stream.
     * Throws for encrypted entries.
     */
    fun openEntry(format: ArchiveFormat, source: FileSource, seekable: SeekableSource?, path: String): InputStream {
        try {
            return when (format) {
                ArchiveFormat.ZIP -> {
                    val z = zip(requireSeekable(seekable))
                    val e = z.getEntry(path) ?: z.getEntry("/$path") ?: run { z.close(); throw IOException("No entry $path") }
                    if (e.generalPurposeBit.usesEncryption()) { z.close(); throw ArchiveUnsupportedException("This entry is password-protected.") }
                    if (!z.canReadEntryData(e)) { z.close(); throw ArchiveUnsupportedException("This entry uses a compression method the app can't read.") }
                    try {
                        z.getInputStream(e).onClose { try { z.close() } finally { seekable?.close() } }
                    } catch (t: Throwable) { z.close(); throw t }
                }
                ArchiveFormat.SEVEN_Z -> {
                    val z = sevenZ(requireSeekable(seekable))
                    val e = z.entries.firstOrNull { it.name.trimStart('/') == path } ?: run { z.close(); throw IOException("No entry $path") }
                    if (e.isEncrypted()) { z.close(); throw ArchiveUnsupportedException("This entry is password-protected.") }
                    try {
                        z.getInputStream(e).onClose { try { z.close() } finally { seekable?.close() } }
                    } catch (t: Throwable) { z.close(); throw t }
                }
                ArchiveFormat.TAR, ArchiveFormat.AR -> seekToEntry(streamArchive(format, source.openStream()), path)
                ArchiveFormat.GZIP, ArchiveFormat.BZIP2, ArchiveFormat.XZ -> {
                    val decompressed = decompressed(format, source)
                    try {
                        if (startsWithTar(decompressed)) seekToEntry(TarArchiveInputStream(decompressed), path) else decompressed
                    } catch (t: Throwable) { decompressed.close(); throw t }
                }
                ArchiveFormat.RAR -> throw ArchiveUnsupportedException("RAR archives can't be opened yet.")
            }.also { if (!needsSeekable(format)) seekable?.close() }
        } catch (t: Throwable) {
            seekable?.close()
            throw t
        }
    }

    private fun requireSeekable(s: SeekableSource?) = s ?: throw IllegalArgumentException("Seekable source required")

    private fun zip(s: SeekableSource): ZipFile = ZipFile.builder().setSeekableByteChannel(s.channel()).get()

    private fun sevenZ(s: SeekableSource): SevenZFile = SevenZFile.builder().setSeekableByteChannel(s.channel()).setMaxMemoryLimitKiB(Limits.MAX_DECOMPRESSOR_MEMORY_KIB).get()

    private fun SevenZArchiveEntry.isEncrypted(): Boolean =
        runCatching { contentMethods?.any { it.method == SevenZMethod.AES256SHA256 } == true }.getOrDefault(false)

    private fun streamArchive(format: ArchiveFormat, input: InputStream): ArchiveInputStream<*> {
        val buffered = BufferedInputStream(input, 64 * 1024)
        return if (format == ArchiveFormat.AR) ArArchiveInputStream(buffered) else TarArchiveInputStream(buffered)
    }

    private fun decompress(format: ArchiveFormat, input: InputStream): InputStream {
        val buffered = BufferedInputStream(input, 64 * 1024)
        return try { when (format) {
            ArchiveFormat.GZIP -> GzipCompressorInputStream.builder().setInputStream(buffered).setDecompressConcatenated(true).get()
            ArchiveFormat.BZIP2 -> BZip2CompressorInputStream(buffered, true)
            ArchiveFormat.XZ -> XZCompressorInputStream(buffered, true, Limits.MAX_DECOMPRESSOR_MEMORY_KIB)
            else -> error("not a compressor: $format")
        } } catch (t: Throwable) { buffered.close(); throw t }
    }

    private fun decompressed(format: ArchiveFormat, source: FileSource): BufferedInputStream =
        BufferedInputStream(LimitedInputStream(decompress(format, source.openStream()), Limits.MAX_ARCHIVE_SCAN_BYTES, source.name), 64 * 1024)

    /** Peeks for the `ustar` magic at offset 257 without consuming the stream. */
    private fun startsWithTar(input: BufferedInputStream): Boolean {
        input.mark(512)
        val head = ByteArray(512)
        val n = input.readFully(head)
        input.reset()
        return n >= 262 && String(head, 257, 5, Charsets.US_ASCII) == "ustar"
    }

    /** "notes.txt.gz" -> "notes.txt"; "data.tgz" never reaches here because it holds a tar. */
    fun innerName(name: String, format: ArchiveFormat): String {
        val suffixes = when (format) {
            ArchiveFormat.GZIP -> listOf(".gz", ".gzip", ".z")
            ArchiveFormat.BZIP2 -> listOf(".bz2", ".bzip2", ".bz")
            ArchiveFormat.XZ -> listOf(".xz")
            else -> emptyList()
        }
        val suffix = suffixes.firstOrNull { name.lowercase().endsWith(it) && name.length > it.length }
        return if (suffix != null) name.dropLast(suffix.length) else "$name.out"
    }

    private fun collect(entries: Sequence<ArchiveEntry>): ArchiveListing {
        val list = ArrayList<ArchiveEntry>()
        for (e in entries) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Archive scan cancelled")
            if (list.size >= Limits.MAX_ARCHIVE_ENTRIES) return ArchiveListing(list, true)
            list += e
        }
        return ArchiveListing(list, false)
    }

    private fun collectStream(input: ArchiveInputStream<*>): ArchiveListing = collect(generateSequence {
        input.nextEntry?.let { e ->
            ArchiveEntry(
                path = e.name.trimStart('/').removePrefix("./"),
                isDirectory = e.isDirectory,
                size = e.size.takeIf { it >= 0 && !e.isDirectory },
                compressedSize = null,
                lastModified = e.lastModifiedDate?.time,
            )
        }
    }.filter { it.path.isNotEmpty() })

    private fun seekToEntry(input: ArchiveInputStream<*>, path: String): InputStream {
        try {
            var scanned = 0
            while (scanned++ < Limits.MAX_ARCHIVE_ENTRIES) {
                if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Archive scan cancelled")
                val e = input.nextEntry ?: throw IOException("No entry $path")
                if (e.name.trimStart('/').removePrefix("./") == path) return input
            }
            throw LimitExceededException("Archive entries", Limits.MAX_ARCHIVE_ENTRIES.toLong())
        } catch (t: Throwable) { input.close(); throw t }
    }

    private fun InputStream.onClose(block: () -> Unit): InputStream = object : FilterInputStream(this) {
        override fun close() {
            try { super.close() } finally { block() }
        }
    }
}
