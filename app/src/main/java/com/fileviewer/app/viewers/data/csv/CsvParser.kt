package com.fileviewer.app.viewers.data.csv

import com.fileviewer.app.viewers.data.DataLimits

/** Parsed delimited text. Rows can be ragged; [columns] is the widest row, capped at the column limit. */
class CsvTable(
    val rows: List<List<String>>,
    val columns: Int,
    val delimiter: Char,
    val truncatedRows: Boolean,
    val truncatedColumns: Boolean,
    val truncatedCells: Boolean,
)

/**
 * RFC 4180 reader: quoted fields, doubled quotes, embedded delimiters and line breaks, CRLF, LF or CR.
 * Lenient on the edges: a quote inside an unquoted field is literal, text after a closing quote is kept,
 * blank lines are skipped, and an unterminated quote runs to the end of the text.
 */
object CsvParser {

    fun parse(
        text: String,
        delimiter: Char,
        maxRows: Int = DataLimits.MAX_TABLE_ROWS,
        maxCols: Int = DataLimits.MAX_TABLE_COLS,
        maxCell: Int = DataLimits.MAX_CELL_CHARS,
    ): CsvTable {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var cellChars = 0
        var widest = 0
        var truncRows = false
        var truncCols = false
        var truncCells = false
        var inQuotes = false
        var afterQuote = false
        var rowHasContent = false

        fun endCell() {
            if (cellChars > maxCell) cell.append('\u2026')
            if (row.size < maxCols) row += cell.toString() else truncCols = true
            cell.setLength(0)
            cellChars = 0
            afterQuote = false
        }

        fun endRow(): Boolean {
            if (!rowHasContent && row.isEmpty() && cell.isEmpty()) return true
            if (rows.size >= maxRows) { truncRows = true; return false }
            endCell()
            rows += row
            widest = maxOf(widest, row.size)
            row = ArrayList()
            rowHasContent = false
            return true
        }

        fun append(c: Char) {
            if (cellChars < maxCell) cell.append(c) else truncCells = true
            cellChars++
        }

        var i = 0
        loop@ while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { append('"'); i++ } else { inQuotes = false; afterQuote = true }
                } else append(c)
            } else when {
                c == '"' && cell.isEmpty() && cellChars == 0 && !afterQuote -> { inQuotes = true; rowHasContent = true }
                c == delimiter -> { rowHasContent = true; endCell() }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    if (!endRow()) break@loop
                }
                else -> { rowHasContent = true; append(c) }
            }
            i++
        }
        if (!truncRows && (rowHasContent || cell.isNotEmpty() || row.isNotEmpty())) endRow()
        return CsvTable(rows, widest, delimiter, truncRows, truncCols, truncCells)
    }
}
