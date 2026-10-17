package com.fileviewer.app.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Returns a seekable view of this source, copying it into [cache] first when the source is
 * stream-only. The copy is cancellable, reports bytes copied to [progress], is bounded by
 * [Limits.MAX_MATERIALIZE_BYTES], and leaves no partial file behind on cancel or failure.
 * With [requireDescriptor], a seekable view that can't hand out a descriptor from byte 0
 * (an AssetFileDescriptor window) is copied too; PdfRenderer and MediaMetadataRetriever need one.
 * Caller closes the result.
 */
suspend fun FileSource.materialize(
    cache: MaterializeCache = MaterializeCache.default,
    requireDescriptor: Boolean = false,
    progress: (Long) -> Unit = {},
): SeekableSource = withContext(Dispatchers.IO) {
    seekableOrNull()?.let { s ->
        if (!requireDescriptor || s.file != null || s.openFileDescriptor()?.also { it.close() } != null) return@withContext s
        s.close()
    }
    cache.existing(key)?.let { return@withContext FileSeekable(it) }

    val dir = cache.dirFor(key)
    val part = File(dir, MaterializeCache.PARTIAL)
    val target = File(dir, MaterializeCache.CONTENT)
    try {
        LimitedInputStream(openStream(), Limits.MAX_MATERIALIZE_BYTES, name).use { input ->
            FileOutputStream(part).use { out ->
                val buf = ByteArray(256 * 1024)
                var copied = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    copied += n
                    progress(copied)
                }
            }
        }
        if (!part.renameTo(target)) throw java.io.IOException("Could not finish caching $name")
    } catch (t: Throwable) {
        part.delete()
        throw t
    }
    cache.trim(keep = key)
    FileSeekable(target)
}
