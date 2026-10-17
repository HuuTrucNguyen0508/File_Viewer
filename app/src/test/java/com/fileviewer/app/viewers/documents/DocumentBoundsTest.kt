package com.fileviewer.app.viewers.documents

import com.fileviewer.app.detect.DocumentFormat
import com.fileviewer.app.source.Limits
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentBoundsTest {
    @Test fun repeatedOdsSheetsShareOneOutputBudget() {
        val cell = "<table:table-cell table:number-columns-repeated=\"100\"><text:p>xxxxxxxxxxxxxxxxxxxx</text:p></table:table-cell>"
        val sheet = "<table:table><table:table-row table:number-rows-repeated=\"2000\">$cell</table:table-row></table:table>"
        val xml = "<office:document-content ${DocFixtures.ODF_NS}><office:body><office:spreadsheet>${sheet.repeat(3)}</office:spreadsheet></office:body></office:document-content>"
        DocFixtures.pkg("content.xml" to xml).use { pkg ->
            val preview = OdfParser.parse(pkg, DocumentFormat.ODS) as SheetsPreview
            assertTrue(preview.sheets.sumOf { it.html.length } <= Limits.MAX_DOC_HTML_CHARS)
            assertTrue(preview.notes.isNotEmpty())
        }
    }

    @Test fun repeatedXlsxSheetsShareOneOutputBudget() {
        val book = "<workbook ${DocFixtures.NS_S} ${DocFixtures.NS_R}><sheets>" +
            (1..3).joinToString("") { "<sheet name=\"Sheet$it\" r:id=\"r1\"/>" } + "</sheets></workbook>"
        val text = "x".repeat(1_500_000)
        val sheet = "<worksheet ${DocFixtures.NS_S}><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>$text</t></is></c></row></sheetData></worksheet>"
        DocFixtures.pkg("xl/workbook.xml" to book, "xl/_rels/workbook.xml.rels" to DocFixtures.rels(Triple("r1", "worksheet", "s1.xml")), "xl/s1.xml" to sheet).use { pkg ->
            val preview = XlsxParser.parse(pkg)
            assertTrue(preview.sheets.sumOf { it.html.length } <= Limits.MAX_DOC_HTML_CHARS)
            assertTrue(CUT_NOTE in preview.notes)
        }
    }

    @Test fun expandedOdtParagraphIsRefusedBeforeGrowingWithoutBound() {
        val xml = "<office:document-content ${DocFixtures.ODF_NS}><office:body><office:text><text:p>" +
            "<text:s text:c=\"64\"/>".repeat(11_000) + "</text:p></office:text></office:body></office:document-content>"
        DocFixtures.pkg("content.xml" to xml).use { pkg ->
            val failure = runCatching { OdfParser.parse(pkg, DocumentFormat.ODT) }.exceptionOrNull()
            assertTrue(failure?.message, failure is DocumentException)
        }
    }

    @Test fun oversizedChapterTextAndSlideTitleStayWithinOutputBudget() {
        val chapter = ChapterHtml.sanitize("<p>" + "&".repeat(1_000) + "</p>", "chapter.xhtml", cap = 100)
        assertTrue(chapter.truncated)
        assertTrue(chapter.html.length <= 100)
        val deck = SlideHtml.deck(3) { _, _ -> SlideContent("&".repeat(1_000_000), "body") to false }
        assertTrue(deck.html.length <= Limits.MAX_DOC_HTML_CHARS)
        assertTrue(deck.notes.isNotEmpty())
    }
    @Test fun oversizedTextBoxRetainsPrefixAndReportsTruncation() {
        val para = "<w:p><w:r><w:t>${"x".repeat(1_000)}</w:t></w:r></w:p>"
        val xml = "<w:document ${DocFixtures.NS_W}><w:body><w:p><w:r><w:t>Before</w:t></w:r>" +
            "<w:r><w:drawing><w:txbxContent>${para.repeat(4_100)}</w:txbxContent></w:drawing></w:r>" +
            "</w:p><w:p><w:r><w:t>After</w:t></w:r></w:p></w:body></w:document>"
        DocFixtures.pkg("word/document.xml" to xml).use { pkg ->
            val preview = DocxParser.parse(pkg)
            assertTrue(preview.html.contains("Before"))
            assertTrue(preview.html.contains("xxxxxxxx"))
            assertTrue(CUT_NOTE in preview.notes)
            assertTrue(preview.html.length <= Limits.MAX_DOC_HTML_CHARS)
        }
    }

    @Test fun longLegacyTextIsKeptAsAPreview() {
        val text = "Readable legacy text ".repeat(210_000)
        val preview = LegacyText.extract(text.byteInputStream())
        assertTrue(preview.html.contains("Readable legacy text"))
        assertTrue(CUT_NOTE in preview.notes)
    }

    @Test fun multipleTextBoxesShareTheOuterOutputBudget() {
        val para = "<w:p><w:r><w:t>${"x".repeat(1_000)}</w:t></w:r></w:p>"
        val box = "<w:r><w:drawing><w:txbxContent>${para.repeat(2_500)}</w:txbxContent></w:drawing></w:r>"
        val xml = "<w:document ${DocFixtures.NS_W}><w:body><w:p>$box$box</w:p></w:body></w:document>"
        DocFixtures.pkg("word/document.xml" to xml).use { pkg ->
            val preview = DocxParser.parse(pkg)
            assertTrue(CUT_NOTE in preview.notes)
            assertTrue(preview.html.length <= Limits.MAX_DOC_HTML_CHARS)
        }
    }

}
