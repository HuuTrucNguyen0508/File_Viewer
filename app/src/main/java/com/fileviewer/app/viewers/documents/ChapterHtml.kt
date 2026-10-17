package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.Limits

/**
 * Turns an EPUB chapter into a safe HTML fragment: an allowlist of text and structure tags,
 * no scripts, styles, forms, embeds or event attributes, links reduced to their text, and
 * image sources resolved inside the package. SVG covers keep only their `<image>`.
 */
object ChapterHtml {

    data class Result(val html: String, val truncated: Boolean)

    private val ALLOWED = setOf(
        "p", "div", "span", "h1", "h2", "h3", "h4", "h5", "h6", "br", "hr", "ul", "ol", "li", "dl", "dt", "dd",
        "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption", "blockquote", "pre", "code", "em", "strong",
        "b", "i", "u", "s", "strike", "sub", "sup", "small", "big", "mark", "del", "ins", "kbd", "samp", "var", "cite",
        "q", "abbr", "dfn", "figure", "figcaption", "section", "article", "aside", "header", "footer", "nav", "main",
        "img", "ruby", "rt", "rp", "address", "time", "bdi", "bdo", "wbr", "center",
    )

    /** Dropped together with everything inside them. */
    private val DROPPED = setOf(
        "script", "style", "head", "title", "iframe", "object", "embed", "noscript", "template", "select", "textarea",
        "audio", "video", "canvas", "applet", "frameset", "noframes", "math-annotation", "annotation-xml",
    )

    private val VOID = setOf("br", "hr", "img", "wbr", "input", "meta", "link", "base", "param", "source", "track", "col", "area")

    fun sanitize(src: String, chapterPath: String, cap: Int = Limits.MAX_DOC_HTML_CHARS): Result {
        val out = HtmlOut(cap)
        val sink = Sink(out, PackagePaths.dirOf(chapterPath))
        try {
            HtmlTokens.scan(src, sink)
            sink.closeAll()
        } catch (_: StopParsing) {
        }
        return Result(out.finish(), out.truncated)
    }

    private class Sink(val out: HtmlOut, val dir: String) : HtmlTokenSink() {
        private val open = ArrayList<String>()
        private var dropTag: String? = null
        private var dropDepth = 0
        private var svg = 0

        override fun start(name: String, attrs: Map<String, String>, selfClosing: Boolean) {
            val dropping = dropTag
            if (dropping != null) {
                if (dropping == "head" && name == "body") { dropTag = null; dropDepth = 0 }
                else { if (name == dropping && !selfClosing) dropDepth++; return }
            }
            if (name in DROPPED) {
                if (!selfClosing && name !in VOID) { dropTag = name; dropDepth = 1 }
                return
            }
            if (name == "svg") { if (!selfClosing) svg++; return }
            if (svg > 0) {
                if (name == "image") img(attrs["href"], attrs["alt"])
                return
            }
            if (name == "img") { img(attrs["src"], attrs["alt"]); return }
            if (name !in ALLOWED) return
            if (name in VOID) { out.raw("<$name>"); return }
            if (selfClosing) return
            out.raw("<$name${keptAttributes(name, attrs)}>")
            open += name
        }

        override fun end(name: String) {
            val dropping = dropTag
            if (dropping != null) {
                if (name == dropping && --dropDepth == 0) dropTag = null
                return
            }
            if (name == "svg") { if (svg > 0) svg--; return }
            if (svg > 0 || name in VOID) return
            val at = open.lastIndexOf(name)
            if (at < 0) return
            while (open.size > at) out.raw("</${open.removeAt(open.lastIndex)}>")
        }

        override fun text(raw: String) {
            if (dropTag != null || svg > 0) return
            out.raw(HtmlTokens.safeText(raw))
        }

        fun closeAll() {
            while (open.isNotEmpty()) out.raw("</${open.removeAt(open.lastIndex)}>")
        }

        private fun img(src: String?, alt: String?) {
            val path = src?.let { PackagePaths.resolve(dir, it) } ?: return
            out.raw(imageHtml(path, alt))
        }

        private fun keptAttributes(name: String, attrs: Map<String, String>): String {
            val sb = StringBuilder()
            fun keep(attr: String, value: String?) { if (value != null) sb.append(' ').append(attr).append("=\"").append(esc(value)).append('"') }
            keep("lang", attrs["lang"]?.takeIf { LANG.matches(it) })
            keep("dir", attrs["dir"]?.takeIf { it == "rtl" || it == "ltr" || it == "auto" })
            when (name) {
                "td", "th" -> {
                    keep("colspan", attrs["colspan"]?.toIntOrNull()?.coerceIn(1, 64)?.toString())
                    keep("rowspan", attrs["rowspan"]?.toIntOrNull()?.coerceIn(1, 1000)?.toString())
                }
                "ol" -> keep("start", attrs["start"]?.toIntOrNull()?.toString())
                "li" -> keep("value", attrs["value"]?.toIntOrNull()?.toString())
            }
            return sb.toString()
        }
    }

    private val LANG = Regex("[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*")
}
