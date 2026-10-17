package com.fileviewer.app.source

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel

/** Random access over a file's bytes. Reads are positional and thread-safe. Caller closes. */
interface SeekableSource : Closeable {
    val size: Long

    /** Reads up to [length] bytes at [position]; returns -1 at end of file. */
    fun read(position: Long, dst: ByteArray, offset: Int = 0, length: Int = dst.size - offset): Int

    /** A real filesystem path, when there is one. */
    val file: File?

    /** A seekable descriptor positioned at byte 0 of the content, or null. Caller closes. */
    fun openFileDescriptor(): ParcelFileDescriptor?

    /** A read-only channel over the content with its own position. Caller closes; closing leaves this source open. */
    fun channel(): SeekableByteChannel = WindowChannel(this)
}

/** Reads exactly [length] bytes at [position] unless the file ends first. */
fun SeekableSource.readFully(position: Long, dst: ByteArray, length: Int = dst.size): Int {
    var total = 0
    while (total < length) {
        val n = read(position + total, dst, total, length - total)
        if (n < 0) break
        total += n
    }
    return total
}

class FileSeekable(override val file: File) : SeekableSource {
    private val raf = java.io.RandomAccessFile(file, "r")
    private val ch: FileChannel = raf.channel
    override val size: Long = ch.size()

    override fun read(position: Long, dst: ByteArray, offset: Int, length: Int): Int =
        if (position >= size) -1 else ch.read(ByteBuffer.wrap(dst, offset, length), position)

    override fun openFileDescriptor(): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    override fun close() = raf.close()
}

/** A window [start, start+size) of an already-open seekable descriptor (AssetFileDescriptor offsets). */
class FdSeekable(
    private val pfd: ParcelFileDescriptor,
    private val start: Long,
    override val size: Long,
) : SeekableSource {
    private val input = ParcelFileDescriptor.AutoCloseInputStream(pfd)
    private val ch: FileChannel = input.channel
    override val file: File? = null

    override fun read(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        val len = minOf(length.toLong(), size - position).toInt()
        return ch.read(ByteBuffer.wrap(dst, offset, len), start + position)
    }

    override fun openFileDescriptor(): ParcelFileDescriptor? = if (start == 0L && size == pfd.statSize) pfd.dup() else null

    override fun close() {
        input.close()
    }
}

/** Channel view of a [SeekableSource]; used by Commons Compress for ZIP and 7z. */
private class WindowChannel(private val src: SeekableSource) : SeekableByteChannel {
    private var pos = 0L
    private var open = true

    override fun read(dst: ByteBuffer): Int {
        if (!open) throw ClosedChannelException()
        if (pos >= src.size) return -1
        val want = minOf(dst.remaining().toLong(), src.size - pos).toInt()
        if (want == 0) return 0
        val n = if (dst.hasArray()) {
            src.read(pos, dst.array(), dst.arrayOffset() + dst.position(), want).also { if (it > 0) dst.position(dst.position() + it) }
        } else {
            val tmp = ByteArray(want)
            src.read(pos, tmp, 0, want).also { if (it > 0) dst.put(tmp, 0, it) }
        }
        if (n > 0) pos += n
        return n
    }

    override fun position(): Long = pos
    override fun position(newPosition: Long): SeekableByteChannel {
        if (newPosition < 0) throw IOException("negative position")
        pos = newPosition
        return this
    }

    override fun size(): Long = src.size
    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()
    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()
    override fun isOpen(): Boolean = open
    override fun close() { open = false }
}
