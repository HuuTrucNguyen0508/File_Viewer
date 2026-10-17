package com.fileviewer.app.viewers.misc

import com.fileviewer.app.viewers.misc.cert.CertificateParser
import com.fileviewer.app.viewers.misc.cert.KeyItem
import com.fileviewer.app.viewers.misc.font.CmapTable
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MalformedInputTest {
    @Test(timeout = 5000) fun repeatedUnclosedPemMarkersAreRefused() {
        val parsed = CertificateParser.parse("-----BEGIN A-----\n".repeat(200_000).toByteArray())
        assertTrue(parsed.items.single() is KeyItem.Unreadable)
    }

    @Test(timeout = 5000) fun overlappingCmapSegmentsAreRefused() {
        val segments = 32_767
        val bytes = ByteBuffer.allocate(12 + 16 + segments * 8).order(ByteOrder.BIG_ENDIAN)
        bytes.putShort(0).putShort(1).putShort(3).putShort(1).putInt(12)
        bytes.position(12)
        bytes.putShort(4).putShort(0).putShort(0).putShort((segments * 2).toShort())
        val ends = 12 + 14
        val starts = 12 + 16 + segments * 2
        val deltas = 12 + 16 + segments * 4
        for (i in 0 until segments) {
            bytes.putShort(ends + i * 2, 0xFFFE.toShort())
            bytes.putShort(starts + i * 2, 0)
            bytes.putShort(deltas + i * 2, 1)
        }
        assertNull(CmapTable.parse(bytes.array()))
    }
}
