package com.fileviewer.app.viewers.data

import android.webkit.WebResourceResponse
import com.fileviewer.app.source.ArchiveEntry
import com.fileviewer.app.source.ArchiveEntrySource
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.LimitedInputStream
import com.fileviewer.app.source.LocalFileSource
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLDecoder

/**
 * Finds the files a page refers to by relative URL, in the same folder as the page or below it
 * (also inside the same archive). Anything that climbs out with `..` or names a different scheme,
 * host or absolute path is refused.
 */
object SiblingFiles {

    /** Resource types a rendered page may load, by extension. */
    private val mimes = mapOf(
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif",
        "webp" to "image/webp", "svg" to "image/svg+xml", "bmp" to "image/bmp", "ico" to "image/x-icon",
        "avif" to "image/avif", "css" to "text/css", "woff" to "font/woff", "woff2" to "font/woff2",
        "ttf" to "font/ttf", "otf" to "font/otf",
    )

    fun mimeOf(path: String): String? = mimes[path.substringAfterLast('.', "").lowercase()]

    /** A safe folder-relative path from a request path, or null when it escapes the folder. Pure. */
    fun normalize(requestPath: String): String? {
        val raw = requestPath.substringBefore('#').substringBefore('?')
        val decoded = try { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") } catch (_: IllegalArgumentException) { return null }
        if (decoded.isEmpty() || decoded.startsWith("/") || '\\' in decoded || '\u0000' in decoded) return null
        if (decoded.substringBefore('/').contains(':')) return null
        val parts = ArrayList<String>()
        for (part in decoded.split('/')) when (part) {
            "", "." -> {}
            ".." -> return null
            else -> parts += part
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    /** Opens [relative] next to [page], or null when the file is missing, too large, or not a page resource. */
    fun open(page: FileSource, relative: String): InputStream? {
        val rel = normalize(relative) ?: return null
        mimeOf(rel) ?: return null
        return try {
            when (page) {
                is LocalFileSource -> openLocal(page.file, rel)
                is ArchiveEntrySource -> openInArchive(page, rel)
                else -> null
            }?.let { LimitedInputStream(it, DataLimits.MAX_RESOURCE_BYTES, rel) }
        } catch (_: Exception) {
            null
        }
    }

    /** A WebView resolver for [page]: answers `images/a.png` style paths, nothing else. */
    fun resolver(page: FileSource): (String) -> WebResourceResponse? = { path ->
        val rel = normalize(path)
        val mime = rel?.let(::mimeOf)
        if (rel == null || mime == null) null else open(page, rel)?.let { WebResourceResponse(mime, null, it) }
    }

    private fun openLocal(pageFile: File, rel: String): InputStream? {
        val folder = pageFile.absoluteFile.parentFile?.canonicalFile ?: return null
        val target = File(folder, rel).canonicalFile
        if (!target.path.startsWith(folder.path + File.separator) || !target.isFile) return null
        return FileInputStream(target)
    }

    private fun openInArchive(page: ArchiveEntrySource, rel: String): InputStream? {
        val dir = page.entry.path.substringBeforeLast('/', "")
        val path = if (dir.isEmpty()) rel else "$dir/$rel"
        val entry = ArchiveEntry(path, isDirectory = false, size = null, compressedSize = null, lastModified = null)
        return ArchiveEntrySource(page.archive, page.format, entry).openStream()
    }
}
