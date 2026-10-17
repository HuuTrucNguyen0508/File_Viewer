package com.fileviewer.app.viewers.data

import com.fileviewer.app.viewers.data.sqlite.SqlText
import com.fileviewer.app.viewers.data.sqlite.SqliteFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SqliteTextTest {
    @Test fun quotesIdentifiersAndDoublesEmbeddedQuotes() {
        assertEquals("\"users\"", SqlText.quote("users"))
        assertEquals("\"we\"\"ird; DROP TABLE x\"", SqlText.quote("we\"ird; DROP TABLE x"))
        assertEquals("\"two words\"", SqlText.quote("two words"))
    }

    @Test fun pageQueryBoundsCellsBeforeTheyReachTheCursor() {
        val sql = SqlText.pageQuery("t", 3, listOf("odd\"name"))
        assertTrue(sql.contains("length(\"odd\"\"name\")"))
        assertTrue(sql.contains("substr(CAST(\"odd\"\"name\" AS TEXT), 1, 2001)"))
        assertTrue(sql.endsWith("LIMIT 101 OFFSET 300"))
        assertTrue(SqlText.pageQuery("t", 2, listOf("id"), pageRows = 10).endsWith("LIMIT 11 OFFSET 20"))
        assertTrue(SqlText.pageQuery("t", 21474837, listOf("id")).endsWith("OFFSET 2147483700"))
    }

    @Test fun onlyReadStatementsAreBuilt() {
        val sql = listOf(SqlText.pageQuery("t", 1, listOf("id")), SqlText.countQuery("t"), SqlText.OBJECTS_QUERY, SqlText.SCHEMA_QUERY)
        sql.forEach { assertTrue(it, it.trimStart().startsWith("SELECT")) }
        assertEquals("SELECT COUNT(*) FROM \"a\"\"b\"", SqlText.countQuery("a\"b"))
    }

    @Test fun pageMath() {
        assertEquals(1L, SqlText.pageCount(0))
        assertEquals(1L, SqlText.pageCount(100))
        assertEquals(2L, SqlText.pageCount(101))
        assertEquals("No rows", SqlText.rangeLabel(0, 0, 0))
        assertEquals("1\u2013100 of 1,204", SqlText.rangeLabel(0, 100, 1204))
        assertEquals("1,201\u20131,204 of 1,204", SqlText.rangeLabel(12, 4, 1204))
        assertEquals("101\u2013150", SqlText.rangeLabel(1, 50, null))
    }

    @Test fun cellFormatting() {
        assertEquals("1 row", SqlText.rowsLabel(1))
        assertEquals("12,345 rows", SqlText.rowsLabel(12345))
        assertEquals("<blob, 1 byte>", SqlText.blobLabel(1))
        assertEquals("<blob, 2,048 bytes>", SqlText.blobLabel(2048))
        assertEquals("abc", SqlText.clipCell("abc", 5))
        assertEquals("abcde\u2026", SqlText.clipCell("abcdefgh", 5))
        assertTrue(SqlText.isInternal("sqlite_sequence"))
        assertFalse(SqlText.isInternal("sequences"))
    }

    private fun header(walBytes: Int) = ByteArray(100).also { it[18] = walBytes.toByte(); it[19] = walBytes.toByte() }

    @Test fun databasesThatNeedSideFilesAreCopied() {
        assertFalse(SqliteFiles.needsCopy(header(1), hasWal = false, hasJournal = false))
        assertTrue(SqliteFiles.needsCopy(header(2), hasWal = false, hasJournal = false))
        assertTrue(SqliteFiles.needsCopy(header(1), hasWal = true, hasJournal = false))
        assertTrue(SqliteFiles.needsCopy(header(1), hasWal = false, hasJournal = true))
        assertFalse(SqliteFiles.isWalMode(ByteArray(10)))
    }
}
