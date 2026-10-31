package com.fileviewer.app.source

import java.io.InputStream

/**
 * What every viewer reads from. Never a raw File: content URIs and archive entries look the same.
 * Frozen contract for phase-2 viewers.
 */
interface FileSource {
    /** Stable identity, used to key the materialize cache. */
    val key: String
    val name: String

    /** Null when the provider doesn't report it. */
    val size: Long?

    /** MIME type from the ContentResolver or the intent. */
    val mimeHint: String?

    /** Epoch millis, when known. */
    val lastModified: Long?

    /** Human-readable location for the Info view: a path, a URI, or archive!/entry. */
    val displayPath: String

    /** 0 for a real file; 1 for an entry of an archive; 2 for an entry of an archive inside an archive... */
    val archiveDepth: Int get() = 0

    /** Refresh retained browser selections; keep the returned source for the whole viewer session. */
    fun newOpening(): FileSource = this

    /** A fresh stream from byte 0. Caller closes. */
    fun openStream(): InputStream

    /**
     * A seekable view when one is cheap: a real file path, or a regular-file descriptor.
     * Pipe-backed descriptors are not seekable and return null; use [materialize]. Caller closes.
     */
    fun seekableOrNull(): SeekableSource?
}

/** File-name extension in lower case, without the dot; "" when there is none. */
val FileSource.extension: String
    get() = name.substringAfterLast('.', "").lowercase().takeIf { name.lastIndexOf('.') > 0 } ?: ""
