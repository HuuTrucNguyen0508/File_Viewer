package com.fileviewer.app.viewers.documents

/**
 * Paths inside a document package. Relative references (relationship targets, EPUB hrefs)
 * resolve against a part's folder; anything that would climb above the package root, or
 * that names a scheme (http:, data:, file:), resolves to null.
 */
object PackagePaths {
    /** URL prefix the preview uses for package entries; the WebView resolver serves it. */
    const val PREFIX = "pkg/"

    fun dirOf(path: String): String = path.substringBeforeLast('/', "")

    fun resolve(baseDir: String, href: String): String? {
        val ref = href.substringBefore('#').substringBefore('?').trim()
        if (ref.isEmpty()) return null
        val colon = ref.indexOf(':')
        if (colon >= 0 && (ref.indexOf('/').let { it < 0 || colon < it })) return null
        val decoded = percentDecode(ref) ?: return null
        val parts = ArrayList<String>()
        if (!decoded.startsWith("/")) baseDir.split('/').filterTo(parts) { it.isNotEmpty() }
        for (seg in decoded.replace('\\', '/').split('/')) when (seg) {
            "", "." -> {}
            ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
            else -> parts += seg
        }
        return parts.joinToString("/").ifEmpty { null }
    }

    /** The URL the preview HTML uses for an entry; [entryOf] reverses it. */
    fun url(path: String): String = PREFIX + path.split('/').joinToString("/") { percentEncode(it) }

    fun entryOf(url: String): String? =
        url.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.substringBefore('?')?.let(::percentDecode)

    fun imageMime(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg", "jpe" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        "ico" -> "image/x-icon"
        "avif" -> "image/avif"
        else -> null
    }

    private fun percentEncode(s: String): String = buildString {
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c.toChar() in "-._~") append(c.toChar())
            else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
    }

    private fun percentDecode(s: String): String? {
        if ('%' !in s) return s
        val out = java.io.ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                if (i + 2 >= s.length) return null
                val v = s.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                out.write(v); i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8)); i++
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
