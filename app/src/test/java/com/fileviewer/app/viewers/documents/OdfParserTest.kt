package com.fileviewer.app.viewers.documents

import com.fileviewer.app.detect.DocumentFormat
import com.fileviewer.app.viewers.documents.DocFixtures.ODF_NS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OdfParserTest {

    private fun content(styles: String, body: String) =
        "<office:document-content $ODF_NS><office:automatic-styles>$styles</office:automatic-styles><office:body>$body</office:body></office:document-content>"

    private fun parse(format: DocumentFormat, xml: String) = DocFixtures.pkg("mimetype" to "x", "content.xml" to xml).use { OdfParser.parse(it, format) }

    @Test fun textDocumentHeadingsSpansListsTablesAndImages() {
        val styles = "<style:style style:name=\"T1\" style:family=\"text\"><style:text-properties fo:font-weight=\"bold\"/></style:style>" +
            "<text:list-style style:name=\"L1\"><text:list-level-style-number text:level=\"1\"/><text:list-level-style-bullet text:level=\"2\"/></text:list-style>"
        val body = "<office:text><text:h text:outline-level=\"2\">Section</text:h>" +
            "<text:p>Plain <text:span text:style-name=\"T1\">bold</text:span><text:s text:c=\"2\"/>end<text:note><text:note-body><text:p>footnote</text:p></text:note-body></text:note></text:p>" +
            "<text:list text:style-name=\"L1\"><text:list-item><text:p>first</text:p><text:list><text:list-item><text:p>inner</text:p></text:list-item></text:list></text:list-item></text:list>" +
            "<table:table><table:table-row><table:table-cell table:number-columns-spanned=\"2\"><text:p>cell</text:p></table:table-cell><table:covered-table-cell/></table:table-row></table:table>" +
            "<text:p><draw:frame><draw:image xlink:href=\"Pictures/a.svg\"/><draw:image xlink:href=\"Pictures/a.png\"/></draw:frame></text:p></office:text>"
        val h = (parse(DocumentFormat.ODT, content(styles, body)) as PagePreview).html
        assertTrue(h, h.contains("<h2>Section</h2>"))
        assertTrue(h, h.contains("<p>Plain <strong>bold</strong>&nbsp;&nbsp;end</p>"))
        assertTrue("notes are left out: $h", !h.contains("footnote"))
        assertTrue(h, h.contains("<ol><li>first<ul><li>inner</li></ul></li></ol>"))
        assertTrue(h, h.contains("<table><tr><td colspan=\"2\"><p>cell</p></td></tr></table>"))
        assertTrue("first image of a frame only: $h", h.contains("pkg/Pictures/a.svg") && !h.contains("a.png"))
    }

    @Test fun spreadsheetTablesBecomeSheetsAndRepeatsAreBounded() {
        val body = "<office:spreadsheet><table:table table:name=\"Totals\">" +
            "<table:table-row><table:table-cell office:value-type=\"string\"><text:p>Name</text:p></table:table-cell>" +
            "<table:table-cell office:value-type=\"float\" office:value=\"3.5\"><text:p>3.50</text:p></table:table-cell>" +
            "<table:table-cell table:number-columns-repeated=\"16380\"/></table:table-row>" +
            "<table:table-row table:number-rows-repeated=\"1048570\"><table:table-cell table:number-columns-repeated=\"16384\"/></table:table-row>" +
            "</table:table><table:table table:name=\"Empty\"/></office:spreadsheet>"
        val preview = parse(DocumentFormat.ODS, content("", body)) as SheetsPreview
        assertEquals(listOf("Totals", "Empty"), preview.sheets.map { it.name })
        val h = preview.sheets[0].html
        assertTrue(h, h.contains("<td>Name</td><td class=\"n\">3.50</td></tr>"))
        assertTrue(h, h.contains("<th>A</th><th>B</th></tr>"))
        assertTrue(!preview.sheets[0].truncated)
    }

    @Test fun presentationPagesBecomeSlides() {
        val body = "<office:presentation><draw:page draw:name=\"p1\">" +
            "<draw:frame presentation:class=\"title\"><draw:text-box><text:p>Welcome</text:p></draw:text-box></draw:frame>" +
            "<draw:frame presentation:class=\"outline\"><draw:text-box><text:list><text:list-item><text:p>Point</text:p></text:list-item></text:list></draw:text-box></draw:frame>" +
            "<presentation:notes><draw:frame><draw:text-box><text:p>speaker</text:p></draw:text-box></draw:frame></presentation:notes>" +
            "</draw:page><draw:page draw:name=\"p2\"><draw:frame><draw:image xlink:href=\"Pictures/x.jpg\"/></draw:frame></draw:page></office:presentation>"
        val h = (parse(DocumentFormat.ODP, content("", body)) as PagePreview).html
        assertTrue(h, h.contains("Slide <b>1</b> of 2</div><h2>Welcome</h2><ul><li>Point</li></ul>"))
        assertTrue(h, h.contains("Slide <b>2</b> of 2</div><figure><img src=\"pkg/Pictures/x.jpg\""))
        assertTrue(h, !h.contains("speaker"))
    }
}
