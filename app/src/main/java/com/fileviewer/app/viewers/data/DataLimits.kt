package com.fileviewer.app.viewers.data

/** Bounds for the phase-2 data viewers. Kept beside them so parallel phase-2 work doesn't edit `Limits`. */
object DataLimits {
    /** Bytes read from a Markdown, HTML, CSV or JSON file; anything past this is cut with a note. */
    const val MAX_TEXT_BYTES = 32L shl 20

    /** CSV and SQLite tables: rows kept in memory, columns shown, characters kept per cell. */
    const val MAX_TABLE_ROWS = 50_000
    const val MAX_TABLE_COLS = 200
    const val MAX_CELL_CHARS = 2_000

    /** JSON: nesting accepted, nodes kept in the tree, characters kept per string value. */
    const val MAX_JSON_DEPTH = 200
    const val MAX_JSON_NODES = 200_000
    const val MAX_JSON_STRING_CHARS = 20_000

    /** SQLite: rows per page and tables or views listed. */
    const val SQL_PAGE_ROWS = 100
    const val MAX_DB_OBJECTS = 5_000

    /** A stylesheet, image or font fetched from the folder next to an HTML or Markdown file. */
    const val MAX_RESOURCE_BYTES = 16L shl 20
}
