package com.fileviewer.app.viewers.documents

/** PowerPoint (PPTX): each slide's title, text (bullets kept), tables and pictures, in order. */
object PptxParser {

    fun parse(pkg: DocPackage): PagePreview {
        val pres = OoxmlRels.of(pkg, "").byType("officeDocument") ?: "ppt/presentation.xml"
        val rels = OoxmlRels.of(pkg, pres)
        val ids = ArrayList<String>()
        pkg.read(pres) { input ->
            XmlScan.parse(input, object : XmlHandler() {
                override fun start(name: String, attrs: Attrs) {
                    if (name == "sldId") attrs.rel("id")?.let { ids += it }
                }
                override fun end(name: String) { if (name == "sldIdLst") throw StopParsing() }
            })
        }
        val slides = ids.mapNotNull { rels.path(it) }.filter { pkg.has(it) }
        return SlideHtml.deck(slides.size) { i, budget -> slide(pkg, slides[i], budget) }
    }

    private fun slide(pkg: DocPackage, path: String, budget: Int): Pair<SlideContent, Boolean> {
        val out = HtmlOut(budget)
        val handler = SlideHandler(out, OoxmlRels.of(pkg, path))
        pkg.read(path) { XmlScan.parse(it, handler) }
        return SlideContent(handler.title.toString().trim().ifEmpty { null }, out.finish()) to out.truncated
    }

    private enum class Role { Title, Body, Plain, Skip }

    private class SlideHandler(val out: HtmlOut, val rels: OoxmlRels) : XmlHandler() {
        val title = DocBuffer()
        private var role = Role.Plain
        private var table = 0
        private var level = 0
        private var bullet: Boolean? = null
        private var numbered = false
        private val para = DocBuffer()
        private var paraText = false
        private var run = RunStyle()
        private var inText = false
        private var alt: String? = null

        override fun start(name: String, attrs: Attrs) {
            when (name) {
                "sp" -> role = Role.Plain
                "ph" -> role = when (attrs["type"]) {
                    "title", "ctrTitle" -> Role.Title
                    "subTitle" -> Role.Plain
                    "dt", "ftr", "sldNum", "hdr" -> Role.Skip
                    else -> Role.Body
                }
                "cNvPr" -> alt = attrs["descr"]
                "tbl" -> { out.closeLists(); out.raw("<table>"); table++ }
                "tr" -> out.raw("<tr>")
                "tc" -> out.raw(attrs["gridSpan"]?.toIntOrNull()?.takeIf { it > 1 }?.let { "<td colspan=\"${it.coerceAtMost(64)}\">" } ?: "<td>")
                "p" -> { para.setLength(0); paraText = false; level = 0; bullet = null; numbered = false }
                "pPr" -> level = attrs["lvl"]?.toIntOrNull() ?: 0
                "buNone" -> bullet = false
                "buChar" -> bullet = true
                "buAutoNum" -> { bullet = true; numbered = true }
                "r", "fld" -> run = RunStyle()
                "rPr" -> run = RunStyle(
                    bold = attrs["b"] == "1" || attrs["b"] == "true",
                    italic = attrs["i"] == "1" || attrs["i"] == "true",
                    underline = attrs["u"].let { it != null && it != "none" },
                    strike = attrs["strike"].let { it != null && it != "noStrike" },
                    sup = (attrs["baseline"]?.toIntOrNull() ?: 0) > 0,
                    sub = (attrs["baseline"]?.toIntOrNull() ?: 0) < 0,
                )
                "t" -> inText = true
                "br" -> para.append("<br>")
                "blip" -> rels.path(attrs.rel("embed"))?.let { path ->
                    if (role != Role.Skip) { out.closeLists(); out.raw("<figure>${imageHtml(path, alt)}</figure>") }
                }
            }
        }

        override fun end(name: String) {
            when (name) {
                "t" -> inText = false
                "p" -> endParagraph()
                "tc" -> out.raw("</td>")
                "tr" -> out.raw("</tr>")
                "tbl" -> { out.raw("</table>"); table-- }
                "sp" -> { out.closeLists(); role = Role.Plain }
            }
        }

        override fun text(s: String) {
            if (!inText || role == Role.Skip) return
            if (role == Role.Title && table == 0) {
                title.append(s)
            } else {
                para.append(run.wrap(esc(s)))
                if (s.isNotBlank()) paraText = true
            }
        }

        private fun endParagraph() {
            if (role == Role.Title && table == 0) { if (title.isNotEmpty()) title.append(' '); return }
            if (!paraText || role == Role.Skip) return
            val html = para.toString()
            // Body placeholders are bulleted by the slide master unless a paragraph turns it off.
            val bulleted = table == 0 && (bullet ?: (role == Role.Body))
            if (bulleted) {
                out.listItem(level, numbered, html)
            } else {
                out.closeLists()
                out.raw("<p>$html</p>")
            }
        }
    }
}
