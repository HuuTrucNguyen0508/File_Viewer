package com.fileviewer.app.source

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/** Every resource bound in the app lives here so viewers never pick their own. */
object Limits {
    /** Bytes sampled for detection. Must cover tar's `ustar` at 257 and three MPEG-TS sync bytes. */
    const val HEADER_BYTES = 4096

    /** Largest `name` table read from a font. */
    const val MAX_FONT_NAME_CHARS = 4096
    const val MAX_NAME_TABLE_BYTES = 1 shl 20
    const val MAX_FONT_TABLES = 256
    const val MAX_FONT_FACES = 1024

    /** Largest `cmap` table read from a font. */
    const val MAX_CMAP_TABLE_BYTES = 8 shl 20

    /** Code point ranges kept from a cmap. */
    const val MAX_COVERAGE_RANGES = 100_000

    /** Glyph cells shown for one block of the glyph grid. */
    const val MAX_GLYPHS_PER_SECTION = 512

    /** Glyph cells shown outside the common blocks. */
    const val MAX_OTHER_GLYPHS = 1024

    /** Largest certificate or key file parsed. */
    const val MAX_CERT_FILE_BYTES = 4L shl 20

    /** Certificates and keys listed from one file. */
    const val MAX_CERT_ITEMS = 200

    /** Permissions listed for one APK. */
    const val MAX_PERMISSIONS = 500

    /** Weighted parser work: block starts, inline delimiters, and table cells. */
    const val MAX_MARKDOWN_COMPLEXITY = 360_000
    const val MARKDOWN_LINE_COST = 4
    const val MARKDOWN_BLOCK_COST = 32
    const val MARKDOWN_TABLE_CELL_COST = 4
    /** Leading container nesting on one line. */
    const val MAX_MARKDOWN_CONTAINERS = 64
    /** Rendered HTML characters, enforced while appending. */
    const val MAX_MARKDOWN_HTML_CHARS = 8_000_000

    /** Bytes read from Markdown, HTML, CSV or JSON; excess is cut with a note. */
    const val MAX_DATA_TEXT_BYTES = 32L shl 20

    /** CSV and SQLite tables: rows kept in memory, columns shown, characters kept per cell. */
    const val MAX_TABLE_ROWS = 50_000
    const val MAX_TABLE_COLS = 200
    const val MAX_CELL_CHARS = 2_000

    /** JSON: nesting accepted, nodes kept in the tree, characters kept per string value. */
    const val MAX_JSON_DEPTH = 200
    const val MAX_JSON_NODES = 200_000
    const val MAX_JSON_STRING_CHARS = 20_000

    /** SQLite: rows per page and tables or views listed. */
    const val SQL_PAGE_ROWS = 100
    const val MAX_DB_OBJECTS = 5_000

    /** A stylesheet, image or font fetched from the folder next to an HTML or Markdown file. */
    const val MAX_RESOURCE_BYTES = 16L shl 20

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

    const val MAX_ARCHIVE_PATH_CHARS = 4096
    const val MAX_ARCHIVE_PATH_DEPTH = 128
    const val MAX_ARCHIVE_TREE_NODES = 100_000

    /** Local extra bytes counted once per referencing entry, even for shared headers. */
    const val MAX_ZIP_LOCAL_EXTRA_BYTES = 4L shl 20

    /** Extra fields in one ZIP header, ignoring local repeats. Real archives carry a handful; merging them is quadratic. */
    const val MAX_ZIP_EXTRA_FIELDS = 64

    const val MAX_ZIP_DIRECTORY_BYTES = 8L shl 20

    const val MAX_ARCHIVE_ENTRIES = 100_000

    const val MAX_DECOMPRESSOR_MEMORY_KIB = 64 * 1024

    /** Archive inside archive inside archive... deeper than this opens in Hex/Info only. */
    const val MAX_ARCHIVE_DEPTH = 4

    /** Decompressed bytes of a single archive entry. */
    const val MAX_ENTRY_BYTES = 2L shl 30

    /** Decompressed bytes scanned while listing a stream archive (tar.gz and friends). */
    const val MAX_ARCHIVE_SCAN_BYTES = 8L shl 30

    /** ZIP subtype detection looks up at most this many marker entries. */
    const val ZIP_INSPECT_ENTRIES = 16

    /** Decompressed bytes of one XML part (document.xml, a sheet, a chapter) read by a document preview. */
    const val MAX_DOC_PART_BYTES = 64L shl 20

    /** Decompressed bytes read across all parts of one document preview (zip-bomb bound). */
    const val MAX_DOC_TOTAL_BYTES = 256L shl 20

    /** Characters of HTML a document preview produces across pages; the rest is cut with a note. */
    const val MAX_DOC_HTML_CHARS = 4_000_000

    /** One embedded image served to a document preview. */
    const val MAX_DOC_IMAGE_BYTES = 32L shl 20

    /** Spreadsheet preview: rows and columns shown per sheet. */
    const val MAX_SHEET_ROWS = 2_000
    const val MAX_SHEET_COLS = 100

    /** Shared strings kept from an XLSX workbook. */
    const val MAX_SHARED_STRINGS = 500_000

    /** Sheets, slides, or EPUB chapters listed per document. */
    const val MAX_DOC_SECTIONS = 2_000

    /** Element nesting accepted in document XML and RTF groups. */
    const val MAX_DOC_NESTING = 512

    /** Bytes of a legacy binary DOC/XLS/PPT scanned for readable text. */
    const val MAX_LEGACY_SCAN_BYTES = 32L shl 20

    /** Longest edge of a decoded image bitmap; telephoto subsamples beyond this. */
    const val MAX_IMAGE_DECODE_PX = 4096

    /** Longest edge of a rendered PDF page bitmap. */
    const val MAX_PDF_PAGE_PX = 4096

    /** Bitmap bytes kept by the PDF page cache. */
    const val MAX_PDF_CACHE_BYTES = 96L shl 20

    const val HEX_WINDOW_ROWS = 1_000_000_000

    const val HEX_PAGE_BYTES = 64 * 1024

    /** Paged text mode reads the file in chunks of about this many bytes, cut at a newline. */
    const val PAGED_TEXT_CHUNK_BYTES = 32 * 1024
}

open class LimitExceededException : IOException {
    constructor(what: String, limit: Long) : super("$what is larger than the ${formatLimit(limit)} limit")
    constructor(message: String) : super(message)
}

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
        checkInterrupted()
        val b = `in`.read()
        if (b >= 0) advance(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        checkInterrupted()
        val allowed = minOf(len.toLong(), (limit - count + 1).coerceAtLeast(1)).toInt()
        val n = `in`.read(b, off, allowed)
        if (n > 0) advance(n.toLong())
        return n
    }

    override fun skip(n: Long): Long {
        val buffer = ByteArray(minOf(n.coerceAtLeast(0), 8192).toInt())
        var skipped = 0L
        while (skipped < n) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), n - skipped).toInt())
            if (read < 0) break
            skipped += read
        }
        return skipped
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Read cancelled")
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
