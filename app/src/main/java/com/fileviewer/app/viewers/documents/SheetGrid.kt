package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.Limits

data class GridCell(val text: String, val numeric: Boolean)

/**
 * The visible part of one sheet: up to [Limits.MAX_SHEET_ROWS] non-empty rows and
 * [Limits.MAX_SHEET_COLS] columns, keyed by their real row and column numbers.
 */
class SheetGrid {
    private val rows = ArrayList<Pair<Int, HashMap<Int, GridCell>>>()
    private var maxCol = -1

    /** True when rows or columns past the limits were dropped. */
    var truncated = false

    val rowCount get() = rows.size

    /** Records a cell; [row] is 1-based, [col] 0-based. Returns false once the row limit is reached. */
    fun set(row: Int, col: Int, cell: GridCell): Boolean {
        if (cell.text.isEmpty()) return true
        if (col >= Limits.MAX_SHEET_COLS) { truncated = true; return true }
        val last = rows.lastOrNull()
        val cells = if (last != null && last.first == row) last.second else {
            if (rows.size >= Limits.MAX_SHEET_ROWS) { truncated = true; return false }
            HashMap<Int, GridCell>().also { rows += row to it }
        }
        cells[col] = cell
        if (col > maxCol) maxCol = col
        return true
    }

    fun toHtml(): String {
        if (rows.isEmpty()) return "<p class=\"empty\">This sheet has no cells with values.</p>"
        val out = HtmlOut()
        try {
            out.raw("<table class=\"sheet\"><thead><tr><th class=\"corner\"></th>")
            for (c in 0..maxCol) out.raw("<th>${columnName(c)}</th>")
            out.raw("</tr></thead><tbody>")
            for ((n, cells) in rows) {
                out.raw("<tr><th>$n</th>")
                for (c in 0..maxCol) {
                    val cell = cells[c]
                    out.raw(if (cell == null) "<td></td>" else if (cell.numeric) "<td class=\"n\">${esc(cell.text)}</td>" else "<td>${esc(cell.text)}</td>")
                }
                out.raw("</tr>")
            }
            out.raw("</tbody></table>")
        } catch (_: StopParsing) {
            truncated = true
        }
        return out.finish()
    }

    companion object {
        /** 0 -> A, 25 -> Z, 26 -> AA. */
        fun columnName(index: Int): String {
            var n = index + 1
            val sb = StringBuilder()
            while (n > 0) {
                val r = (n - 1) % 26
                sb.append('A' + r)
                n = (n - 1) / 26
            }
            return sb.reverse().toString()
        }

        /** "AB12" -> 27; null when the reference has no column letters. */
        fun columnIndex(ref: String): Int? {
            var n = 0
            var i = 0
            while (i < ref.length && ref[i].isLetter()) {
                n = n * 26 + (ref[i].uppercaseChar() - 'A' + 1)
                if (n > 1_000_000) return null
                i++
            }
            return if (i == 0) null else n - 1
        }

        /** "AB12" -> 12. */
        fun rowNumber(ref: String): Int? = ref.dropWhile { it.isLetter() }.toIntOrNull()
    }
}
