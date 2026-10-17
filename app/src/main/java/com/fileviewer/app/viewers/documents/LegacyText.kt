package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.Limits
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InterruptedIOException

/**
 * Best-effort text from legacy binary Office files (DOC, XLS, PPT): runs of readable
 * characters in single-byte (Windows-1252) or UTF-16LE form, in file order. Formatting,
 * numbers stored as binary and CJK text are not recovered; stray names from the file
 * structure can appear.
 */
object LegacyText {
    private const val MIN_8BIT = 6
    private const val MIN_16BIT = 4

    private val NOISE = setOf(
        "Root Entry", "WordDocument", "SummaryInformation", "DocumentSummaryInformation", "CompObj", "1Table", "0Table",
        "Workbook", "Book", "PowerPoint Document", "Current User", "Pictures", "ObjectPool", "Data",
    )

    private class Run(val offset: Long, val text: String)

    fun extract(input: InputStream): PagePreview {
        val runs = ArrayList<Run>()
        var kept = 0L
        val single = StringBuilder()
        var singleStart = 0L
        val wide = arrayOf(StringBuilder(), StringBuilder())
        val wideStart = longArrayOf(0, 0)
        val low = IntArray(2) { -1 }

        fun keep(offset: Long, sb: StringBuilder, min: Int) {
            if (sb.length >= min) {
                val t = sb.toString().trim()
                if (t.length >= min && t !in NOISE && readable(t, ascii = min == MIN_8BIT)) { runs += Run(offset, t); kept += t.length }
            }
            sb.setLength(0)
        }

        val buf = BufferedInputStream(input, 64 * 1024)
        var pos = 0L
        while (pos < Limits.MAX_LEGACY_SCAN_BYTES && kept < Limits.MAX_DOC_HTML_CHARS / 2) {
            if (pos and 0xFFFF == 0L && Thread.currentThread().isInterrupted) throw InterruptedIOException("Scan cancelled")
            val b = buf.read()
            if (b < 0) break
            if (singleByte(b)) {
                if (single.isEmpty()) singleStart = pos
                single.append(cp1252(b))
            } else {
                keep(singleStart, single, MIN_8BIT)
            }
            val lane = (pos and 1).toInt()
            // A UTF-16 unit completes on every second byte of its lane.
            val other = 1 - lane
            if (low[other] >= 0) {
                val ch = (low[other] or (b shl 8)).toChar()
                if (wideChar(ch)) {
                    if (wide[other].isEmpty()) wideStart[other] = pos - 1
                    wide[other].append(ch)
                } else {
                    keep(wideStart[other], wide[other], MIN_16BIT)
                }
                low[other] = -1
            }
            low[lane] = b
            pos++
        }
        keep(singleStart, single, MIN_8BIT)
        for (l in 0..1) keep(wideStart[l], wide[l], MIN_16BIT)

        val out = HtmlOut()
        try {
            var last: String? = null
            for (run in runs.sortedBy { it.offset }) {
                for (line in run.text.split('\r', '\n', '\u000B', '\u000C')) {
                    val t = line.trim()
                    if (t.isEmpty() || t == last) continue
                    out.raw("<p>${esc(t)}</p>")
                    last = t
                }
            }
        } catch (_: StopParsing) {
        }
        val html = out.finish().ifEmpty { "<p class=\"empty\">No readable text was found in this file.</p>" }
        return PagePreview(html, PageKind.ExtractedText, listOfNotNull(CUT_NOTE.takeIf { out.truncated || pos >= Limits.MAX_LEGACY_SCAN_BYTES }))
    }

    private fun singleByte(b: Int) = b in 0x20..0x7E || b == 0x09 || b == 0x0D || b == 0x0A || b in 0xC0..0xFF || b == 0x92 || b == 0x93 || b == 0x94 || b == 0x96

    private fun cp1252(b: Int): Char = when (b) {
        0x92 -> '’'; 0x93 -> '“'; 0x94 -> '”'; 0x96 -> '–'
        else -> b.toChar()
    }

    /** Latin, Greek, Cyrillic, Hebrew, Arabic and general punctuation; CJK is left out because binary data mimics it. */
    private fun wideChar(c: Char): Boolean = c.code in 0x20..0x7E || c == '\r' || c == '\n' || c == '\t' ||
        c.code in 0xA0..0x017F || c.code in 0x0370..0x04FF || c.code in 0x0590..0x06FF || c.code in 0x2010..0x2027

    /**
     * Mostly letters. Single-byte runs must also be mostly ASCII letters: binary data is full of
     * bytes that happen to be accented Latin-1 letters.
     */
    private fun readable(t: String, ascii: Boolean): Boolean {
        var letters = 0
        var asciiLetters = 0
        var other = 0
        for (c in t) when {
            c.isLetter() -> { letters++; if (c < '\u0080') asciiLetters++ }
            c.isWhitespace() -> {}
            else -> other++
        }
        return letters >= 3 && letters >= other * 2 && (!ascii || asciiLetters * 4 >= letters * 3)
    }
}
