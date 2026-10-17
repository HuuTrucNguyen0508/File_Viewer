package com.fileviewer.app.viewers.documents

import com.fileviewer.app.detect.DocumentFormat
import com.fileviewer.app.source.LimitExceededException
import com.fileviewer.app.source.Limits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RtfAndLegacyTest {

    private fun rtf(s: String) = RtfParser.parse(s).html

    @Test fun rtfParagraphsFormattingAndCharacters() {
        val src = "{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0\\fswiss Helvetica;}}{\\colortbl;\\red255\\green0\\blue0;}" +
            "{\\*\\generator Riched20;}{\\info{\\title Secret title}}\n" +
            "\\pard Hello {\\b bold} and {\\i\\ul both}\\i0 .\\par\n" +
            "Caf\\'e9 \\u8364? price\\line next{\\pict\\pngblip 89504e47}\\par\n" +
            "\\uc2\\u20013\\'81\\'82 end\\par}"
        val h = rtf(src)
        assertEquals("<p>Hello <strong>bold</strong> and <em><u>both</u></em>.</p><p>Café € price<br>next</p><p>中 end</p>", h)
        assertFalse(h.contains("Helvetica") || h.contains("Secret") || h.contains("Riched") || h.contains("89504e47"))
    }

    @Test fun rtfTablesAndEscapes() {
        val src = "{\\rtf1 \\trowd\\cellx1000\\cellx2000\\pard\\intbl A\\cell B\\{x\\}\\cell\\row\\pard After\\par}"
        assertEquals("<table><tr><td>A</td><td>B{x}</td></tr></table><p>After</p>", rtf(src))
    }

    @Test fun rtfHtmlIsEscapedAndDeepNestingIsRefused() {
        assertEquals("<p>&lt;script&gt;</p>", rtf("{\\rtf1 <script>\\par}"))
        val deep = "{\\rtf1 " + "{".repeat(Limits.MAX_DOC_NESTING + 5) + "x" + "}".repeat(Limits.MAX_DOC_NESTING + 5) + "}"
        assertThrows(LimitExceededException::class.java) { RtfParser.parse(deep) }
    }

    @Test fun legacyBinariesYieldReadableRunsInFileOrder() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()))
        out.write(ByteArray(64) { (it * 7).toByte() })
        out.write("Meeting notes for Tuesday\r".toByteArray(Charsets.ISO_8859_1))
        out.write(ByteArray(16))
        out.write("Root Entry".toByteArray(Charsets.UTF_16LE))
        out.write(ByteArray(3))
        out.write("Привет, мир".toByteArray(Charsets.UTF_16LE))
        out.write(byteArrayOf(1, 2, 3, 4, 5))
        out.write("x1#".toByteArray())
        val preview = DocumentLoader.load(DocumentFormat.DOC, ByteArrayInputStream(out.toByteArray())) as PagePreview
        assertEquals(PageKind.ExtractedText, preview.kind)
        assertEquals("<p>Meeting notes for Tuesday</p><p>Привет, мир</p>", preview.html)
    }

    @Test fun legacyFileWithNoTextSaysSo() {
        val preview = LegacyText.extract(ByteArrayInputStream(ByteArray(4096) { (it % 7).toByte() }))
        assertTrue(preview.html.contains("No readable text"))
    }
}
