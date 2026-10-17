package com.fileviewer.app.viewers.code

import com.fileviewer.app.source.FileSeekable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class TextLoadingTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun readCappedStopsAtTheLimit() {
        assertNull(TextLoader.readCapped(ByteArrayInputStream(ByteArray(101)), 100))
        assertEquals(100, TextLoader.readCapped(ByteArrayInputStream(ByteArray(100)), 100)!!.size)
    }

    @Test fun decodeDropsMatchingBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hé".toByteArray()
        assertEquals("hé", TextLoader.decode(bytes, Charsets.UTF_8))
    }

    @Test fun softBreakSplitsOnlyLongLines() {
        assertFalse(TextLoader.softBreak("short\nlines", 10).splitLines)
        val d = TextLoader.softBreak("abcdefghij12\nok", 5)
        assertTrue(d.splitLines)
        assertEquals("abcde\nfghij\n12\nok", d.text)
    }

    @Test fun pagedChunksCoverTheFileAndEndAfterNewlines() {
        val line = "line of text é\n"
        val text = line.repeat(500)
        val f = File(tmp.root, "big.txt").apply { writeText(text) }
        FileSeekable(f).use { s ->
            val starts = PagedText.chunkStarts(s, Charsets.UTF_8, chunk = 1000)
            assertEquals(0L, starts.first())
            assertTrue(starts.size > 5)
            val bytes = f.readBytes()
            val rebuilt = StringBuilder()
            starts.forEachIndexed { i, st ->
                val end = if (i + 1 < starts.size) starts[i + 1].toInt() else bytes.size
                if (i > 0) assertEquals('\n'.code.toByte(), bytes[st.toInt() - 1])
                rebuilt.append(String(bytes, st.toInt(), end - st.toInt(), Charsets.UTF_8))
            }
            assertEquals(text, rebuilt.toString())
        }
    }

    @Test fun pagedChunksNeverSplitUtf8WhenNoNewlineIsNear() {
        val text = "é".repeat(10_000) // 20 000 bytes, no newline
        val f = File(tmp.root, "one-line.txt").apply { writeText(text) }
        FileSeekable(f).use { s ->
            val starts = PagedText.chunkStarts(s, Charsets.UTF_8, chunk = 1001)
            val bytes = f.readBytes()
            starts.drop(1).forEach { assertTrue((bytes[it.toInt()].toInt() and 0xC0) != 0x80) }
        }
    }
}
