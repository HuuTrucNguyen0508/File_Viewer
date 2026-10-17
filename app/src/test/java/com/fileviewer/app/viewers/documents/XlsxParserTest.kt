package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.Limits
import com.fileviewer.app.viewers.documents.DocFixtures.NS_R
import com.fileviewer.app.viewers.documents.DocFixtures.NS_S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxParserTest {

    private fun xlsx(sheet1: String, sheet2: String = "<worksheet $NS_S><sheetData/></worksheet>", date1904: Boolean = false) = DocFixtures.pkg(
        "_rels/.rels" to DocFixtures.rootRels("xl/workbook.xml"),
        "xl/workbook.xml" to "<workbook $NS_S $NS_R><workbookPr date1904=\"${if (date1904) 1 else 0}\"/><sheets>" +
            "<sheet name=\"Budget\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Notes\" sheetId=\"2\" state=\"hidden\" r:id=\"rId2\"/></sheets></workbook>",
        "xl/_rels/workbook.xml.rels" to DocFixtures.rels(
            Triple("rId1", "worksheet", "worksheets/sheet1.xml"),
            Triple("rId2", "worksheet", "/xl/worksheets/sheet2.xml"),
            Triple("rId3", "sharedStrings", "sharedStrings.xml"),
            Triple("rId4", "styles", "styles.xml"),
        ),
        "xl/sharedStrings.xml" to "<sst $NS_S><si><t>Item</t></si><si><r><t>Rich </t></r><r><t>text</t></r><rPh><t>skip</t></rPh></si></sst>",
        "xl/styles.xml" to "<styleSheet $NS_S><numFmts><numFmt numFmtId=\"164\" formatCode=\"dd/mm/yyyy\\ hh:mm\"/></numFmts>" +
            "<cellStyleXfs><xf numFmtId=\"14\"/></cellStyleXfs>" +
            "<cellXfs><xf numFmtId=\"0\"/><xf numFmtId=\"14\"/><xf numFmtId=\"164\"/><xf numFmtId=\"10\"/><xf numFmtId=\"4\"/></cellXfs></styleSheet>",
        "xl/worksheets/sheet1.xml" to sheet1,
        "xl/worksheets/sheet2.xml" to sheet2,
    )

    private fun sheet(rows: String) = "<worksheet $NS_S><sheetData>$rows</sheetData></worksheet>"

    @Test fun valuesOfEveryCellType() {
        val rows = "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"C1\" t=\"inlineStr\"><is><t>inline</t></is></c></row>" +
            "<row r=\"3\"><c r=\"A3\"><v>42</v></c><c r=\"B3\"><v>0.30000000000000004</v></c><c r=\"C3\" t=\"b\"><v>1</v></c><c r=\"D3\" t=\"e\"><v>#DIV/0!</v></c>" +
            "<c r=\"E3\" t=\"str\"><f>A3*2</f><v>84</v></c></row>"
        val result = xlsx(sheet(rows)).use { XlsxParser.parse(it) }
        assertEquals(listOf("Budget", "Notes (hidden)"), result.sheets.map { it.name })
        val h = result.sheets[0].html
        listOf("<td>Item</td>", "<td>Rich text</td>", "<td>inline</td>", "<td class=\"n\">42</td>", "<td class=\"n\">0.3</td>", "<td>TRUE</td>", "<td>#DIV/0!</td>", "<td>84</td>")
            .forEach { assertTrue("$it in $h", h.contains(it)) }
        assertTrue(h, !h.contains("skip"))
        // Column letters A..E and the real row numbers, 1 and 3.
        assertTrue(h, h.contains("<th>A</th><th>B</th><th>C</th><th>D</th><th>E</th>"))
        assertTrue(h, h.contains("<tr><th>1</th>") && h.contains("<tr><th>3</th>") && !h.contains("<tr><th>2</th>"))
        assertTrue(result.sheets[1].html.contains("no cells"))
    }

    @Test fun datesPercentsAndFixedDecimals() {
        val rows = "<row r=\"1\"><c r=\"A1\" s=\"1\"><v>45292</v></c><c r=\"B1\" s=\"2\"><v>45292.75</v></c><c r=\"C1\" s=\"3\"><v>0.125</v></c><c r=\"D1\" s=\"4\"><v>1234567.891</v></c></row>"
        val h = xlsx(sheet(rows)).use { XlsxParser.parse(it) }.sheets[0].html
        listOf(">2024-01-01<", ">2024-01-01 18:00<", ">12.5%<", ">1,234,567.89<").forEach { assertTrue("$it in $h", h.contains(it)) }
    }

    @Test fun date1904Workbooks() {
        val h = xlsx(sheet("<row r=\"1\"><c r=\"A1\" s=\"1\"><v>0</v></c></row>"), date1904 = true).use { XlsxParser.parse(it) }.sheets[0].html
        assertTrue(h, h.contains(">1904-01-01<"))
    }

    @Test fun bigSheetsStopAtTheRowAndColumnLimits() {
        val rows = StringBuilder()
        for (r in 1..Limits.MAX_SHEET_ROWS + 10) rows.append("<row r=\"$r\"><c r=\"A$r\"><v>$r</v></c></row>")
        rows.append("<row r=\"${Limits.MAX_SHEET_ROWS + 20}\"><c r=\"ZZZ1\"><v>1</v></c></row>")
        val result = xlsx(sheet(rows.toString())).use { XlsxParser.parse(it) }
        val s = result.sheets[0]
        assertTrue(s.truncated)
        assertTrue(s.html.contains("<tr><th>${Limits.MAX_SHEET_ROWS}</th>"))
        assertTrue(!s.html.contains("<tr><th>${Limits.MAX_SHEET_ROWS + 1}</th>"))
        assertTrue(result.notes.single().contains("${Limits.MAX_SHEET_ROWS} rows"))
    }

    @Test fun formatsGeneralNumbersLikeExcel() {
        assertEquals("1E+20", XlsxFormats.general(1e20))
        assertEquals("-3", XlsxFormats.general(-3.0))
        assertEquals("0.1", XlsxFormats.general(0.1))
    }
}
