package com.fileviewer.app.viewers.documents

/**
 * Word (DOCX) to HTML: paragraphs, heading styles, bold/italic/underline, lists, tables,
 * hyperlinks as text, and embedded images. Headers, footers, comments and notes are left out.
 */
object DocxParser {

    fun parse(pkg: DocPackage): PagePreview {
        val main = OoxmlRels.of(pkg, "").byType("officeDocument") ?: "word/document.xml"
        val rels = OoxmlRels.of(pkg, main)
        val styles = DocxStyles.load(pkg, rels.byType("styles"), rels.byType("numbering"))
        val out = HtmlOut()
        pkg.read(main) { XmlScan.parse(it, BodyHandler(out, rels, styles)) }
        return PagePreview(out.finish(), PageKind.Text, listOfNotNull(CUT_NOTE.takeIf { out.truncated }))
    }

    private class Para(val styleId: String? = null) {
        var style: String? = styleId
        var numId: String? = null
        var level = 0
        val html = DocBuffer()
        var visible = false
    }

    private class BodyHandler(val out: HtmlOut, val rels: OoxmlRels, val styles: DocxStyles) : XmlHandler() {
        private val paras = ArrayList<Para>()
        /** Paragraphs from text boxes, written after the paragraph that anchors them. */
        private val deferred = DocBuffer()
        private var skip = 0
        private var inPPr = false
        private var inRun = false
        private var inRunProps = false
        private var inText = false
        private var run = RunStyle()
        private val runHtml = DocBuffer()
        private var alt: String? = null
        private var textBox: BodyHandler? = null
        private var textBoxDepth = 0
        /** Inside `<w:tc>` with its `<td>` not yet written (the span comes from `<w:tcPr>`). */
        private var cellPending = false
        private var cellSpan = 1
        private var inCellProps = false

        private val para get() = paras.lastOrNull()

        override fun start(name: String, attrs: Attrs) {
            if (inTextBox { textBoxDepth++; it.start(name, attrs) }) return
            if (skip > 0 || name in setOf("Fallback", "pPrChange", "rPrChange", "tcPrChange", "trPrChange", "tblPrChange", "sectPrChange")) { skip++; return }
            if (name == "txbxContent") {
                textBox = BodyHandler(HtmlOut(out.remaining - deferred.length), rels, styles)
                textBoxDepth = 1
                return
            }
            when (name) {
                "tbl" -> { openCell(); out.closeLists(); out.raw("<table>") }
                "tr" -> out.raw("<tr>")
                "tc" -> { cellPending = true; cellSpan = 1 }
                "tcPr" -> inCellProps = true
                "gridSpan" -> if (inCellProps) cellSpan = attrs["val"]?.toIntOrNull()?.coerceIn(1, 64) ?: 1
                "p" -> { openCell(); paras += Para() }
                "pPr" -> inPPr = true
                "pStyle" -> if (inPPr) para?.style = attrs["val"]
                "ilvl" -> if (inPPr) para?.level = attrs["val"]?.toIntOrNull() ?: 0
                "numId" -> if (inPPr) para?.numId = attrs["val"]
                "r" -> { inRun = true; run = RunStyle(); runHtml.setLength(0) }
                "rPr" -> inRunProps = inRun && !inPPr
                "b" -> if (inRunProps) run = run.copy(bold = attrs.toggle())
                "i" -> if (inRunProps) run = run.copy(italic = attrs.toggle())
                "u" -> if (inRunProps) run = run.copy(underline = attrs.toggle())
                "strike", "dstrike" -> if (inRunProps) run = run.copy(strike = attrs.toggle())
                "vertAlign" -> if (inRunProps) run = run.copy(sup = attrs["val"] == "superscript", sub = attrs["val"] == "subscript")
                "t" -> inText = inRun
                "tab" -> if (inRun && !inPPr) runHtml.append(' ')
                "br", "cr" -> if (inRun) runHtml.append("<br>")
                "noBreakHyphen" -> if (inRun) runHtml.append('-')
                "hyperlink" -> para?.html?.append("<span class=\"link\" title=\"${esc(rels.url(attrs.rel("id")) ?: "")}\">")
                "docPr" -> alt = attrs["descr"] ?: attrs["title"]
                "blip" -> image(attrs.rel("embed") ?: attrs.rel("link"))
                "imagedata" -> image(attrs.rel("id"))
            }
        }

        override fun end(name: String) {
            if (inTextBox { box ->
                if (--textBoxDepth == 0) {
                    deferred.append(box.out.finish())
                    para?.visible = true
                    textBox = null
                } else box.end(name)
            }) return
            if (skip > 0) { skip--; return }
            when (name) {
                "tbl" -> { out.closeLists(); out.raw("</table>") }
                "tr" -> out.raw("</tr>")
                "tcPr" -> { inCellProps = false; openCell() }
                "tc" -> { openCell(); out.closeLists(); out.raw("</td>") }
                "p" -> endParagraph()
                "pPr" -> inPPr = false
                "rPr" -> inRunProps = false
                "t" -> inText = false
                "r" -> {
                    inRun = false
                    para?.html?.append(run.wrap(runHtml.toString()))
                    runHtml.setLength(0)
                }
                "hyperlink" -> para?.html?.append("</span>")
            }
        }

        override fun text(s: String) {
            if (inTextBox { it.text(s) }) return
            if (skip == 0 && inText) {
                runHtml.append(esc(s))
                if (s.isNotBlank()) para?.visible = true
            }
        }

        private inline fun inTextBox(block: (BodyHandler) -> Unit): Boolean {
            val box = textBox ?: return false
            try {
                block(box)
            } catch (_: StopParsing) {
                textBox = null
                para?.html?.takeIf { it.isNotEmpty() }?.let { out.raw("<p>$it</p>") }
                out.raw(deferred.toString())
                out.raw(box.out.finish())
                out.truncate()
            }
            return true
        }

        private fun openCell() {
            if (!cellPending) return
            cellPending = false
            out.raw(if (cellSpan > 1) "<td colspan=\"$cellSpan\">" else "<td>")
        }

        private fun image(id: String?) {
            val path = rels.path(id) ?: return
            val html = imageHtml(path, alt)
            if (inRun) runHtml.append(html) else para?.html?.append(html)
            para?.visible = true
            alt = null
        }

        private fun endParagraph() {
            val p = paras.removeAt(paras.lastIndex)
            val html = p.html.toString()
            if (!p.visible) return
            val heading = styles.headingLevel(p.style)
            val block = when {
                heading != null -> "<h$heading>$html</h$heading>"
                p.numId != null && p.numId != "0" -> null
                else -> "<p>$html</p>"
            }
            if (paras.isNotEmpty()) {
                deferred.append(block ?: "<p>$html</p>")
                return
            }
            if (block == null) {
                out.listItem(p.level, styles.ordered(p.numId!!, p.level), html)
            } else {
                out.closeLists()
                out.raw(block)
            }
            if (deferred.isNotEmpty()) {
                out.raw(deferred.toString())
                deferred.setLength(0)
            }
        }
    }
}
