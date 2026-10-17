package com.fileviewer.app.viewers.hex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexPagingTest {
    @Test fun rowsAndPagesForUnevenFile() {
        val p = HexPaging(fileSize = 65_536L + 20, bytesPerRow = 16, pageBytes = 65_536)
        assertEquals(4098L, p.rowCount)
        assertEquals(4096, p.rowsPerPage)
        assertEquals(2L, p.pageCount)
        assertEquals(1L, p.pageOf(4096))
        assertEquals(20, p.pageLength(1))
        assertEquals(4, p.rowLength(4097))
        assertEquals(16, p.indexInPage(4097))
        assertEquals(65_536L + 16, p.rowOffset(4097))
    }

    @Test fun emptyAndLargeFiles() {
        val empty = HexPaging(0)
        assertEquals(0L, empty.rowCount)
        assertEquals(0L, empty.pageCount)
        assertEquals(0L, empty.rowFor(100))
        val big = HexPaging(200L * 1024 * 1024 * 1024)
        assertEquals(200L * 1024 * 1024 * 1024 / 16, big.rowCount)
        assertEquals(big.rowCount - 1, big.rowFor(Long.MAX_VALUE))
    }

    @Test fun offsetParsing() {
        assertEquals(500L, HexPaging.parseOffset("500"))
        assertEquals(0x1F4L, HexPaging.parseOffset("0x1F4"))
        assertEquals(0x1F4L, HexPaging.parseOffset("1f4h"))
        assertEquals(0xABCL, HexPaging.parseOffset("abc"))
        assertEquals(0x1F4L, HexPaging.parseOffset("\$1F4"))
        assertNull(HexPaging.parseOffset("-5"))
        assertNull(HexPaging.parseOffset("zz"))
    }

    @Test fun rowFormatting() {
        val buf = "Hello, hex view!\u0000".toByteArray()
        assertEquals("48 65 6C 6C 6F 2C 20 68  65 78 20 76 69 65 77 21", HexPaging.formatHex(buf, 0, 16))
        assertEquals("00" + " ".repeat(46), HexPaging.formatHex(buf, 16, 1))
        assertEquals("Hello, hex view!", HexPaging.formatAscii(buf, 0, 16))
        assertEquals("·", HexPaging.formatAscii(buf, 16, 1))
        assertEquals("000001F0", HexPaging.formatOffset(0x1F0))
    }
}
