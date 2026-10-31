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
        // Fail closed: a malformed directory must not bypass accounting for local extras.
        inspect(channel)
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
        // The library merges each header's fields quadratically, so bytes alone do not bound its work.
        // Central fields are retained with repeats. Local repeats collapse, and must be allowed,
        // because zipalign's zero padding parses as thousands of id-0 fields.
        fun checkExtraFields(start: Long, length: Int, countRepeats: Boolean) {
            var field = start
            var fields = 0
            val ids = HashSet<Short>()
            while (field + 4 <= start + length) {
                val header = read(field, 4)
                if (ids.add(header.short) || countRepeats) fields++
                if (fields > Limits.MAX_ZIP_EXTRA_FIELDS)
                    throw LimitExceededException("ZIP header has more than ${Limits.MAX_ZIP_EXTRA_FIELDS} extra fields")
                field += 4 + (header.short.toInt() and 65535)
            }
        }
        // Library scans backwards from size-22 through size-(22+65535), inclusive,
        // accepting the first signature without validating the declared comment length.
        var eocd = c.size() - 22
        val stop = maxOf(0, c.size() - 65557)
        while (eocd >= stop && u32(eocd) != 0x06054b50L) eocd--
        if (eocd < stop) throw IOException("No EOCD")
        var prefix = 0L
        var at = if (eocd > 20 && u32(eocd - 20) == 0x07064b50L) {
            val zip64 = i64(eocd - 12)
            if (u32(zip64) != 0x06064b50L) throw IOException("Invalid ZIP64 locator")
            i64(zip64 + 48) // No prefix correction in the library's non-split ZIP64 branch.
        } else {
            val size = u32(eocd + 12)
            val offset = u32(eocd + 16)
            prefix = maxOf(eocd - size - offset, 0)
            offset + prefix
        }
        var entries = 0
        var bytes = 0L
        var localExtras = 0L
        while (u32(at) == 0x02014b50L) {
            if (++entries > Limits.MAX_ARCHIVE_ENTRIES)
                throw LimitExceededException("ZIP entry count", Limits.MAX_ARCHIVE_ENTRIES.toLong())
            val header = read(at, 46)
            val length = 46L + (header.getShort(28).toInt() and 65535) +
                (header.getShort(30).toInt() and 65535) + (header.getShort(32).toInt() and 65535)
            val nameLength = header.getShort(28).toInt() and 65535
            val extraLength = header.getShort(30).toInt() and 65535
            val size64 = header.getInt(24) == -1
            val compressed64 = header.getInt(20) == -1
            var local = (header.getInt(42).toLong() and 0xffffffffL) + prefix
            if (local == 0xffffffffL) {
                var extra = at + 46 + nameLength
                val end = extra + extraLength
                var found = false
                while (extra + 4 <= end) {
                    val field = read(extra, 4)
                    val id = field.short.toInt() and 65535
                    val length = field.short.toInt() and 65535
                    if (extra + 4 + length > end) throw IOException("Invalid ZIP extra")
                    if (id == 1) {
                        val offsetAt = extra + 4 + (if (size64) 8 else 0) + (if (compressed64) 8 else 0)
                        if (offsetAt + 8 > extra + 4 + length) throw IOException("Missing ZIP64 offset")
                        local = i64(offsetAt) // Library replaces, rather than adds, the prefix for ZIP64 offsets.
                        found = true
                        break
                    }
                    extra += 4 + length
                }
                if (!found) throw IOException("Missing ZIP64 offset")
            }
            checkExtraFields(at + 46 + nameLength, extraLength, countRepeats = true)
            val localHeader = read(local + 26, 4)
            val localNameLength = localHeader.short.toInt() and 65535 // skipped by the library
            val localExtraLength = localHeader.short.toInt() and 65535
            checkExtraFields(local + 30 + localNameLength, localExtraLength, countRepeats = false)
            localExtras += localExtraLength
            if (localExtras > Limits.MAX_ZIP_LOCAL_EXTRA_BYTES)
                throw LimitExceededException("ZIP local extra metadata", Limits.MAX_ZIP_LOCAL_EXTRA_BYTES)
            bytes += length
            if (bytes > Limits.MAX_ZIP_DIRECTORY_BYTES)
                throw LimitExceededException("ZIP central directory", Limits.MAX_ZIP_DIRECTORY_BYTES)
            if (length > c.size() - at) throw EOFException()
            at += length
        }
    }
}
