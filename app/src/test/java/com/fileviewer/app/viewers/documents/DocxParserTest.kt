package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.LimitExceededException
import com.fileviewer.app.source.Limits
import com.fileviewer.app.viewers.documents.DocFixtures.NS_A
import com.fileviewer.app.viewers.documents.DocFixtures.NS_R
import com.fileviewer.app.viewers.documents.DocFixtures.NS_W
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxParserTest {

    private fun docx(body: String): DocPackage = DocFixtures.pkg(
        "_rels/.rels" to DocFixtures.rootRels("word/document.xml"),
        "word/_rels/document.xml.rels" to DocFixtures.rels(
            Triple("rIdS", "styles", "styles.xml"),
            Triple("rIdN", "numbering", "numbering.xml"),
            Triple("rIdImg", "image", "media/image1.png"),
            Triple("rIdEmf", "image", "media/chart.emf"),
            Triple("rIdEvil", "image", "../../../etc/passwd.png"),
            Triple("rIdLink", "hyperlink", "https://example.com/page"),
        ),
        "word/styles.xml" to "<w:styles $NS_W>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Titre1\"><w:name w:val=\"heading 1\"/></w:style>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Sub\"><w:name w:val=\"My sub\"/><w:basedOn w:val=\"Lvl2\"/></w:style>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Lvl2\"><w:name w:val=\"Level\"/><w:pPr><w:outlineLvl w:val=\"1\"/></w:pPr></w:style>" +
            "</w:styles>",
        "word/numbering.xml" to "<w:numbering $NS_W>" +
            "<w:abstractNum w:abstractNumId=\"0\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"bullet\"/></w:lvl></w:abstractNum>" +
            "<w:abstractNum w:abstractNumId=\"1\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"decimal\"/></w:lvl></w:abstractNum>" +
            "<w:num w:numId=\"5\"><w:abstractNumId w:val=\"0\"/></w:num><w:num w:numId=\"6\"><w:abstractNumId w:val=\"1\"/></w:num>" +
            "</w:numbering>",
        "word/document.xml" to "<w:document $NS_W $NS_R $NS_A xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\"><w:body>$body</w:body></w:document>",
        "word/media/image1.png" to "png",
    )

    private fun p(text: String, pPr: String = "", rPr: String = "") = "<w:p><w:pPr>$pPr</w:pPr><w:r><w:rPr>$rPr</w:rPr><w:t xml:space=\"preserve\">$text</w:t></w:r></w:p>"

    private fun html(body: String) = docx(body).use { DocxParser.parse(it).html }

    @Test fun headingsComeFromStyleNamesOutlineLevelsAndInheritance() {
        val h = html(p("Intro", "<w:pStyle w:val=\"Titre1\"/>") + p("Detail", "<w:pStyle w:val=\"Sub\"/>") + p("Body text"))
        assertEquals("<h1>Intro</h1><h2>Detail</h2><p>Body text</p>", h)
    }

    @Test fun runFormatting() {
        val h = html(p("bold", rPr = "<w:b/>") + p("not bold", rPr = "<w:b w:val=\"0\"/>") + p("it", rPr = "<w:i/><w:u w:val=\"single\"/>"))
        assertEquals("<p><strong>bold</strong></p><p>not bold</p><p><em><u>it</u></em></p>", h)
    }

    @Test fun bulletsAndNumberedListsNest() {
        val item = { t: String, num: Int, lvl: Int -> p(t, "<w:numPr><w:ilvl w:val=\"$lvl\"/><w:numId w:val=\"$num\"/></w:numPr>") }
        val h = html(item("a", 5, 0) + item("b", 5, 0) + p("between") + item("one", 6, 0) + item("nested", 5, 1))
        assertEquals("<ul><li>a</li><li>b</li></ul><p>between</p><ol><li>one<ul><li>nested</li></ul></li></ol>", h)
    }

    @Test fun tablesWithMergedColumns() {
        val tbl = "<w:tbl><w:tr><w:tc><w:tcPr><w:gridSpan w:val=\"2\"/></w:tcPr>${p("wide")}</w:tc></w:tr>" +
            "<w:tr><w:tc>${p("x")}</w:tc><w:tc>${p("y")}</w:tc></w:tr></w:tbl>"
        assertEquals("<table><tr><td colspan=\"2\"><p>wide</p></td></tr><tr><td><p>x</p></td><td><p>y</p></td></tr></table>", html(tbl))
    }

    @Test fun hyperlinksStayTextAndImagesPointIntoThePackage() {
        val link = "<w:p><w:hyperlink r:id=\"rIdLink\"><w:r><w:t>site</w:t></w:r></w:hyperlink></w:p>"
        val drawing = { id: String -> "<w:p><w:r><w:drawing><wp:docPr xmlns:wp=\"urn:wp\" descr=\"A chart\"/><a:graphic><a:blip r:embed=\"$id\"/></a:graphic></w:drawing></w:r></w:p>" }
        val fallback = "<w:p><mc:AlternateContent><mc:Choice Requires=\"wps\"><w:r><w:t>choice</w:t></w:r></mc:Choice><mc:Fallback><w:r><w:t>fallback</w:t></w:r></mc:Fallback></mc:AlternateContent></w:p>"
        val h = html(link + drawing("rIdImg") + drawing("rIdEmf") + drawing("rIdEvil") + fallback)
        assertTrue(h, h.contains("<span class=\"link\" title=\"https://example.com/page\">site</span>"))
        assertTrue(h, h.contains("<img src=\"pkg/word/media/image1.png\" alt=\"A chart\">"))
        assertTrue(h, h.contains("EMF image not shown"))
        assertFalse("traversing target must not resolve: $h", h.contains("passwd"))
        assertTrue(h, h.contains("choice") && !h.contains("fallback"))
    }

    @Test fun doctypeAndExternalEntitiesAreRefused() {
        val evil = DocFixtures.pkg(
            "word/document.xml" to "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/hostname\">]><w:document $NS_W><w:body><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:body></w:document>",
        )
        val e = assertThrows(DocumentException::class.java) { evil.use { DocxParser.parse(it) } }
        assertTrue(e.message, e.message!!.contains("DOCTYPE", ignoreCase = true))
    }

    @Test fun longDocumentsAreCutWithANote() {
        val para = p("x".repeat(1000))
        val body = StringBuilder().apply { repeat(Limits.MAX_DOC_HTML_CHARS / 1000 + 50) { append(para) } }.toString()
        val preview = docx(body).use { DocxParser.parse(it) }
        assertTrue(preview.html.length <= Limits.MAX_DOC_HTML_CHARS + 2000)
        assertEquals(listOf(CUT_NOTE), preview.notes)
    }

    @Test fun oversizedPartHitsThePartLimit() {
        val huge = ByteArray((Limits.MAX_DOC_PART_BYTES + 1024).toInt()) { ' '.code.toByte() }
        val prefix = "<w:document $NS_W><w:body>".toByteArray()
        System.arraycopy(prefix, 0, huge, 0, prefix.size)
        val pkg = DocFixtures.pkgOf(mapOf("word/document.xml" to huge))
        assertThrows(LimitExceededException::class.java) { pkg.use { DocxParser.parse(it) } }
    }
}
