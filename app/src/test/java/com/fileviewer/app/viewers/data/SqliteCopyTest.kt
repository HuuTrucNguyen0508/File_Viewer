package com.fileviewer.app.viewers.data

import com.fileviewer.app.source.LimitExceededException
import com.fileviewer.app.viewers.data.sqlite.SqliteFiles
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SqliteCopyTest {
    @Test fun copiesDatabaseWithoutChangingSource() {
        val folder = Files.createTempDirectory("sqlite-copy").toFile()
        try {
            val bytes = ByteArray(1000) { it.toByte() }
            val source = folder.resolve("source").apply { writeBytes(bytes) }
            val target = folder.resolve("copy")
            SqliteFiles.copy(source, target, 1000)
            assertArrayEquals(bytes, source.readBytes())
            assertArrayEquals(bytes, target.readBytes())
        } finally { folder.deleteRecursively() }
    }

    @Test fun refusesOversizedDatabaseAndSidecarsInsteadOfOpeningOriginal() {
        val folder = Files.createTempDirectory("sqlite-copy-limit").toFile()
        try {
            val source = folder.resolve("source-wal").apply { writeBytes(ByteArray(1001)) }
            val target = folder.resolve("copy-wal")
            val result = runCatching { SqliteFiles.copy(source, target, 1000) }
            assertTrue(result.exceptionOrNull() is LimitExceededException)
            assertTrue(source.length() == 1001L)
        } finally { folder.deleteRecursively() }
    }
    @Test fun enforcesBytesReadWhenMetadataUnderstatesFileSize() {
        val folder = Files.createTempDirectory("sqlite-copy-stream-limit").toFile()
        try {
            val actual = folder.resolve("source").apply { writeBytes(ByteArray(1001)) }
            val source = object : java.io.File(actual.path) { override fun length() = 0L }
            val result = runCatching { SqliteFiles.copy(source, folder.resolve("copy"), 1000) }
            assertTrue(result.exceptionOrNull() is LimitExceededException)
            assertTrue(actual.length() == 1001L)
        } finally { folder.deleteRecursively() }
    }

}
