package com.fileviewer.app.source

import com.fileviewer.app.viewers.documents.DocPackage
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel

class AuditZipTest {
    private fun directory(n: Int, prefix: Int = 0, zip64: Boolean = false, variable: Int = 0): ByteArray {
        val size = n * (46 + variable)
        val b = ByteBuffer.allocate(prefix + 30 + size + (if (zip64) 76 else 0) + 22).order(ByteOrder.LITTLE_ENDIAN)
        b.position(prefix); b.putInt(0x04034b50); b.position(prefix + 30)
        repeat(n) {
            val at = b.position(); b.putInt(0x02014b50)
            b.putShort(at + 28, variable.toShort())
            b.position(at + 46 + variable)
        }
        if (zip64) {
            val at = b.position()
            b.putInt(0x06064b50).putLong(44); b.position(at + 40)
            b.putLong(size.toLong()).putLong((prefix + 30).toLong())
            b.putInt(0x07064b50).putInt(0).putLong(at.toLong()).putInt(1)
        }
        b.putInt(0x06054b50).putShort(0).putShort(0).putShort(1).putShort(1)
            .putInt(size).putInt(30).putShort(0)
        return b.array()
    }
    @Test fun legitimateHundredThousandEntriesOpen() {
        BoundedZip.open(SeekableInMemoryByteChannel(directory(100000))).use {
            assert(it.entries.asSequence().count() == 100000)
        }
    }
    @Test fun prefixedAndZip64LayoutsCannotBypassTheGate() {
        for (prefix in listOf(0, 97)) for (zip64 in listOf(false, true)) {
            assertThrows(LimitExceededException::class.java) {
                BoundedZip.open(SeekableInMemoryByteChannel(directory(100001, prefix, zip64)))
            }
            BoundedZip.open(SeekableInMemoryByteChannel(directory(1, prefix, zip64))).close()
        }
    }
    @Test fun metadataBytesAreBoundedIndependentlyOfCount() {
        assertThrows(LimitExceededException::class.java) {
            BoundedZip.open(SeekableInMemoryByteChannel(directory(129, variable = 65535)))
        }
    }
    @Test fun dishonestEocdCountCannotBypassLimit() {
        // Empty entries point to a shared valid local header. EOCD deliberately claims one entry.
        val n = 100001
        val b = ByteBuffer.allocate(30 + n * 46 + 22).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x04034b50); b.position(30)
        repeat(n) {
            val start = b.position(); b.putInt(0x02014b50); b.position(start + 46)
        }
        b.putInt(0x06054b50).putShort(0).putShort(0).putShort(1).putShort(1)
            .putInt(n * 46).putInt(30).putShort(0)
        val file = java.io.File.createTempFile("zip-limit", ".zip")
        try {
            file.writeBytes(b.array())
            FileSeekable(file).use { seekable ->
                assertThrows(LimitExceededException::class.java) {
                    ArchiveAccess.list(com.fileviewer.app.detect.ArchiveFormat.ZIP, LocalFileSource(file), seekable)
                }
            }
        } finally { file.delete() }
    }
}
