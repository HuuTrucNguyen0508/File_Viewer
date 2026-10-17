package com.fileviewer.app.viewers.misc.font

import com.fileviewer.app.source.Limits
import java.io.InterruptedIOException

/** The code points a font has glyphs for, as sorted, non-adjacent ranges. */
class Coverage(val ranges: List<IntRange>) {
    val count: Long = ranges.sumOf { (it.last - it.first + 1).toLong() }

    operator fun contains(cp: Int): Boolean {
        var lo = 0
        var hi = ranges.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val r = ranges[mid]
            when {
                cp < r.first -> hi = mid - 1
                cp > r.last -> lo = mid + 1
                else -> return true
            }
        }
        return false
    }

    /** Covered code points in [range], at most [limit]. */
    fun within(range: IntRange, limit: Int): List<Int> {
        val out = ArrayList<Int>()
        for (r in ranges) {
            if (r.last < range.first) continue
            if (r.first > range.last) break
            for (cp in maxOf(r.first, range.first)..minOf(r.last, range.last)) {
                if (out.size >= limit) return out
                out.add(cp)
            }
        }
        return out
    }

    /** Covered code points outside every one of [excluded], at most [limit]. */
    fun outside(excluded: List<IntRange>, limit: Int): List<Int> {
        val out = ArrayList<Int>()
        for (r in ranges) for (cp in r) {
            if (out.size >= limit) return out
            if (excluded.none { cp in it }) out.add(cp)
        }
        return out
    }
}

/** Reads the best Unicode subtable (format 4 or 12) of a `cmap` table. */
object CmapTable {
    private class Subtable(val offset: Int, val format: Int, val score: Int)

    fun parse(table: ByteArray): Coverage? {
        val count = table.u16(2)
        val best = (0 until count).mapNotNull { i ->
            val platform = table.u16(4 + i * 8)
            val encoding = table.u16(6 + i * 8)
            val offset = table.u32(8 + i * 8)
            if (platform < 0 || offset !in 0 until table.size) return@mapNotNull null
            val score = when {
                platform == 3 && encoding == 10 || platform == 0 && encoding >= 4 -> 5
                platform == 3 && encoding == 1 || platform == 0 && encoding == 3 -> 4
                platform == 0 -> 3
                platform == 3 && encoding == 0 -> 1
                else -> return@mapNotNull null
            }
            val at = offset.toInt()
            Subtable(at, table.u16(at), score).takeIf { it.format == 4 || it.format == 12 }
        }.maxByOrNull { it.score } ?: return null
        val ranges = if (best.format == 4) format4(table, best.offset) else format12(table, best.offset)
        return ranges?.let { Coverage(merge(it)) }
    }

    private fun format4(t: ByteArray, at: Int): List<IntRange>? {
        val segments = t.u16(at + 6) / 2
        if (segments <= 0 || at + 16 + segments * 8 > t.size) return null
        val ends = at + 14
        val starts = at + 16 + segments * 2
        val deltas = at + 16 + segments * 4
        val offsets = at + 16 + segments * 6
        val out = ArrayList<IntRange>()
        var previousEnd = -1
        for (i in 0 until segments) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Font parse cancelled")
            val start = t.u16(starts + i * 2)
            val end = t.u16(ends + i * 2)
            if (end < start || start <= previousEnd) return null
            previousEnd = end
            if (start == 0xFFFF) continue
            val rangeOffset = t.u16(offsets + i * 2)
            val delta = t.u16(deltas + i * 2)
            var runStart = -1
            for (cp in start..end) {
                val glyph = if (rangeOffset == 0) (cp + delta) and 0xFFFF else {
                    val g = t.u16(offsets + i * 2 + rangeOffset + (cp - start) * 2)
                    if (g <= 0) 0 else (g + delta) and 0xFFFF
                }
                if (glyph != 0 && runStart < 0) runStart = cp
                if (glyph == 0 && runStart >= 0) { out.add(runStart until cp); runStart = -1 }
            }
            if (runStart >= 0) out.add(runStart..end)
            if (out.size > Limits.MAX_COVERAGE_RANGES) break
        }
        return out
    }

    private fun format12(t: ByteArray, at: Int): List<IntRange>? {
        val groups = t.u32(at + 12)
        if (groups < 0) return null
        val available = (t.size - at - 16) / 12
        val n = minOf(groups, available.toLong(), Limits.MAX_COVERAGE_RANGES.toLong()).toInt()
        val out = ArrayList<IntRange>()
        for (i in 0 until n) {
            val start = t.u32(at + 16 + i * 12)
            val end = t.u32(at + 20 + i * 12)
            if (start in 0..end && end <= 0x10FFFF) out.add(start.toInt()..end.toInt())
        }
        return out
    }

    private fun merge(ranges: List<IntRange>): List<IntRange> {
        val out = ArrayList<IntRange>()
        for (r in ranges.sortedBy { it.first }) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last + 1) out[out.lastIndex] = last.first..maxOf(last.last, r.last) else out.add(r)
        }
        return out
    }
}
