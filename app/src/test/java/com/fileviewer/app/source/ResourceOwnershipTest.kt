package com.fileviewer.app.source

import com.fileviewer.app.BytesSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class ResourceOwnershipTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun cancellationDuringAcquisitionClosesUndeliveredResource() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        val job = launch(Dispatchers.Default) {
            acquireResource {
                entered.complete(Unit)
                release.await()
                Closeable { closed.set(true) }
            }.close()
        }
        entered.await()
        job.cancel()
        release.countDown()
        job.join()
        assertTrue("The cancelled caller must not lose the acquired handle", closed.get())
    }

    @Test fun concurrentCopiesOfOneSourceDoNotSharePartialFiles() = runBlocking {
        val root = tmp.newFolder()
        val cache = MaterializeCache(root)
        val content = ByteArray(900_000) { it.toByte() }
        val source = BytesSource("shared", content)
        val first = async(Dispatchers.IO) { source.materialize(cache).use { it.file!!.readBytes() } }
        val second = async(Dispatchers.IO) { source.materialize(cache).use { it.file!!.readBytes() } }
        assertTrue(content.contentEquals(first.await()))
        assertTrue(content.contentEquals(second.await()))
        assertFalse(root.walk().any { it.name.endsWith(".part") })
    }

    @Test fun boundedSkipStopsReadingAtLimitPlusOne() {
        var consumed = 0
        val source = object : java.io.InputStream() {
            override fun read(): Int { consumed++; return 0 }
        }
        val bounded = LimitedInputStream(source, 16, "sample")
        org.junit.Assert.assertThrows(LimitExceededException::class.java) { bounded.skip(1_000_000) }
        assertEquals(17, consumed)
    }
}
