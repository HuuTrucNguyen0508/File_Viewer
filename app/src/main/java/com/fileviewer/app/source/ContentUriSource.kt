package com.fileviewer.app.source

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import java.io.FileNotFoundException
import java.io.InputStream

/** A content:// (or file://) URI read through the ContentResolver. */
class ContentUriSource private constructor(
    private val resolver: ContentResolver,
    val uri: Uri,
    override val name: String,
    override val size: Long?,
    override val mimeHint: String?,
    override val lastModified: Long?,
) : FileSource {
    override val key: String = ContentOpeningKey(uri.toString()).key
    override val displayPath: String get() = uri.toString()

    override fun openStream(): InputStream =
        resolver.openInputStream(uri) ?: throw FileNotFoundException("No stream for $uri")

    override fun seekableOrNull(): SeekableSource? {
        val afd: AssetFileDescriptor = try {
            resolver.openAssetFileDescriptor(uri, "r")
        } catch (_: Exception) {
            null
        } ?: return null
        val pfd = afd.parcelFileDescriptor
        val regular = try {
            OsConstants.S_ISREG(Os.fstat(pfd.fileDescriptor).st_mode)
        } catch (_: Exception) {
            false
        }
        if (!regular) {
            afd.close()
            return null
        }
        val start = afd.startOffset
        val length = if (afd.declaredLength == AssetFileDescriptor.UNKNOWN_LENGTH) {
            pfd.statSize - start
        } else {
            afd.declaredLength
        }
        if (start < 0 || length < 0 || start > pfd.statSize || length > pfd.statSize - start) {
            afd.close()
            return null
        }
        return FdSeekable(pfd, start, length)
    }

    override fun equals(other: Any?) = other is ContentUriSource && other.key == key
    override fun hashCode() = key.hashCode()

    companion object {
        /** Queries display name, size, and modified date; tolerates providers that report none of them. */
        fun from(resolver: ContentResolver, uri: Uri, mimeFromIntent: String? = null): ContentUriSource {
            var name: String? = null
            var size: Long? = null
            var modified: Long? = null
            runCatching {
                resolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }
                            ?.let { name = c.getString(it) }
                        c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }
                            ?.let { size = c.getLong(it).takeIf { n -> n >= 0 } }
                        c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                            .takeIf { it >= 0 && !c.isNull(it) }?.let { modified = c.getLong(it) }
                    }
                }
            }
            val mime = runCatching { resolver.getType(uri) }.getOrNull()
                ?: mimeFromIntent?.takeUnless { it == "*/*" }
            val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "file"
            return ContentUriSource(resolver, uri, name ?: fallbackName, size, mime, modified)
        }
    }
}
