package com.fileviewer.app.viewers.misc

/** Bounds for the phase-2 misc viewers. Kept beside them so parallel phase-2 work doesn't edit `Limits`. */
object MiscLimits {
    /** Largest `name` table read from a font. */
    const val MAX_NAME_TABLE_BYTES = 1 shl 20

    /** Largest `cmap` table read from a font. */
    const val MAX_CMAP_TABLE_BYTES = 8 shl 20

    /** Code point ranges kept from a cmap. */
    const val MAX_COVERAGE_RANGES = 100_000

    /** Glyph cells shown for one block of the glyph grid. */
    const val MAX_GLYPHS_PER_SECTION = 512

    /** Glyph cells shown outside the common blocks. */
    const val MAX_OTHER_GLYPHS = 1024

    /** Largest certificate or key file parsed. */
    const val MAX_CERT_FILE_BYTES = 4L shl 20

    /** Certificates and keys listed from one file. */
    const val MAX_CERT_ITEMS = 200

    /** Permissions listed for one APK. */
    const val MAX_PERMISSIONS = 500
}
