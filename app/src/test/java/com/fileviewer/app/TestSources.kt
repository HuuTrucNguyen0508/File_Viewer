package com.fileviewer.app

import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.SeekableSource
import java.io.ByteArrayInputStream
import java.io.InputStream

/** A stream-only source, like a pipe-backed content URI. */
class BytesSource(
    override val name: String,
    private val bytes: ByteArray,
    override val mimeHint: String? = null,
    override val archiveDepth: Int = 0,
) : FileSource {
    override val key = "bytes:$name:${bytes.contentHashCode()}"
    override val size: Long = bytes.size.toLong()
    override val lastModified: Long? = null
    override val displayPath = name
    override fun openStream(): InputStream = ByteArrayInputStream(bytes)
    override fun seekableOrNull(): SeekableSource? = null
}

fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
