package com.fileviewer.app.detect

import com.fileviewer.app.bytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSnifferTest {
    @Test fun bomsAreRecognizedAndMeasured() {
        assertEquals(TextSniff(true, Charsets.UTF_8, 3), TextSniffer.sniff(bytes(0xEF, 0xBB, 0xBF) + "hi".toByteArray()))
        val utf32 = TextSniffer.sniff(bytes(0xFF, 0xFE, 0, 0) + "hi".toByteArray(TextSniffer.UTF_32LE))
        assertEquals(TextSniff(true, TextSniffer.UTF_32LE, 4), utf32)
        assertEquals(Charsets.UTF_16BE, TextSniffer.sniff(bytes(0xFE, 0xFF) + "hi".toByteArray(Charsets.UTF_16BE)).charset)
    }

    @Test fun latin1TextIsTextButNotUtf8() {
        val s = TextSniffer.sniff("café crème".toByteArray(Charsets.ISO_8859_1))
        assertTrue(s.isText)
        assertEquals(Charsets.ISO_8859_1, s.charset)
    }

    @Test fun truncationOnlyForgivenWhenSampleWasCut() {
        val cut = "ab".toByteArray() + bytes(0xE2, 0x82) // first two bytes of '€'
        assertEquals(Charsets.UTF_8, TextSniffer.sniff(cut, truncated = true).charset)
        assertEquals(Charsets.ISO_8859_1, TextSniffer.sniff(cut, truncated = false).charset)
    }

    @Test fun controlHeavyDataIsBinary() {
        assertFalse(TextSniffer.sniff(ByteArray(100) { (it % 8 + 1).toByte() }).isText)
        assertTrue(TextSniffer.sniff("\u001B[31mred\u001B[0m log line\n".toByteArray()).isText)
    }
}
