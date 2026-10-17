package com.fileviewer.app.viewers.misc

import com.fileviewer.app.viewers.misc.FontFixtures.NameRecord
import com.fileviewer.app.viewers.misc.font.ByteRange
import com.fileviewer.app.viewers.misc.font.CmapTable
import com.fileviewer.app.viewers.misc.font.FontInfoReader
import com.fileviewer.app.viewers.misc.font.GlyphSets
import com.fileviewer.app.viewers.misc.font.NameTable
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontParsingTest {
    private val latin = FontFixtures.format4(0x20..0x7E, 0xA0..0xFF)
    private val simpleFont = FontFixtures.font(name = FontFixtures.nameTable(*FontFixtures.englishName), cmap = FontFixtures.cmapTable(Triple(3, 1, latin)))

    @Test fun nameTableGivesFamilyStyleAndMetadata() {
        val names = NameTable.parse(FontFixtures.nameTable(*FontFixtures.englishName))
        assertEquals("Test Sans", names.family)
        assertEquals("Bold Italic", names.style)
        assertEquals("Test Sans Bold Italic", names.fullName)
        assertEquals("Version 1.002", names.version)
        assertEquals("TestSans-BoldItalic", names.postScript)
        assertEquals("Ada Lovelace", names.designer)
        assertEquals("Free to use under the OFL.", names.license)
    }

    @Test fun typographicNamesBeatLegacyOnesAndEnglishBeatsOtherLanguages() {
        val names = NameTable.parse(
            FontFixtures.nameTable(
                NameRecord(3, 1, 0x407, 1, "Schrift Fett"), NameRecord(3, 1, 0x409, 1, "Test Sans Bold"),
                NameRecord(3, 1, 0x409, 16, "Test Sans"), NameRecord(3, 1, 0x409, 17, "Bold"),
                NameRecord(1, 0, 0, 4, "Mac name"), NameRecord(0, 3, 0, 4, "Unicode name"),
            ),
        )
        assertEquals("Test Sans", names.family)
        assertEquals("Bold", names.style)
        assertEquals("Unicode name", names.fullName)
    }

    @Test fun macOnlyNamesAreDecoded() {
        val names = NameTable.parse(FontFixtures.nameTable(NameRecord(1, 0, 0, 1, "Old Mac Font")))
        assertEquals("Old Mac Font", names.family)
        assertNull(names.designer)
    }

    @Test fun truncatedNameTableDoesNotThrow() {
        val table = FontFixtures.nameTable(*FontFixtures.englishName)
        assertNull(NameTable.parse(table.copyOf(40)).designer)
        assertNull(NameTable.parse(ByteArray(0)).family)
    }

    @Test fun cmapFormat4ReportsRanges() {
        val coverage = CmapTable.parse(FontFixtures.cmapTable(Triple(3, 1, latin)))!!
        assertEquals(listOf(0x20..0x7E, 0xA0..0xFF), coverage.ranges)
        assertEquals(95L + 96L, coverage.count)
        assertTrue('A'.code in coverage)
        assertFalse(0x80 in coverage)
        assertFalse(0x100 in coverage)
    }

    @Test fun cmapFormat12ReportsAstralPlanesAndBeatsBmp() {
        val coverage = CmapTable.parse(
            FontFixtures.cmapTable(Triple(3, 1, latin), Triple(3, 10, FontFixtures.format12(0x41..0x5A, 0x1F600..0x1F64F))),
        )!!
        assertEquals(listOf(0x41..0x5A, 0x1F600..0x1F64F), coverage.ranges)
        assertTrue(0x1F600 in coverage)
        assertFalse(0x61 in coverage)
    }

    @Test fun adjacentRangesMerge() {
        val coverage = CmapTable.parse(FontFixtures.cmapTable(Triple(0, 4, FontFixtures.format12(0x41..0x45, 0x46..0x4A))))!!
        assertEquals(listOf(0x41..0x4A), coverage.ranges)
    }

    @Test fun fontWithoutUsableCmapHasNoCoverage() {
        assertNull(CmapTable.parse(FontFixtures.cmapTable(Triple(1, 0, ByteArray(8)))))
    }

    @Test fun readsInfoFromFontBytes() {
        val info = FontInfoReader.read(ByteRange.of(simpleFont))!!
        assertEquals("Test Sans", info.names.family)
        assertEquals(1, info.faceCount)
        assertEquals("TrueType outlines", info.outlines)
        assertEquals(191L, info.coverage!!.count)
    }

    @Test fun cffFlavorIsReported() {
        val otf = FontFixtures.font("OTTO", FontFixtures.nameTable(*FontFixtures.englishName), FontFixtures.cmapTable(Triple(3, 1, latin)))
        assertEquals("PostScript outlines", FontInfoReader.read(ByteRange.of(otf))!!.outlines)
    }

    @Test fun collectionReadsTheRequestedFace() {
        val other = FontFixtures.nameTable(NameRecord(3, 1, 0x409, 1, "Second Face"))
        val ttc = FontFixtures.collection(
            FontFixtures.nameTable(*FontFixtures.englishName) to FontFixtures.cmapTable(Triple(3, 1, latin)),
            other to FontFixtures.cmapTable(Triple(3, 1, FontFixtures.format4(0x41..0x5A))),
        )
        val first = FontInfoReader.read(ByteRange.of(ttc), 0)!!
        val second = FontInfoReader.read(ByteRange.of(ttc), 1)!!
        assertEquals(2, first.faceCount)
        assertEquals("Test Sans", first.names.family)
        assertEquals("Second Face", second.names.family)
        assertEquals(26L, second.coverage!!.count)
        assertNull(FontInfoReader.read(ByteRange.of(ttc), 2))
    }

    @Test fun nonFontsAreRejected() {
        assertNull(FontInfoReader.read(ByteRange.of("hello, this is text".toByteArray())))
        assertNull(FontInfoReader.read(ByteRange.of(ByteArray(0))))
        assertNull(FontInfoReader.read(ByteRange.of(simpleFont.copyOf(20))))
    }

    @Test fun glyphSectionsListOnlyCoveredCharacters() {
        val coverage = CmapTable.parse(FontFixtures.cmapTable(Triple(3, 10, FontFixtures.format12(0x20..0x7E, 0x410..0x415, 0x1F600..0x1F602))))!!
        val sections = GlyphSets.sections(coverage)
        assertEquals(listOf("Basic Latin", "Cyrillic", "More characters in this font"), sections.map { it.title })
        assertEquals(95, sections[0].codePoints.size)
        assertEquals((0x410..0x415).toList(), sections[1].codePoints)
        assertEquals(listOf(0x1F600, 0x1F601, 0x1F602), sections[2].codePoints)
    }

    @Test fun glyphSectionsFallBackToLatinWithoutACmap() {
        assertEquals(listOf("Basic Latin", "Latin-1 Supplement"), GlyphSets.sections(null).map { it.title })
    }

    @Test fun readsTheBundledVariableFont() {
        val file = File("src/main/res/font/plex_sans.ttf")
        val info = file.inputStream().use { FontInfoReader.read(ByteRange.of(it.readBytes())) }
        assertNotNull(info)
        assertTrue(info!!.names.family!!, info.names.family!!.startsWith("IBM Plex Sans"))
        assertTrue('g'.code in info.coverage!!)
        assertTrue(info.coverage!!.count > 400)
    }
}
