package com.fileviewer.app.source

import com.fileviewer.app.BytesSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class AuditCacheTest {
    @Test fun retainedSelectionCreatesFreshOpeningAndReusesItWithinSession() = runBlocking {
        val cache = MaterializeCache(tmp.newFolder())
        var provider = "old"
        val retained = object : FileSource by BytesSource("document", byteArrayOf()) {
            override fun newOpening(): FileSource = object : FileSource by BytesSource("document", provider.toByteArray()) {
                override val key = ContentOpeningKey("content://provider/1").key
            }
        }
        val state = com.fileviewer.app.AppState()
        state.openExternal(listOf(retained, retained))
        state.openShared(retained)
        val first = (state.current as com.fileviewer.app.Screen.Viewer).source
        assertEquals("old", first.materialize(cache).use { it.file!!.readText() })
        provider = "new"
        state.back()
        state.openShared(retained)
        val second = (state.current as com.fileviewer.app.Screen.Viewer).source
        assertNotEquals(first.key, second.key)
        assertEquals("new", second.materialize(cache).use { it.file!!.readText() })
        assertEquals("old", first.materialize(cache).use { it.file!!.readText() })
        assertEquals(second.materialize(cache).use { it.file }, second.materialize(cache).use { it.file })
    }
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
