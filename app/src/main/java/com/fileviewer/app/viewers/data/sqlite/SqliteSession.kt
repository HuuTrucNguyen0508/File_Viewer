package com.fileviewer.app.viewers.data.sqlite

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import com.fileviewer.app.source.Limits
import com.fileviewer.app.viewers.data.DataLimits
import java.io.Closeable
import java.io.File
import java.util.UUID

class DbObject(val name: String, val isView: Boolean)

/** One page of a table: [rows] holds at most a page; [hasMore] says a next page exists. */
class DbPage(val columns: List<String>, val rows: List<List<String?>>, val hasMore: Boolean)

/**
 * A read-only connection. Opened with OPEN_READONLY and only ever sent SELECTs, so nothing is written.
 * Databases that need side files are opened from a private copy, deleted on [close].
 */
class SqliteSession private constructor(private val db: SQLiteDatabase, private val scratch: File?) : Closeable {

    fun objects(): List<DbObject> = db.rawQuery(SqlText.OBJECTS_QUERY, null).use { c ->
        val out = ArrayList<DbObject>()
        while (c.moveToNext() && out.size < DataLimits.MAX_DB_OBJECTS) out += DbObject(c.getString(0), c.getString(1) == "view")
        out
    }

    fun count(table: String, signal: CancellationSignal? = null): Long =
        db.rawQuery(SqlText.countQuery(table), null, signal).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    fun page(table: String, page: Int, signal: CancellationSignal? = null): DbPage =
        db.rawQuery(SqlText.pageQuery(table, page), null, signal).use { c ->
            val rows = ArrayList<List<String?>>()
            val cols = minOf(c.columnCount, DataLimits.MAX_TABLE_COLS)
            while (c.moveToNext() && rows.size < DataLimits.SQL_PAGE_ROWS + 1) rows += List(cols) { cell(c, it) }
            val more = rows.size > DataLimits.SQL_PAGE_ROWS
            DbPage(List(cols) { c.getColumnName(it) }, if (more) rows.dropLast(1) else rows, more)
        }

    /** The CREATE statements for [table] and its indexes and triggers, one blank line apart. */
    fun schema(table: String): String = db.rawQuery(SqlText.SCHEMA_QUERY, arrayOf(table)).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0).trim().trimEnd(';') + ";") }.joinToString("\n\n")
    }

    override fun close() {
        db.close()
        scratch?.deleteRecursively()
    }

    private fun cell(c: Cursor, i: Int): String? = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_BLOB -> SqlText.blobLabel(c.getBlob(i).size)
        else -> SqlText.clipCell(c.getString(i))
    }

    companion object {
        /** Opens [file] read-only. Blocking; call off the main thread. */
        fun open(context: Context, file: File): SqliteSession {
            val header = file.inputStream().use { s -> ByteArray(100).also { s.read(it) } }
            val hasWal = sideFile(file, "-wal").let { it.isFile && it.length() > 0 }
            val hasJournal = sideFile(file, "-journal").let { it.isFile && it.length() > 0 }
            val inCache = file.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator)
            var path = file
            var scratch: File? = null
            if (!inCache && SqliteFiles.needsCopy(header, hasWal, hasJournal) && file.length() <= Limits.MAX_MATERIALIZE_BYTES) {
                scratch = scratchRoot(context).resolve(UUID.randomUUID().toString()).also { it.mkdirs() }
                path = scratch.resolve("db").also { file.copyTo(it) }
                // The journal is left behind on purpose: replaying it would be a write.
                if (hasWal) sideFile(file, "-wal").copyTo(sideFile(path, "-wal"))
            }
            return try {
                SqliteSession(SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY), scratch)
            } catch (t: Throwable) {
                scratch?.deleteRecursively()
                throw t
            }
        }

        private fun sideFile(db: File, suffix: String) = File(db.path + suffix)

        private fun scratchRoot(context: Context): File = File(context.cacheDir, "sqlite-view").also { root ->
            val stale = System.currentTimeMillis() - 60 * 60 * 1000
            root.listFiles()?.filter { it.lastModified() < stale }?.forEach { it.deleteRecursively() }
        }
    }
}
