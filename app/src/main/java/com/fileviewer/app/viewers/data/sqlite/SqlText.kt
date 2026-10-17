package com.fileviewer.app.viewers.data.sqlite

import com.fileviewer.app.source.Limits
import com.fileviewer.app.source.LimitedInputStream
import com.fileviewer.app.source.LimitExceededException
import java.io.File

/** SQL text and cell formatting for the SQLite browser. Pure, so it runs under JVM tests. */
object SqlText {

    /** `"name"` with embedded quotes doubled: safe for any table name. */
    fun quote(identifier: String): String = "\"" + identifier.replace("\"", "\"\"") + "\""

    /** Asks for one row more than a page so the caller learns whether a next page exists. */
    fun pageQuery(table: String, page: Int, columns: List<String>, pageRows: Int = Limits.SQL_PAGE_ROWS): String {
        val projection = columns.joinToString(", ") { column ->
            val id = quote(column)
            "CASE WHEN typeof($id) = 'blob' THEN length($id) ELSE substr(CAST($id AS TEXT), 1, ${Limits.MAX_CELL_CHARS + 1}) END, typeof($id)"
        }
        return "SELECT $projection FROM ${quote(table)} LIMIT ${pageRows + 1} OFFSET ${page.toLong() * pageRows}"
    }

    fun columnsQuery(table: String) = "SELECT * FROM ${quote(table)} LIMIT 0"

    fun countQuery(table: String): String = "SELECT COUNT(*) FROM ${quote(table)}"

    const val OBJECTS_QUERY =
        "SELECT name, type FROM sqlite_master WHERE type IN ('table', 'view') ORDER BY type DESC, name COLLATE NOCASE"

    const val SCHEMA_QUERY =
        "SELECT sql FROM sqlite_master WHERE tbl_name = ? AND sql IS NOT NULL ORDER BY CASE type WHEN 'table' THEN 0 WHEN 'view' THEN 1 WHEN 'index' THEN 2 ELSE 3 END, name"

    /** `1,234 rows`, `1 row`. */
    fun rowsLabel(count: Long): String = "%,d %s".format(count, if (count == 1L) "row" else "rows")

    /** The text of a BLOB cell: its size, never its bytes. */
    fun blobLabel(size: Int): String = "<blob, %,d %s>".format(size, if (size == 1) "byte" else "bytes")

    fun clipCell(text: String, max: Int = Limits.MAX_CELL_CHARS): String =
        if (text.length > max) text.substring(0, max) + "\u2026" else text

    /** True for the tables SQLite keeps for itself (`sqlite_sequence`...). */
    fun isInternal(name: String) = name.startsWith("sqlite_", ignoreCase = true)

    /** Pages needed for [rows] rows, at least one. */
    fun pageCount(rows: Long, pageRows: Int = Limits.SQL_PAGE_ROWS): Long = maxOf(1L, (rows + pageRows - 1) / pageRows)

    /** `101-200 of 1,204`. */
    fun rangeLabel(page: Int, shown: Int, total: Long?, pageRows: Int = Limits.SQL_PAGE_ROWS): String {
        if (shown == 0) return "No rows"
        val first = page.toLong() * pageRows + 1
        val last = first + shown - 1
        return if (total != null) "%,d\u2013%,d of %,d".format(first, last, total) else "%,d\u2013%,d".format(first, last)
    }
}

/** How a database file is opened so the original is never touched. */
object SqliteFiles {
    fun copy(source: File, target: File, limit: Long = Limits.MAX_MATERIALIZE_BYTES) {
        if (source.length() > limit) throw LimitExceededException("Database copy", limit)
        LimitedInputStream(source.inputStream(), limit, "Database copy").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    /** Byte 18 and 19 of the header are 2 for a database in write-ahead-log mode. */
    fun isWalMode(header: ByteArray): Boolean = header.size > 19 && header[18].toInt() == 2 && header[19].toInt() == 2

    /**
     * A database in WAL mode, or one with a `-wal` or `-journal` beside it, can't be opened read-only in place:
     * SQLite wants to create or replay side files. Those are opened from a private copy.
     */
    fun needsCopy(header: ByteArray, hasWal: Boolean, hasJournal: Boolean): Boolean =
        isWalMode(header) || hasWal || hasJournal
}
