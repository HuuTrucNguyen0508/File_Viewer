package com.fileviewer.app.viewers.misc.font

import com.fileviewer.app.source.SeekableSource
import com.fileviewer.app.source.readFully

/** Positional byte access, so parsers read only the tables they need from a file of any size. */
fun interface ByteRange {
    /** Up to [length] bytes at [position]; shorter (or empty) at the end of the data. */
    fun read(position: Long, length: Int): ByteArray

    companion object {
        fun of(bytes: ByteArray) = ByteRange { pos, len ->
            if (pos < 0 || pos >= bytes.size) ByteArray(0) else bytes.copyOfRange(pos.toInt(), minOf(bytes.size.toLong(), pos + len).toInt())
        }

        fun of(source: SeekableSource) = ByteRange { pos, len ->
            val buf = ByteArray(len.coerceAtLeast(0))
            val n = if (pos < 0) 0 else source.readFully(pos, buf)
            if (n == buf.size) buf else buf.copyOf(n)
        }
    }
}

/** Big-endian reads that return -1 instead of throwing when the table is short. */
internal fun ByteArray.u16(at: Int): Int = if (at < 0 || at + 2 > size) -1 else ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

internal fun ByteArray.u32(at: Int): Long = if (at < 0 || at + 4 > size) -1 else (u16(at).toLong() shl 16) or u16(at + 2).toLong()

internal fun ByteArray.tag(at: Int): String = if (at < 0 || at + 4 > size) "" else String(CharArray(4) { (this[at + it].toInt() and 0xFF).toChar() })

/** An opened sfnt face (TrueType, OpenType, or one face of a collection): its table directory. */
class Sfnt private constructor(
    private val range: ByteRange,
    /** Faces in the file; 1 unless it is a TTC. */
    val faceCount: Int,
    /** `\0\1\0\0` for TrueType outlines, `OTTO` for CFF. */
    val flavor: String,
    private val tables: Map<String, Pair<Long, Long>>,
) {
    fun has(tag: String) = tag in tables

    /** The table's bytes, truncated to [maxBytes]; null when the font has no such table. */
    fun table(tag: String, maxBytes: Int): ByteArray? {
        val (offset, length) = tables[tag] ?: return null
        val bytes = range.read(offset, minOf(length, maxBytes.toLong()).toInt())
        return bytes.takeIf { it.isNotEmpty() }
    }

    companion object {
        private val FLAVORS = setOf("\u0000\u0001\u0000\u0000", "OTTO", "true", "typ1")
        private const val MAX_TABLES = 256
        private const val MAX_FACES = 1024

        /** Null when the data isn't an sfnt font, or [face] is out of range. */
        fun open(range: ByteRange, face: Int = 0): Sfnt? {
            val head = range.read(0, 12)
            var directory = 0L
            var faces = 1
            if (head.tag(0) == "ttcf") {
                faces = head.u32(8).coerceIn(0L, MAX_FACES.toLong()).toInt()
                if (face !in 0 until faces) return null
                val offsets = range.read(12L + 4L * face, 4)
                directory = offsets.u32(0).takeIf { it >= 0 } ?: return null
            } else if (face != 0) return null
            val header = range.read(directory, 12)
            val flavor = header.tag(0)
            if (flavor !in FLAVORS) return null
            val count = header.u16(4)
            if (count !in 1..MAX_TABLES) return null
            val records = range.read(directory + 12, count * 16)
            if (records.size < count * 16) return null
            val tables = HashMap<String, Pair<Long, Long>>()
            for (i in 0 until count) tables.putIfAbsent(records.tag(i * 16), records.u32(i * 16 + 8) to records.u32(i * 16 + 12))
            return Sfnt(range, faces, flavor, tables)
        }
    }
}
