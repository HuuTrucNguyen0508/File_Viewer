package com.fileviewer.app.viewers.hex

import com.fileviewer.app.source.Limits

/** Row and page arithmetic for the hex view. Pure. */
class HexPaging(val fileSize: Long, val bytesPerRow: Int = 16, val pageBytes: Int = Limits.HEX_PAGE_BYTES) {
    init {
        require(fileSize >= 0 && bytesPerRow > 0 && pageBytes % bytesPerRow == 0)
    }

    val rowCount: Long get() = fileSize / bytesPerRow + if (fileSize % bytesPerRow == 0L) 0 else 1
    val rowsPerPage: Int get() = pageBytes / bytesPerRow
    val pageCount: Long get() = fileSize / pageBytes + if (fileSize % pageBytes == 0L) 0 else 1

    fun rowOffset(row: Long): Long = row * bytesPerRow
    fun pageOf(row: Long): Long = row / rowsPerPage
    fun pageStart(page: Long): Long = page * pageBytes

    /** Bytes in [page]; the last page may be short. */
    fun pageLength(page: Long): Int = minOf(pageBytes.toLong(), fileSize - pageStart(page)).toInt().coerceAtLeast(0)

    /** Bytes shown in [row]; the last row may be short. */
    fun rowLength(row: Long): Int = minOf(bytesPerRow.toLong(), fileSize - rowOffset(row)).toInt().coerceAtLeast(0)

    /** Index of [row]'s first byte inside its page buffer. */
    fun indexInPage(row: Long): Int = ((row % rowsPerPage) * bytesPerRow).toInt()

    /** The row containing byte [offset], clamped to the file. */
    fun rowFor(offset: Long): Long = if (fileSize == 0L) 0 else (offset.coerceIn(0, fileSize - 1)) / bytesPerRow

    /** A window starts on a row boundary and never needs an unbounded LazyColumn index. */
    fun windowStartFor(offset: Long): Long = rowFor(offset) / Limits.HEX_WINDOW_ROWS * Limits.HEX_WINDOW_ROWS
    fun windowRows(startRow: Long): Int = minOf(Limits.HEX_WINDOW_ROWS.toLong(), rowCount - startRow).coerceAtLeast(0).toInt()
    fun windowLastByte(startRow: Long): Long {
        val endRow = startRow + windowRows(startRow)
        return if (endRow == rowCount) fileSize - 1 else rowOffset(endRow) - 1
    }
    fun nextWindow(startRow: Long): Long = if (rowCount - startRow > Limits.HEX_WINDOW_ROWS)
        startRow + Limits.HEX_WINDOW_ROWS else startRow
    fun previousWindow(startRow: Long): Long = (startRow - Limits.HEX_WINDOW_ROWS).coerceAtLeast(0)
    fun indexFor(offset: Long): Int = (rowFor(offset) - windowStartFor(offset)).toInt()

    companion object {
        /** Parses "0x1F4", "1f4h", "$1F4" or decimal "500". Returns null when it isn't a number. */
        fun parseOffset(input: String): Long? {
            val s = input.trim().replace("_", "").replace(" ", "")
            return when {
                s.startsWith("0x", true) -> s.drop(2).toLongOrNull(16)
                s.startsWith("$") -> s.drop(1).toLongOrNull(16)
                s.endsWith("h", true) -> s.dropLast(1).toLongOrNull(16)
                s.any { it in 'a'..'f' || it in 'A'..'F' } -> s.toLongOrNull(16)
                else -> s.toLongOrNull()
            }?.takeIf { it >= 0 }
        }

        private val HEX = "0123456789ABCDEF".toCharArray()

        /** "000001F0" style offset with at least 8 digits. */
        fun formatOffset(offset: Long, digits: Int = 8): String =
            offset.toString(16).uppercase().padStart(digits, '0')

        /** "48 65 6C 6C 6F" with a wider gap after the 8th byte, padded to a full row. */
        fun formatHex(buf: ByteArray, from: Int, len: Int, bytesPerRow: Int = 16): String {
            val sb = StringBuilder(bytesPerRow * 3 + 1)
            for (i in 0 until bytesPerRow) {
                if (i == bytesPerRow / 2) sb.append(' ')
                if (i < len) {
                    val v = buf[from + i].toInt() and 0xFF
                    sb.append(HEX[v shr 4]).append(HEX[v and 0xF])
                } else {
                    sb.append("  ")
                }
                if (i < bytesPerRow - 1) sb.append(' ')
            }
            return sb.toString()
        }

        /** Printable ASCII as itself, everything else as '·'. */
        fun formatAscii(buf: ByteArray, from: Int, len: Int): String {
            val chars = CharArray(len)
            for (i in 0 until len) {
                val v = buf[from + i].toInt() and 0xFF
                chars[i] = if (v in 0x20..0x7E) v.toChar() else '·'
            }
            return String(chars)
        }
    }
}
