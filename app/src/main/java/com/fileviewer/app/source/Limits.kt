package com.fileviewer.app.source

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Every resource bound in the app lives here so viewers never pick their own. */
object Limits {
    /** Bytes sampled for detection. Must cover tar's `ustar` at 257 and three MPEG-TS sync bytes. */
    const val HEADER_BYTES = 4096

    /** Text read into the editor, counted by bytes actually read. Larger files open in paged text mode. */
    const val MAX_EDITOR_TEXT_BYTES = 20L shl 20

    /** Above this many bytes the editor shows plain text without syntax highlighting. */
    const val MAX_HIGHLIGHT_BYTES = 5L shl 20

    /** Lines longer than this are soft-broken for display (minified files stall the editor otherwise). */
    const val MAX_LINE_DISPLAY_CHARS = 5_000

    /** Largest stream copied into the cache to make it seekable. */
    const val MAX_MATERIALIZE_BYTES = 2L shl 30

    /** The materialize cache is trimmed least-recently-used down to this size. */
    const val MAX_CACHE_BYTES = 1L shl 30

    const val MAX_ARCHIVE_ENTRIES = 100_000

    /** Archive inside archive inside archive... deeper than this opens in Hex/Info only. */
    const val MAX_ARCHIVE_DEPTH = 4

    /** Decompressed bytes of a single archive entry. */
    const val MAX_ENTRY_BYTES = 2L shl 30

    /** Decompressed bytes scanned while listing a stream archive (tar.gz and friends). */
    const val MAX_ARCHIVE_SCAN_BYTES = 8L shl 30

    /** ZIP subtype detection looks up at most this many marker entries. */
    const val ZIP_INSPECT_ENTRIES = 16

    /** Longest edge of a decoded image bitmap; telephoto subsamples beyond this. */
    const val MAX_IMAGE_DECODE_PX = 4096

    /** Longest edge of a rendered PDF page bitmap. */
    const val MAX_PDF_PAGE_PX = 4096

    /** Bitmap bytes kept by the PDF page cache. */
    const val MAX_PDF_CACHE_BYTES = 96L shl 20

    const val HEX_PAGE_BYTES = 64 * 1024

    /** Paged text mode reads the file in chunks of about this many bytes, cut at a newline. */
    const val PAGED_TEXT_CHUNK_BYTES = 32 * 1024
}

class LimitExceededException(what: String, limit: Long) :
    IOException("$what is larger than the ${formatLimit(limit)} limit")

private fun formatLimit(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "${bytes shr 30} GB"
    bytes >= 1L shl 20 -> "${bytes shr 20} MB"
    bytes >= 1L shl 10 -> "${bytes shr 10} KB"
    else -> "$bytes byte"
}

/** Throws [LimitExceededException] once more than [limit] bytes have been read. */
class LimitedInputStream(input: InputStream, private val limit: Long, private val what: String) :
    FilterInputStream(input) {
    var count = 0L
        private set

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) advance(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) advance(n.toLong())
        return n
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) advance(skipped)
        return skipped
    }

    private fun advance(n: Long) {
        count += n
        if (count > limit) throw LimitExceededException(what, limit)
    }
}

/** Reads until [buf] is full or the stream ends; returns the byte count. */
fun InputStream.readFully(buf: ByteArray, max: Int = buf.size): Int {
    var total = 0
    while (total < max) {
        val n = read(buf, total, max - total)
        if (n < 0) break
        total += n
    }
    return total
}
