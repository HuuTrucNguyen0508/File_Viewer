package com.fileviewer.app.viewers.code

import com.fileviewer.app.detect.TextSniffer
import com.fileviewer.app.source.Limits
import com.fileviewer.app.source.SeekableSource
import com.fileviewer.app.source.readFully
import java.nio.charset.Charset

/**
 * Splits a large text file into chunks of about [Limits.PAGED_TEXT_CHUNK_BYTES], each ending
 * just after a newline when one is near, so chunks decode independently. Pure; reads only
 * a small window per chunk boundary.
 */
object PagedText {

    fun unitSize(cs: Charset): Int = when (cs) {
        Charsets.UTF_16LE, Charsets.UTF_16BE, Charsets.UTF_16 -> 2
        TextSniffer.UTF_32LE, TextSniffer.UTF_32BE -> 4
        else -> 1
    }

    /** Chunk start offsets; the last chunk ends at the file size. */
    fun chunkStarts(src: SeekableSource, cs: Charset, chunk: Int = Limits.PAGED_TEXT_CHUNK_BYTES): LongArray {
        val unit = unitSize(cs)
        val newline = "\n".toByteArray(cs).let { if (it.size > unit) it.copyOfRange(it.size - unit, it.size) else it }
        val starts = ArrayList<Long>()
        val window = ByteArray(4096)
        var pos = TextSniffer.bom(ByteArray(4).also { src.readFully(0, it) }, minOf(4, src.size.toInt()))
            ?.takeIf { it.first == cs }?.second?.toLong() ?: 0L
        while (pos < src.size) {
            starts += pos
            val target = alignDown(pos + chunk, unit, pos)
            if (target >= src.size) break
            val n = src.readFully(target, window)
            var cut = -1
            var i = 0
            while (i + newline.size <= n) {
                if ((0 until newline.size).all { window[i + it] == newline[it] }) { cut = i + newline.size; break }
                i += unit
            }
            pos = if (cut >= 0) target + cut else if (unit == 1) backOffContinuation(src, target, pos) else target
        }
        if (starts.isEmpty()) starts += 0L
        return starts.toLongArray()
    }

    private fun alignDown(offset: Long, unit: Int, base: Long): Long = base + ((offset - base) / unit) * unit

    /** Moves a cut back off UTF-8 continuation bytes so no character is split. */
    private fun backOffContinuation(src: SeekableSource, at: Long, floor: Long): Long {
        val b = ByteArray(4)
        val from = maxOf(floor + 1, at - 3)
        val n = src.readFully(from, b, (at - from).toInt() + 1)
        var cut = at
        var idx = n - 1
        while (idx >= 0 && cut > floor + 1 && (b[idx].toInt() and 0xC0) == 0x80) { cut--; idx-- }
        return cut
    }
}
