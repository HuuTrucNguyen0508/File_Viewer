package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.Limits
import com.fileviewer.app.viewer.html.HtmlPage

fun esc(s: String): String {
    if (s.length > Limits.MAX_DOC_HTML_CHARS) throw DocumentException("A text block is too large to preview. Open Text to read it.")
    return HtmlPage.escape(s)
}

/** Character formatting shared by the Office and ODF parsers. */
data class RunStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val sup: Boolean = false,
    val sub: Boolean = false,
) {
    fun wrap(html: String): String {
        if (html.isEmpty()) return html
        var s = html
        if (sup) s = "<sup>$s</sup>"
        if (sub) s = "<sub>$s</sub>"
        if (strike) s = "<s>$s</s>"
        if (underline) s = "<u>$s</u>"
        if (italic) s = "<em>$s</em>"
        if (bold) s = "<strong>$s</strong>"
        return s
    }
}

/**
 * Output HTML with a size cap. Past [cap] characters it sets [truncated] and throws
 * [StopParsing], which ends the parse; [finish] then closes open lists and returns the page.
 */
class HtmlOut(private val cap: Int = Limits.MAX_DOC_HTML_CHARS) {
    private val sb = StringBuilder()
    private val lists = ArrayList<Boolean>()

    var truncated = false
        private set

    val length get() = sb.length
    val remaining get() = (cap - sb.length).coerceAtLeast(0)

    fun raw(s: String) {
        if (truncated) throw StopParsing()
        val remaining = (cap - sb.length).coerceAtLeast(0)
        if (s.length > remaining) {
            sb.append(s, 0, remaining)
            truncated = true
            throw StopParsing()
        }
        sb.append(s)
    }

    fun text(s: String) = raw(esc(s))

    fun truncate(): Nothing {
        truncated = true
        throw StopParsing()
    }

    /** One list item at nesting [level] (0-based), opening and closing `<ul>`/`<ol>` as needed. */
    fun listItem(level: Int, ordered: Boolean, html: String) {
        val depth = level.coerceIn(0, 8) + 1
        while (lists.size > depth) closeList()
        if (lists.size == depth && lists.last() != ordered) closeList()
        if (lists.size == depth) raw("</li>")
        while (lists.size < depth) {
            lists += ordered
            raw(if (ordered) "<ol>" else "<ul>")
        }
        raw("<li>$html")
    }

    fun closeLists() {
        while (lists.isNotEmpty()) closeList()
    }

    private fun closeList() {
        val ordered = lists.removeAt(lists.lastIndex)
        raw(if (ordered) "</li></ol>" else "</li></ul>")
    }

    /** The finished HTML; safe to call after truncation. */
    fun finish(): String {
        while (lists.isNotEmpty()) sb.append(if (lists.removeAt(lists.lastIndex)) "</li></ol>" else "</li></ul>")
        return sb.toString()
    }
}

/** An `<img>` for a package entry, or a labelled placeholder for formats a WebView can't draw (EMF, WMF, TIFF). */
fun imageHtml(path: String?, alt: String?): String {
    if (path == null) return ""
    val altText = alt?.takeIf { it.isNotBlank() }
    if (PackagePaths.imageMime(path) == null) {
        val ext = path.substringAfterLast('.', "").uppercase().ifEmpty { "Embedded" }
        return "<span class=\"missing\">${esc("$ext image not shown")}</span>"
    }
    return "<img src=\"${esc(PackagePaths.url(path))}\" alt=\"${esc(altText ?: "")}\">"
}
