package com.fileviewer.app

import com.fileviewer.app.viewers.hex.HexPaging
import org.junit.Assert.*
import org.junit.Test

class AuditHexTest {
    @Test fun savedTopByteRestoresAcrossRowWidthsAndWindows() {
        val position = com.fileviewer.app.viewers.hex.HexPosition(0)
        val before = HexPaging(Long.MAX_VALUE, 8)
        val start = before.windowStartFor(24_000_000_088L)
        position.offset = { before.rowOffset(start + 11) }
        val scope = object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any) = value is Long
        }
        val saved = with(com.fileviewer.app.viewers.hex.HexPosition.Saver) { scope.save(position) }!!
        val restored = com.fileviewer.app.viewers.hex.HexPosition.Saver.restore(saved)!!
        val after = HexPaging(Long.MAX_VALUE, 16)
        val top = after.rowOffset(after.windowStartFor(restored.offset()) + after.indexFor(restored.offset()))
        assertTrue(restored.offset() in top until top + 16)
        assertTrue(top > 0)
    }
    @Test fun hexRowCountDoesNotOverflow() {
        assertEquals(Long.MAX_VALUE / 16 + 1, HexPaging(Long.MAX_VALUE).rowCount)
    }

    @Test fun everyTargetMapsToAReachableWindowWithoutOverflow() {
        for (size in listOf((16L shl 30) - 1, (16L shl 30) + 1, (32L shl 30) + 1, Long.MAX_VALUE - 1, Long.MAX_VALUE)) {
            for (width in listOf(8, 16)) {
                val p = HexPaging(size, width)
                for (offset in listOf(0, 7, 1000000000L * width - 1, 1000000000L * width, size - 1, Long.MAX_VALUE)) {
                    val start = p.windowStartFor(offset)
                    val index = p.indexFor(offset)
                    assertTrue(index >= 0 && index < p.windowRows(start))
                    assertEquals(p.rowFor(offset), start + index)
                    assertTrue(p.windowLastByte(start) >= offset.coerceAtMost(size - 1))
                    assertTrue(p.windowLastByte(start) < size)
                    assertEquals(start, p.nextWindow(p.previousWindow(start)).takeIf { start > 0 } ?: start)
                }
            }
        }
    }
}
