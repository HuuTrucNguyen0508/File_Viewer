package com.fileviewer.app.source

import com.fileviewer.app.BytesSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class LimitsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun limitedStreamCountsBytesActuallyRead() {
        val s = LimitedInputStream(ByteArrayInputStream(ByteArray(100)), 64, "sample")
        assertEquals(64, s.readNBytes(64).size)
        val e = assertThrows(LimitExceededException::class.java) { s.read() }
        assertEquals("sample is larger than the 64 byte limit", e.message)
    }

    @Test fun limitedStreamAllowsExactlyTheLimit() {
        val s = LimitedInputStream(ByteArrayInputStream(ByteArray(64)), 64, "x")
        assertEquals(64, s.readAllBytes().size)
        assertEquals(64L, s.count)
    }

    @Test fun materializeCopiesStreamOnlySourcesIntoTheCache() = runBlocking {
        val cache = MaterializeCache(tmp.newFolder("cache"))
        val data = ByteArray(300_000) { (it % 251).toByte() }
        val progress = mutableListOf<Long>()
        cache.let { c ->
            BytesSource("blob.bin", data).materialize(c) { progress += it }.use { s ->
                assertEquals(data.size.toLong(), s.size)
                val buf = ByteArray(10)
                s.readFully(299_990, buf)
                assertArrayEquals(data.copyOfRange(299_990, 300_000), buf)
                assertTrue(s.file!!.name == MaterializeCache.CONTENT)
            }
        }
        assertEquals(data.size.toLong(), progress.last())
    }

    @Test fun failedMaterializeLeavesNoPartialFile() {
        val root = tmp.newFolder("cache")
        val cache = MaterializeCache(root)
        val failing = object : com.fileviewer.app.source.FileSource by BytesSource("x", ByteArray(0)) {
            override fun openStream(): InputStream = object : InputStream() {
                var n = 0
                override fun read(): Int = if (n++ < 1000) 1 else throw IOException("disk gone")
            }
        }
        assertThrows(IOException::class.java) { runBlocking { failing.materialize(cache) } }
        assertFalse(root.walk().any { it.isFile })
    }

    @Test fun cacheTrimsLeastRecentlyUsed() {
        val root = tmp.newFolder("cache")
        val cache = MaterializeCache(root, maxBytes = 150)
        fun put(key: String, age: Long) = cache.contentFile(key).apply {
            writeBytes(ByteArray(100)); parentFile!!.setLastModified(age)
        }
        val old = put("old", 1_000)
        val recent = put("recent", 2_000)
        cache.trim()
        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertEquals(1, root.listFiles()!!.size)
    }

    @Test fun localFileIsSeekableWithoutCopy() {
        val f = File(tmp.root, "a.bin").apply { writeBytes(ByteArray(10) { it.toByte() }) }
        LocalFileSource(f).seekableOrNull().use { s ->
            val buf = ByteArray(3)
            assertEquals(3, s.read(7, buf))
            assertArrayEquals(byteArrayOf(7, 8, 9), buf)
            assertEquals(-1, s.read(10, buf))
        }
    }
}
