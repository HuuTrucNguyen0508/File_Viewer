package com.fileviewer.app.viewers.misc

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Builds minimal sfnt fonts with just a `name` and a `cmap` table. */
object FontFixtures {
    class NameRecord(val platform: Int, val encoding: Int, val language: Int, val id: Int, val text: String)

    fun nameTable(vararg records: NameRecord): ByteArray {
        val strings = ByteArrayOutputStream()
        val encoded = records.map { r ->
            val bytes = if (r.platform == 1) r.text.toByteArray(Charsets.ISO_8859_1) else r.text.toByteArray(Charsets.UTF_16BE)
            Triple(r, strings.size(), bytes).also { strings.write(bytes) }
        }
        val header = 6 + records.size * 12
        val buf = ByteBuffer.allocate(header + strings.size())
        buf.putShort(0).putShort(records.size.toShort()).putShort(header.toShort())
        for ((r, offset, bytes) in encoded) {
            buf.putShort(r.platform.toShort()).putShort(r.encoding.toShort()).putShort(r.language.toShort())
            buf.putShort(r.id.toShort()).putShort(bytes.size.toShort()).putShort(offset.toShort())
        }
        buf.put(strings.toByteArray())
        return buf.array()
    }

    /** A format 4 subtable mapping each given inclusive range to consecutive glyphs 1.. (idRangeOffset 0). */
    fun format4(vararg ranges: IntRange): ByteArray {
        val segs = ranges.size + 1
        val buf = ByteBuffer.allocate(16 + segs * 8)
        buf.putShort(4).putShort((16 + segs * 8).toShort()).putShort(0).putShort((segs * 2).toShort())
        buf.putShort(0).putShort(0).putShort(0)
        ranges.forEach { buf.putShort(it.last.toShort()) }
        buf.putShort(0xFFFF.toShort())
        buf.putShort(0)
        ranges.forEach { buf.putShort(it.first.toShort()) }
        buf.putShort(0xFFFF.toShort())
        ranges.forEach { buf.putShort((1 - it.first).toShort()) }
        buf.putShort(1)
        repeat(segs) { buf.putShort(0) }
        return buf.array()
    }

    fun format12(vararg ranges: IntRange): ByteArray {
        val buf = ByteBuffer.allocate(16 + ranges.size * 12)
        buf.putShort(12).putShort(0).putInt(16 + ranges.size * 12).putInt(0).putInt(ranges.size)
        var glyph = 1
        for (r in ranges) { buf.putInt(r.first).putInt(r.last).putInt(glyph); glyph += r.last - r.first + 1 }
        return buf.array()
    }

    /** A cmap with one subtable per (platform, encoding) pair. */
    fun cmapTable(vararg subtables: Triple<Int, Int, ByteArray>): ByteArray {
        val header = 4 + subtables.size * 8
        val out = ByteBuffer.allocate(header + subtables.sumOf { it.third.size })
        out.putShort(0).putShort(subtables.size.toShort())
        var offset = header
        for ((p, e, data) in subtables) { out.putShort(p.toShort()).putShort(e.toShort()).putInt(offset); offset += data.size }
        subtables.forEach { out.put(it.third) }
        return out.array()
    }

    /** Directory offsets are relative to the whole file, so [base] is where this font starts in it. */
    private fun sfnt(version: String, tables: Map<String, ByteArray>, base: Int): ByteArray {
        val sorted = tables.entries.sortedBy { it.key }
        var offset = base + 12 + sorted.size * 16
        val buf = ByteBuffer.allocate(offset - base + sorted.sumOf { (it.value.size + 3) and 3.inv() })
        buf.put(if (version == "TT") byteArrayOf(0, 1, 0, 0) else version.toByteArray(Charsets.ISO_8859_1))
        buf.putShort(sorted.size.toShort()).putShort(0).putShort(0).putShort(0)
        for ((tag, data) in sorted) {
            buf.put(tag.toByteArray(Charsets.ISO_8859_1)).putInt(0).putInt(offset).putInt(data.size)
            offset += (data.size + 3) and 3.inv()
        }
        for ((_, data) in sorted) { buf.put(data); repeat(((data.size + 3) and 3.inv()) - data.size) { buf.put(0) } }
        return buf.array()
    }

    fun font(version: String = "TT", name: ByteArray, cmap: ByteArray): ByteArray =
        sfnt(version, mapOf("name" to name, "cmap" to cmap), 0)

    /** A TTC whose faces share nothing; each face is `(name, cmap)`. */
    fun collection(vararg faces: Pair<ByteArray, ByteArray>): ByteArray {
        val header = 12 + faces.size * 4
        var base = header
        val bodies = faces.map { (name, cmap) -> sfnt("TT", mapOf("name" to name, "cmap" to cmap), base).also { base += it.size } }
        val out = ByteBuffer.allocate(base)
        out.put("ttcf".toByteArray()).putInt(0x00010000).putInt(faces.size)
        var offset = header
        for (b in bodies) { out.putInt(offset); offset += b.size }
        bodies.forEach { out.put(it) }
        return out.array()
    }

    val englishName = arrayOf(
        NameRecord(3, 1, 0x409, 1, "Test Sans"), NameRecord(3, 1, 0x409, 2, "Bold Italic"),
        NameRecord(3, 1, 0x409, 4, "Test Sans Bold Italic"), NameRecord(3, 1, 0x409, 5, "Version 1.002"),
        NameRecord(3, 1, 0x409, 6, "TestSans-BoldItalic"), NameRecord(3, 1, 0x409, 9, "Ada Lovelace"),
        NameRecord(3, 1, 0x409, 13, "Free to use under the OFL."),
    )
}
