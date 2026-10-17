package com.fileviewer.app.viewers.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubParserTest {
    private val container = "<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
        "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>"

    private fun opf(extraManifest: String = "", spineAttrs: String = "", doctype: String = "") = "<?xml version=\"1.0\"?>$doctype" +
        "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>A Small Book</dc:title></metadata>" +
        "<manifest><item id=\"c1\" href=\"text/ch%201.xhtml\" media-type=\"application/xhtml+xml\"/>" +
        "<item id=\"c2\" href=\"text/ch2.xhtml\" media-type=\"application/xhtml+xml\"/>" +
        "<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>$extraManifest</manifest>" +
        "<spine $spineAttrs><itemref idref=\"c1\"/><itemref idref=\"css\"/><itemref idref=\"c2\"/><itemref idref=\"missing\"/></spine></package>"

    private val chapter1 = "<?xml version=\"1.0\" encoding=\"utf-8\"?><!DOCTYPE html>" +
        "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><head><title>Ch1</title>" +
        "<script>alert(1)</script><style>p{color:red}</style><link rel=\"stylesheet\" href=\"../style.css\"/></head>" +
        "<body onload=\"x()\"><h1 class=\"c\" style=\"color:red\">Chapter One</h1>" +
        "<p onclick=\"evil()\">It was a <em>dark</em> &amp; stormy&nbsp;night. <a href=\"ch2.xhtml#n1\">1</a></p>" +
        "<img src=\"../images/fig%201.png\" alt=\"Figure\"/><img src=\"../../../../outside.png\"/><img src=\"http://example.com/x.png\"/>" +
        "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"><image xlink:href=\"../images/cover.jpg\"/><text>svgtext</text></svg>" +
        "<iframe src=\"x\">frame text</iframe><form><input type=\"text\"/><button>Go</button></form><p>unclosed <b>bold</body></html>"

    private fun epub(opf: String, vararg more: Pair<String, String>) = DocFixtures.pkg(
        "mimetype" to "application/epub+zip",
        "META-INF/container.xml" to container,
        "OEBPS/content.opf" to opf,
        "OEBPS/text/ch 1.xhtml" to chapter1,
        "OEBPS/text/ch2.xhtml" to "<html><body><p>Second</p></body></html>",
        *more,
    )

    @Test fun spineChaptersWithNavTitles() {
        val nav = "<html xmlns:epub=\"http://www.idpf.org/2007/ops\"><body><nav epub:type=\"landmarks\"><a href=\"ch2.xhtml\">Wrong</a></nav>" +
            "<nav epub:type=\"toc\"><ol><li><a href=\"text/ch%201.xhtml\">The <i>First</i>\n Chapter</a></li></ol></nav></body></html>"
        val book = epub(opf("<item id=\"nav\" href=\"nav.xhtml\" properties=\"nav\" media-type=\"application/xhtml+xml\"/>"), "OEBPS/nav.xhtml" to nav)
            .use { EpubParser.parse(it) }
        assertEquals("A Small Book", book.title)
        assertEquals(listOf("OEBPS/text/ch 1.xhtml", "OEBPS/text/ch2.xhtml"), book.chapters.map { it.path })
        assertEquals("The First Chapter", book.chapters[0].title)
        assertNull(book.chapters[1].title)
    }

    @Test fun epub2NcxTitlesThroughAPlainDoctype() {
        val ncx = "<?xml version=\"1.0\"?><!DOCTYPE ncx PUBLIC \"-//NISO//DTD ncx 2005-1//EN\" \"http://www.daisy.org/z3986/2005/ncx-2005-1.dtd\">" +
            "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\"><navMap><navPoint id=\"n2\"><navLabel><text>Two</text></navLabel><content src=\"text/ch2.xhtml\"/></navPoint></navMap></ncx>"
        val doctype = "<!DOCTYPE package PUBLIC \"+//ISBN 0-9673008-1-9//DTD OEB 1.2 Package//EN\" \"http://openebook.org/dtds/oeb-1.2/oebpkg12.dtd\">"
        val book = epub(opf("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>", "toc=\"ncx\"", doctype), "OEBPS/toc.ncx" to ncx)
            .use { EpubParser.parse(it) }
        assertEquals("Two", book.chapters[1].title)
    }

    @Test fun doctypeWithAnInternalSubsetIsRefused() {
        val evil = "<!DOCTYPE package [<!ENTITY x SYSTEM \"file:///etc/hostname\">]>"
        assertThrows(DocumentException::class.java) { epub(opf(doctype = evil)).use { EpubParser.parse(it) } }
    }

    @Test fun chaptersAreSanitizedAndImagesResolveInsideThePackage() {
        val h = epub(opf()).use { pkg -> EpubParser.chapter(pkg, EpubParser.parse(pkg).chapters[0]) }.html
        assertTrue(h, h.contains("<h1>Chapter One</h1>"))
        assertTrue(h, h.contains("<p>It was a <em>dark</em> &amp; stormy&nbsp;night. 1</p>"))
        assertTrue(h, h.contains("<img src=\"pkg/OEBPS/images/fig%201.png\" alt=\"Figure\">"))
        assertTrue(h, h.contains("<img src=\"pkg/OEBPS/images/cover.jpg\" alt=\"\">"))
        assertTrue(h, h.endsWith("<p>unclosed <b>bold</b></p>"))
        listOf("alert", "script", "color:red", "onload", "onclick", "evil", "href", "outside", "example.com", "svgtext", "frame text", "<input", "<form", "Ch1", "class=")
            .forEach { assertFalse("$it leaked: $h", h.contains(it)) }
        assertTrue("button text stays readable: $h", h.contains("Go"))
    }

    @Test fun missingContainerIsAClearError() {
        val e = assertThrows(DocumentException::class.java) { DocFixtures.pkg("mimetype" to "application/epub+zip").use { EpubParser.parse(it) } }
        assertTrue(e.message!!.contains("container.xml"))
    }
}
