package com.fileviewer.app.viewers.misc.font

import com.fileviewer.app.viewers.misc.MiscLimits

/** What the specimen header and glyph grid need from a font file. */
data class FontInfo(
    val names: FontNames,
    /** Null when the font has no readable Unicode cmap. */
    val coverage: Coverage?,
    val faceCount: Int,
    val flavor: String,
) {
    val outlines: String get() = if (flavor == "OTTO") "PostScript outlines" else "TrueType outlines"
}

object FontInfoReader {
    /** Null when [range] isn't an sfnt font. Reads the table directory, `name` and `cmap` only. */
    fun read(range: ByteRange, face: Int = 0): FontInfo? {
        val sfnt = Sfnt.open(range, face) ?: return null
        val names = sfnt.table("name", MiscLimits.MAX_NAME_TABLE_BYTES)?.let(NameTable::parse)
            ?: FontNames(null, null, null, null, null, null, null, null)
        val coverage = sfnt.table("cmap", MiscLimits.MAX_CMAP_TABLE_BYTES)?.let(CmapTable::parse)
        return FontInfo(names, coverage, sfnt.faceCount, sfnt.flavor)
    }
}

/** A titled run of code points shown as one block of the glyph grid. */
class GlyphSection(val title: String, val codePoints: List<Int>)

object GlyphSets {
    private val BLOCKS = listOf(
        "Basic Latin" to (0x20..0x7E),
        "Latin-1 Supplement" to (0xA0..0xFF),
        "Latin Extended-A" to (0x100..0x17F),
        "Greek and Coptic" to (0x370..0x3FF),
        "Cyrillic" to (0x400..0x4FF),
        "Punctuation" to (0x2000..0x206F),
        "Currency" to (0x20A0..0x20CF),
        "Arrows" to (0x2190..0x21FF),
        "Mathematical operators" to (0x2200..0x22FF),
    )

    /** Blocks the font covers, then up to [MiscLimits.MAX_OTHER_GLYPHS] more characters it reports. */
    fun sections(coverage: Coverage?): List<GlyphSection> {
        if (coverage == null) {
            return BLOCKS.take(2).map { (title, r) -> GlyphSection(title, r.toList()) }
        }
        val named = BLOCKS.mapNotNull { (title, r) ->
            coverage.within(r, MiscLimits.MAX_GLYPHS_PER_SECTION).takeIf { it.isNotEmpty() }?.let { GlyphSection(title, it) }
        }
        val other = coverage.outside(BLOCKS.map { it.second } + listOf(0..0x1F, 0x7F..0x9F), MiscLimits.MAX_OTHER_GLYPHS)
        return if (other.isEmpty()) named else named + GlyphSection("More characters in this font", other)
    }
}
