package com.fileviewer.app.source

import com.fileviewer.app.detect.ArchiveFormat
import kotlinx.coroutines.runBlocking
import java.io.InputStream

/** One entry inside an archive. Seekable only once materialized into the cache. */
class ArchiveEntrySource(
    val archive: FileSource,
    val format: ArchiveFormat,
    val entry: ArchiveEntry,
    private val cache: MaterializeCache = MaterializeCache.default,
) : FileSource {
    override val key: String get() = "${archive.key}!/${entry.path}"
    override val name: String get() = entry.path.trimEnd('/').substringAfterLast('/')
    override val size: Long? get() = entry.size
    override val mimeHint: String? get() = null
    override val lastModified: Long? get() = entry.lastModified
    override val displayPath: String get() = "${archive.displayPath}!/${entry.path}"
    override val archiveDepth: Int get() = archive.archiveDepth + 1

    override fun openStream(): InputStream {
        cache.existing(key)?.let { return it.inputStream() }
        val seekable = if (ArchiveAccess.needsSeekable(format)) archiveSeekable() else null
        return LimitedInputStream(ArchiveAccess.openEntry(format, archive, seekable, entry.path), Limits.MAX_ENTRY_BYTES, name)
    }

    override fun seekableOrNull(): SeekableSource? = cache.existing(key)?.let(::FileSeekable)

    /** The archive itself, made seekable; reuses an earlier materialized copy. Called on a background thread. */
    private fun archiveSeekable(): SeekableSource =
        archive.seekableOrNull() ?: cache.existing(archive.key)?.let(::FileSeekable) ?: runBlocking { archive.materialize(cache) }

    override fun equals(other: Any?) = other is ArchiveEntrySource && other.key == key
    override fun hashCode() = key.hashCode()
}
