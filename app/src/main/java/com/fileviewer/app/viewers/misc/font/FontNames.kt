package com.fileviewer.app.viewers.misc.font

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

    private class Record(val platform: Int, val language: Int, val id: Int, val text: String)

    fun parse(table: ByteArray): FontNames {
        val count = table.u16(2)
        val storage = table.u16(4)
        val records = buildList {
            for (i in 0 until count) {
                val at = 6 + i * 12
                val length = table.u16(at + 8)
                val offset = storage + table.u16(at + 10)
                if (at + 12 > table.size || length < 0 || offset < 0 || offset + length > table.size) continue
                val platform = table.u16(at)
                val charset = when (platform) {
                    0, 3 -> Charsets.UTF_16BE
                    1 -> Charsets.ISO_8859_1
                    else -> continue
                }
                val text = String(table, offset, length, charset).replace("\u0000", "").trim()
                if (text.isNotEmpty()) add(Record(platform, table.u16(at + 4), table.u16(at + 6), text))
            }
        }

        /** Windows English first, then any Windows string, Unicode platform, and Mac Roman last. */
        fun rank(r: Record) = when {
            r.platform == 3 && r.language == 0x409 -> 0
            r.platform == 3 -> 1
            r.platform == 0 -> 2
            else -> 3
        }

        fun pick(vararg ids: Int): String? = ids.asList().firstNotNullOfOrNull { id -> records.filter { it.id == id }.minByOrNull(::rank)?.text }
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
