package com.fileviewer.app.viewers.documents

import com.fileviewer.app.viewers.documents.DocFixtures.NS_A
import com.fileviewer.app.viewers.documents.DocFixtures.NS_P
import com.fileviewer.app.viewers.documents.DocFixtures.NS_R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PptxParserTest {

    private fun shape(ph: String?, vararg paras: String) =
        "<p:sp><p:nvSpPr><p:cNvPr id=\"1\" name=\"s\"/><p:cNvSpPr/><p:nvPr>${ph?.let { "<p:ph $it/>" } ?: ""}</p:nvPr></p:nvSpPr>" +
            "<p:txBody>${paras.joinToString("")}</p:txBody></p:sp>"

    private fun para(text: String, pPr: String = "", rPr: String = "") = "<a:p>$pPr<a:r><a:rPr $rPr/><a:t>$text</a:t></a:r></a:p>"

    private fun slide(vararg shapes: String) = "<p:sld $NS_P $NS_A $NS_R><p:cSld><p:spTree>${shapes.joinToString("")}</p:spTree></p:cSld></p:sld>"

    @Test fun slidesInPresentationOrderWithTitlesBulletsAndPictures() {
        val first = slide(
            shape("type=\"title\"", para("Quarterly plan")),
            shape("idx=\"1\"", para("Grow", rPr = "b=\"1\""), para("Detail", "<a:pPr lvl=\"1\"/>"), para("No bullet", "<a:pPr><a:buNone/></a:pPr>")),
            "<p:pic><p:nvPicPr><p:cNvPr id=\"4\" name=\"pic\" descr=\"Logo\"/></p:nvPicPr><p:blipFill><a:blip r:embed=\"rId2\"/></p:blipFill></p:pic>",
            shape("type=\"sldNum\"", para("7")),
        )
        val second = slide(shape(null, para("Plain box")), shape(null, para("Numbered", "<a:pPr><a:buAutoNum type=\"arabicPeriod\"/></a:pPr>")))
        val pkg = DocFixtures.pkg(
            "_rels/.rels" to DocFixtures.rootRels("ppt/presentation.xml"),
            "ppt/presentation.xml" to "<p:presentation $NS_P $NS_R><p:sldIdLst><p:sldId id=\"256\" r:id=\"rA\"/><p:sldId id=\"257\" r:id=\"rB\"/></p:sldIdLst></p:presentation>",
            "ppt/_rels/presentation.xml.rels" to DocFixtures.rels(Triple("rA", "slide", "slides/slide9.xml"), Triple("rB", "slide", "slides/slide1.xml")),
            "ppt/slides/slide9.xml" to first,
            "ppt/slides/_rels/slide9.xml.rels" to DocFixtures.rels(Triple("rId2", "image", "../media/logo.jpeg")),
            "ppt/slides/slide1.xml" to second,
        )
        val preview = pkg.use { PptxParser.parse(it) }
        assertEquals(PageKind.Slides, preview.kind)
        val h = preview.html
        val one = h.indexOf("Slide <b>1</b> of 2")
        val two = h.indexOf("Slide <b>2</b> of 2")
        assertTrue(h, one in 0 until two)
        assertTrue(h, h.contains("<h2>Quarterly plan</h2>"))
        assertTrue(h, h.contains("<ul><li><strong>Grow</strong><ul><li>Detail</li></ul></li></ul><p>No bullet</p>"))
        assertTrue(h, h.contains("<figure><img src=\"pkg/ppt/media/logo.jpeg\" alt=\"Logo\"></figure>"))
        assertTrue("slide number placeholder is skipped: $h", !h.contains(">7<"))
        assertTrue(h, h.indexOf("Plain box") > two && h.contains("<p>Plain box</p><ol><li>Numbered</li></ol>"))
    }
}
