package com.fileviewer.app.viewers.data

import com.fileviewer.app.source.Limits

import com.fileviewer.app.source.FileSource
import com.fileviewer.app.viewers.code.TextLoader
import java.io.InputStream
import java.io.InterruptedIOException

/** Decoded text of a data file; [truncated] when more than [Limits.MAX_DATA_TEXT_BYTES] bytes were present. */
data class LoadedText(val text: String, val truncated: Boolean)

object DataText {

    /** Blocking; call off the main thread. */
    fun load(source: FileSource, maxBytes: Long = Limits.MAX_DATA_TEXT_BYTES): LoadedText =
        source.openStream().use { decode(it, maxBytes) }

    fun decode(input: InputStream, maxBytes: Long): LoadedText {
        val (bytes, truncated) = readPrefix(input, maxBytes)
        val text = TextLoader.decode(bytes, TextLoader.detectCharset(bytes))
        return LoadedText(if (truncated) text.dropLastWhile { it == '\uFFFD' } else text, truncated)
    }

    /** Reads at most [max] bytes; the flag says whether the stream held more. */
    fun readPrefix(input: InputStream, max: Long): Pair<ByteArray, Boolean> {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (total < max) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Read cancelled")
            val n = input.read(buf, 0, minOf(buf.size.toLong(), max - total).toInt())
            if (n < 0) return out.toByteArray() to false
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray() to (input.read() >= 0)
    }
}
