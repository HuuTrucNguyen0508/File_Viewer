package com.fileviewer.app.source

import com.fileviewer.app.BytesSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class AuditCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun reopenCopiesFreshBytesAndOneOpeningReusesItsCopy() = runBlocking {
        val cache = MaterializeCache(tmp.newFolder())
        fun source(text: String) = object : FileSource by BytesSource("document", text.toByteArray()) {
            override val key = ContentOpeningKey("content://provider/1").key
        }
        val old = source("old")
        val fresh = source("new")
        val first = old.materialize(cache).use { it.file!! }
        assertEquals(first, old.materialize(cache).use { it.file!! })
        assertEquals("new", fresh.materialize(cache).use { it.file!!.readText() })
        assertNotEquals(old.key, fresh.key)
    }
}
