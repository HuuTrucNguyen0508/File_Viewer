package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.LimitExceededException
import com.fileviewer.app.source.Limits
import java.io.InterruptedIOException
import java.nio.charset.Charset

/**
 * A small RTF reader: paragraphs, line breaks, bold/italic/underline/strike, Unicode and
 * code-page characters, and simple tables. Font and color tables, pictures, objects,
 * headers, footers, footnotes and field instructions are skipped.
 */
object RtfParser {

    private val SKIP_DESTINATIONS = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "header", "headerl", "headerr", "headerf",
        "footer", "footerl", "footerr", "footerf", "footnote", "fldinst", "themedata", "colorschememapping",
        "latentstyles", "datastore", "xmlnstbl", "listtable", "listoverridetable", "rsidtbl", "generator",
        "pgdsctbl", "filetbl", "revtbl", "bkmkstart", "bkmkend", "annotation", "atnid", "atnauthor", "nonshppict",
        "shppict", "private", "xe", "tc", "txe", "comment", "userprops", "docvar", "mmathPr",
    )

    private data class State(
        var style: RunStyle = RunStyle(),
        var skip: Boolean = false,
        var uc: Int = 1,
    )

    fun parse(src: String): PagePreview {
        val p = Parser(src)
        try {
            p.run()
            p.flushParagraph()
            p.closeTable()
        } catch (_: StopParsing) {
        }
        return PagePreview(p.out.finish(), PageKind.Text, listOfNotNull(CUT_NOTE.takeIf { p.out.truncated }))
    }

    private class Parser(val src: String) {
        val out = HtmlOut()
        private var i = 0
        private val stack = ArrayList<State>()
        private var st = State()
        private val para = DocBuffer()
        private val run = DocBuffer()
        private var runStyle = RunStyle()
        private var paraHasText = false
        private var inTable = false
        private var paraInTable = false
        private val row = ArrayList<String>()
        private var codePage: Charset = Charset.forName("windows-1252")
        private val pendingBytes = java.io.ByteArrayOutputStream()

        fun run() {
            var steps = 0
            while (i < src.length) {
                if (++steps and 0xFFF == 0 && Thread.currentThread().isInterrupted) throw InterruptedIOException("Parse cancelled")
                when (val c = src[i]) {
                    '{' -> {
                        flushBytes()
                        if (stack.size >= Limits.MAX_DOC_NESTING) throw LimitExceededException("RTF nesting", Limits.MAX_DOC_NESTING.toLong())
                        stack += st.copy(); i++
                    }
                    '}' -> {
                        flushBytes()
                        if (stack.isNotEmpty()) st = stack.removeAt(stack.lastIndex)
                        i++
                    }
                    '\\' -> control()
                    '\r', '\n' -> i++
                    else -> { flushBytes(); emit(c.toString()); i++ }
                }
            }
            flushBytes()
        }

        private fun control() {
            i++
            if (i >= src.length) return
            val c = src[i]
            if (!c.isLetter()) {
                i++
                when (c) {
                    '\\', '{', '}' -> emit(c.toString())
                    '~' -> emit(" ")
                    '_' -> emit("‑")
                    '-' -> {}
                    // `\*` marks an optional destination; none of them carry body text.
                    '*' -> st.skip = true
                    '\'' -> hexByte()
                    '\n', '\r' -> paragraph()
                    '\t' -> emit("\t")
                }
                return
            }
            val start = i
            while (i < src.length && src[i].isLetter() && i - start < 32) i++
            val word = src.substring(start, i)
            var param: Int? = null
            val numStart = i
            if (i < src.length && (src[i] == '-' || src[i].isDigit())) {
                i++
                while (i < src.length && src[i].isDigit() && i - numStart < 11) i++
                param = src.substring(numStart, i).toIntOrNull()
            }
            if (i < src.length && src[i] == ' ') i++
            word(word, param)
        }

        private fun word(w: String, param: Int?) {
            if (w != "u" && w != "bin") flushBytes()
            if (w in SKIP_DESTINATIONS) { st.skip = true; return }
            val on = param != 0
            when (w) {
                "par", "sect" -> paragraph()
                "line" -> emitHtml("<br>")
                "tab" -> emit("\t")
                "emdash" -> emit("—"); "endash" -> emit("–")
                "lquote" -> emit("‘"); "rquote" -> emit("’")
                "ldblquote" -> emit("“"); "rdblquote" -> emit("”")
                "bullet" -> emit("•")
                "b" -> st.style = st.style.copy(bold = on)
                "i" -> st.style = st.style.copy(italic = on)
                "ul", "uld", "uldb", "ulw", "ulwave", "uldash" -> st.style = st.style.copy(underline = on)
                "ulnone" -> st.style = st.style.copy(underline = false)
                "strike", "striked" -> st.style = st.style.copy(strike = on)
                "super" -> st.style = st.style.copy(sup = on, sub = false)
                "sub" -> st.style = st.style.copy(sub = on, sup = false)
                "nosupersub" -> st.style = st.style.copy(sup = false, sub = false)
                "plain" -> st.style = RunStyle()
                "pard" -> paraInTable = false
                "intbl" -> paraInTable = true
                "cell" -> cell()
                "row" -> endRow()
                "uc" -> st.uc = (param ?: 1).coerceIn(0, 10)
                "u" -> param?.let { unicode(it) }
                "ansicpg" -> param?.let { cp -> runCatching { Charset.forName("windows-$cp") }.recoverCatching { Charset.forName("cp$cp") }.getOrNull()?.let { codePage = it } }
                "bin" -> { flushBytes(); i = (i.toLong() + (param ?: 0).coerceAtLeast(0)).coerceAtMost(src.length.toLong()).toInt() }
            }
        }

        private fun unicode(v: Int) {
            val cp = if (v < 0) v + 65536 else v
            emit(if (Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else "�")
            var skipChars = st.uc
            while (skipChars > 0 && i < src.length) {
                when {
                    src.startsWith("\\'", i) -> { i = minOf(src.length, i + 4); skipChars-- }
                    src[i] == '\\' || src[i] == '{' || src[i] == '}' -> break
                    src[i] == '\r' || src[i] == '\n' -> i++
                    else -> { i++; skipChars-- }
                }
            }
        }

        private fun hexByte() {
            val hex = src.substring(i, minOf(src.length, i + 2))
            i += hex.length
            hex.toIntOrNull(16)?.let { if (!st.skip) pendingBytes.write(it) }
        }

        private fun flushBytes() {
            if (pendingBytes.size() == 0) return
            val s = String(pendingBytes.toByteArray(), codePage)
            pendingBytes.reset()
            emit(s)
        }

        private fun emit(text: String) {
            if (st.skip) return
            if (runStyle != st.style) flushRun()
            runStyle = st.style
            run.append(esc(text))
            if (text.isNotBlank()) paraHasText = true
        }

        private fun emitHtml(html: String) {
            if (st.skip) return
            flushRun()
            para.append(html)
        }

        private fun flushRun() {
            if (run.isEmpty()) return
            para.append(runStyle.wrap(run.toString()))
            run.setLength(0)
        }

        private fun paragraph() {
            if (st.skip) return
            flushRun()
            if (paraInTable) {
                para.append("<br>")
                return
            }
            flushParagraph()
        }

        fun flushParagraph() {
            flushRun()
            closeTableUnlessRowOpen()
            if (paraHasText || para.contains("<br>")) out.raw("<p>${para.toString().removeSuffix("<br>")}</p>")
            para.setLength(0)
            paraHasText = false
        }

        private fun cell() {
            flushRun()
            row += para.toString().removeSuffix("<br>")
            para.setLength(0)
            paraHasText = false
        }

        private fun endRow() {
            flushRun()
            if (!inTable) { out.raw("<table>"); inTable = true }
            out.raw("<tr>" + row.joinToString("") { "<td>$it</td>" } + "</tr>")
            row.clear()
            para.setLength(0)
            paraHasText = false
        }

        private fun closeTableUnlessRowOpen() {
            if (inTable && row.isEmpty() && !paraInTable) closeTable()
        }

        fun closeTable() {
            if (row.isNotEmpty()) endRow()
            if (inTable) { out.raw("</table>"); inTable = false }
        }
    }
}
