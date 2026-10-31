package com.fileviewer.app.viewers.misc.font

import com.fileviewer.app.source.Limits

/** Human-readable strings from a font's `name` table. */
data class FontNames(
    val family: String?,
    val style: String?,
    val fullName: String?,
    val version: String?,
    val postScript: String?,
    val designer: String?,
    val license: String?,
    val copyright: String?,
)

object NameTable {
    private const val COPYRIGHT = 0
    private const val FAMILY = 1
    private const val STYLE = 2
    private const val FULL_NAME = 4
    private const val VERSION = 5
    private const val POSTSCRIPT = 6
    private const val DESIGNER = 9
    private const val LICENSE = 13
    private const val TYPOGRAPHIC_FAMILY = 16
    private const val TYPOGRAPHIC_STYLE = 17

    private class Record(val platform: Int, val language: Int, val offset: Int, val length: Int)

    fun parse(table: ByteArray): FontNames {
        val count = table.u16(2)
        val storage = table.u16(4)
        val records = HashMap<Int, Record>()
        fun rank(r: Record) = when {
            r.platform == 3 && r.language == 0x409 -> 0
            r.platform == 3 -> 1
            r.platform == 0 -> 2
            else -> 3
        }
        val displayed = setOf(COPYRIGHT, FAMILY, STYLE, FULL_NAME, VERSION, POSTSCRIPT,
            DESIGNER, LICENSE, TYPOGRAPHIC_FAMILY, TYPOGRAPHIC_STYLE)
        for (i in 0 until count) {
            val at = 6 + i * 12
            if (at + 12 > table.size) break
            val id = table.u16(at + 6)
            val platform = table.u16(at)
            if (id !in displayed || platform !in setOf(0, 1, 3)) continue
            val length = table.u16(at + 8)
            val offset = storage + table.u16(at + 10)
            if (length <= 0 || offset < 0 || offset + length > table.size) continue
            val r = Record(platform, table.u16(at + 4), offset, length)
            if (records[id]?.let { rank(it) <= rank(r) } != true) records[id] = r
        }
        fun pick(vararg ids: Int): String? {
            val r = ids.asList().firstNotNullOfOrNull { records[it] } ?: return null
            val unicode = r.platform != 1
            val length = minOf(r.length, Limits.MAX_FONT_NAME_CHARS * if (unicode) 2 else 1)
            return String(table, r.offset, length, if (unicode) Charsets.UTF_16BE else Charsets.ISO_8859_1)
                .replace("\u0000", "").trim().takeIf { it.isNotEmpty() }
        }
        return FontNames(
            family = pick(TYPOGRAPHIC_FAMILY, FAMILY),
            style = pick(TYPOGRAPHIC_STYLE, STYLE),
            fullName = pick(FULL_NAME),
            version = pick(VERSION),
            postScript = pick(POSTSCRIPT),
            designer = pick(DESIGNER),
            license = pick(LICENSE),
            copyright = pick(COPYRIGHT),
        )
    }
}
