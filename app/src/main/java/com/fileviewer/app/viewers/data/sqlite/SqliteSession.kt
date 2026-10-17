package com.fileviewer.app.viewers.data.sqlite

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import com.fileviewer.app.source.Limits
import com.fileviewer.app.source.readFully
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
class SqliteSession private constructor(private val db: SQLiteDatabase, private val scratch: File?, val missingWal: Boolean) : Closeable {

    fun objects(): List<DbObject> = db.rawQuery(SqlText.OBJECTS_QUERY, null).use { c ->
        val out = ArrayList<DbObject>()
        while (c.moveToNext() && out.size < Limits.MAX_DB_OBJECTS) out += DbObject(c.getString(0), c.getString(1) == "view")
        out
    }

    fun count(table: String, signal: CancellationSignal? = null): Long =
        db.rawQuery(SqlText.countQuery(table), null, signal).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    fun page(table: String, page: Int, signal: CancellationSignal? = null): DbPage {
        val columns = db.rawQuery(SqlText.columnsQuery(table), null, signal).use { it.columnNames.take(Limits.MAX_TABLE_COLS) }
        return db.rawQuery(SqlText.pageQuery(table, page, columns), null, signal).use { c ->
            val rows = ArrayList<List<String?>>()
            while (c.moveToNext() && rows.size < Limits.SQL_PAGE_ROWS + 1) rows += List(columns.size) { cell(c, it) }
            val more = rows.size > Limits.SQL_PAGE_ROWS
            DbPage(columns, if (more) rows.dropLast(1) else rows, more)
        }
    }

    /** The CREATE statements for [table] and its indexes and triggers, one blank line apart. */
    fun schema(table: String): String = db.rawQuery(SqlText.SCHEMA_QUERY, arrayOf(table)).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0).trim().trimEnd(';') + ";") }.joinToString("\n\n")
    }

    override fun close() {
        db.close()
        scratch?.deleteRecursively()
    }

    private fun cell(c: Cursor, column: Int): String? {
        val value = column * 2
        return when (c.getString(value + 1)) {
            "null" -> null
            "blob" -> SqlText.blobLabel(c.getInt(value))
            else -> SqlText.clipCell(c.getString(value))
        }
    }

    companion object {
        /** Opens [file] read-only. Blocking; call off the main thread. */
        fun open(context: Context, file: File): SqliteSession {
            val header = file.inputStream().use { input -> ByteArray(100).also { input.readFully(it) } }
            val hasWal = sideFile(file, "-wal").let { it.isFile && it.length() > 0 }
            val hasJournal = sideFile(file, "-journal").let { it.isFile && it.length() > 0 }
            val inCache = file.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator)
            var path = file
            var scratch: File? = null
            return try {
                if (!inCache && SqliteFiles.needsCopy(header, hasWal, hasJournal)) {
                    scratch = scratchRoot(context).resolve(UUID.randomUUID().toString())
                    check(scratch.mkdirs()) { "Couldn't create a private database copy" }
                    path = scratch.resolve("db")
                    SqliteFiles.copy(file, path)
                    if (hasWal) SqliteFiles.copy(sideFile(file, "-wal"), sideFile(path, "-wal"))
                }
                SqliteSession(SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY), scratch, SqliteFiles.isWalMode(header) && !hasWal)
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
