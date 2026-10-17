package com.fileviewer.app.viewers.documents

import java.io.InterruptedIOException

/**
 * A forgiving HTML/XHTML tokenizer for EPUB chapters. It never builds a DOM and never
 * resolves DTDs: comments, doctypes and processing instructions are skipped, CDATA becomes text.
 * Tag and attribute names are lower-cased with any namespace prefix removed.
 */
abstract class HtmlTokenSink {
    abstract fun start(name: String, attrs: Map<String, String>, selfClosing: Boolean)
    abstract fun end(name: String)

    /** Raw text between tags; entities are not decoded. */
    abstract fun text(raw: String)
}

object HtmlTokens {

    fun scan(src: String, sink: HtmlTokenSink) {
        var i = 0
        val n = src.length
        var steps = 0
        while (i < n) {
            if (++steps and 0xFFF == 0 && Thread.currentThread().isInterrupted) throw InterruptedIOException("Parse cancelled")
            val lt = src.indexOf('<', i)
            if (lt < 0) { sink.text(src.substring(i)); break }
            if (lt > i) sink.text(src.substring(i, lt))
            i = when {
                src.startsWith("<!--", lt) -> src.indexOf("-->", lt + 4).let { if (it < 0) n else it + 3 }
                src.startsWith("<![CDATA[", lt) -> {
                    val end = src.indexOf("]]>", lt + 9).let { if (it < 0) n else it }
                    sink.text(esc(src.substring(lt + 9, end)))
                    minOf(n, end + 3)
                }
                src.startsWith("<!", lt) || src.startsWith("<?", lt) -> src.indexOf('>', lt).let { if (it < 0) n else it + 1 }
                src.startsWith("</", lt) -> {
                    val gt = src.indexOf('>', lt).let { if (it < 0) n else it }
                    sink.end(localName(src.substring(lt + 2, gt).trim()))
                    minOf(n, gt + 1)
                }
                lt + 1 < n && src[lt + 1].isLetter() -> tag(src, lt + 1, sink)
                else -> { sink.text("&lt;"); lt + 1 }
            }
        }
    }

    /** Parses a start tag beginning at the name; returns the index after its '>'. */
    private fun tag(src: String, from: Int, sink: HtmlTokenSink): Int {
        val n = src.length
        var i = from
        while (i < n && !src[i].isWhitespace() && src[i] != '>' && src[i] != '/') i++
        val name = localName(src.substring(from, i))
        val attrs = LinkedHashMap<String, String>()
        var selfClosing = false
        while (i < n) {
            val c = src[i]
            when {
                c == '>' -> { sink.start(name, attrs, selfClosing); return i + 1 }
                c == '/' -> { selfClosing = true; i++ }
                c.isWhitespace() -> i++
                else -> {
                    selfClosing = false
                    val ns = i
                    while (i < n && !src[i].isWhitespace() && src[i] != '=' && src[i] != '>' && src[i] != '/') i++
                    val attr = localName(src.substring(ns, i))
                    while (i < n && src[i].isWhitespace()) i++
                    var value = ""
                    if (i < n && src[i] == '=') {
                        i++
                        while (i < n && src[i].isWhitespace()) i++
                        if (i < n && (src[i] == '"' || src[i] == '\'')) {
                            val q = src[i]
                            val end = src.indexOf(q, i + 1).let { if (it < 0) n else it }
                            value = src.substring(i + 1, end)
                            i = minOf(n, end + 1)
                        } else {
                            val vs = i
                            while (i < n && !src[i].isWhitespace() && src[i] != '>') i++
                            value = src.substring(vs, i)
                        }
                    }
                    if (attr.isNotEmpty() && attr !in attrs) attrs[attr] = decodeEntities(value)
                }
            }
        }
        sink.start(name, attrs, selfClosing)
        return n
    }

    private fun localName(raw: String): String = raw.substringAfterLast(':').lowercase()

    private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{0,31});")

    /** Keeps well-formed entity references for the WebView to decode; escapes everything else. */
    fun safeText(raw: String): String {
        val sb = StringBuilder(raw.length + 16)
        var last = 0
        for (m in ENTITY.findAll(raw)) {
            sb.append(esc(raw.substring(last, m.range.first)))
            sb.append(m.value)
            last = m.range.last + 1
        }
        sb.append(esc(raw.substring(last)))
        return sb.toString()
    }

    /** Decodes the entities that matter in attribute values (URLs, alt text). */
    fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        return ENTITY.replace(s) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let(::codePoint)
                body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePoint)
                else -> NAMED[body]
            } ?: m.value
        }
    }

    private fun codePoint(cp: Int): String? = if (Character.isValidCodePoint(cp) && cp != 0) String(Character.toChars(cp)) else null

    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
}
