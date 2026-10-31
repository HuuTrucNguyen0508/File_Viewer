package com.fileviewer.app.source

import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel

/** The sole eager ZIP-index factory. Locator arithmetic matches Commons Compress 1.28.0 ZipFile. */
object BoundedZip {
    fun open(channel: SeekableByteChannel): ZipFile {
        try {
            preflight(channel)
            channel.position(0)
            return ZipFile.builder().setSeekableByteChannel(channel).get()
        } catch (t: Throwable) { channel.close(); throw t }
    }

    fun preflight(channel: SeekableByteChannel) {
        try {
            inspect(channel)
        } catch (e: LimitExceededException) {
            throw e
        } catch (_: IOException) {
            // Preserve permissive library handling of small malformed layouts. Even if every
            // byte were a central record, this file cannot hold 100,000 records or 8 MB metadata.
            if (channel.size() > Limits.MAX_ZIP_UNPARSED_BYTES)
                throw LimitExceededException("Unrecognized ZIP layout", Limits.MAX_ZIP_UNPARSED_BYTES)
        } catch (_: IllegalArgumentException) {
            if (channel.size() > Limits.MAX_ZIP_UNPARSED_BYTES)
                throw LimitExceededException("Unrecognized ZIP layout", Limits.MAX_ZIP_UNPARSED_BYTES)
        }
    }

    private fun inspect(c: SeekableByteChannel) {
        val buffer = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN)
        fun read(at: Long, count: Int): ByteBuffer {
            if (at < 0 || at > c.size() - count) throw EOFException()
            c.position(at)
            buffer.clear().limit(count)
            while (buffer.hasRemaining()) if (c.read(buffer) < 0) throw EOFException()
            buffer.flip()
            return buffer
        }
        fun u32(at: Long) = read(at, 4).int.toLong() and 0xffffffffL
        fun i64(at: Long) = read(at, 8).long
        // Library scans backwards from size-22 through size-(22+65535), inclusive,
        // accepting the first signature without validating the declared comment length.
        var eocd = c.size() - 22
        val stop = maxOf(0, c.size() - 65557)
        while (eocd >= stop && u32(eocd) != 0x06054b50L) eocd--
        if (eocd < stop) throw IOException("No EOCD")
        var at = if (eocd > 20 && u32(eocd - 20) == 0x07064b50L) {
            val zip64 = i64(eocd - 12)
            if (u32(zip64) != 0x06064b50L) throw IOException("Invalid ZIP64 locator")
            i64(zip64 + 48) // No prefix correction in the library's non-split ZIP64 branch.
        } else {
            val size = u32(eocd + 12)
            val offset = u32(eocd + 16)
            offset + maxOf(eocd - size - offset, 0)
        }
        var entries = 0
        var bytes = 0L
        while (u32(at) == 0x02014b50L) {
            if (++entries > Limits.MAX_ARCHIVE_ENTRIES)
                throw LimitExceededException("ZIP entry count", Limits.MAX_ARCHIVE_ENTRIES.toLong())
            val header = read(at, 46)
            val length = 46L + (header.getShort(28).toInt() and 65535) +
                (header.getShort(30).toInt() and 65535) + (header.getShort(32).toInt() and 65535)
            bytes += length
            if (bytes > Limits.MAX_ZIP_DIRECTORY_BYTES)
                throw LimitExceededException("ZIP central directory", Limits.MAX_ZIP_DIRECTORY_BYTES)
            if (length > c.size() - at) throw EOFException()
            at += length
        }
    }
}
