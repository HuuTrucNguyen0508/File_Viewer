package com.fileviewer.app.viewers.documents

import com.fileviewer.app.detect.DocumentFormat
import com.fileviewer.app.source.Limits

/**
 * OpenDocument (ODT, ODS, ODP) from content.xml. Text documents become one page; each
 * spreadsheet table becomes a sheet; each presentation page becomes a slide card.
 */
object OdfParser {

    fun parse(pkg: DocPackage, format: DocumentFormat): DocPreview {
        val h = ContentHandler()
        pkg.read("content.xml") { XmlScan.parse(it, h) }
        h.finishOpenParts()
        return when (format) {
            DocumentFormat.ODS -> SheetsPreview(
                h.sheets,
                listOfNotNull(CUT_NOTE.takeIf { h.cut }, "Large sheets show their first ${Limits.MAX_SHEET_ROWS} rows and ${Limits.MAX_SHEET_COLS} columns.".takeIf { h.sheets.any { it.truncated } }),
            )
            DocumentFormat.ODP -> {
                val deck = SlideHtml.deck(h.slides.size) { i, _ -> h.slides[i] to false }
                if (h.cut) deck.copy(notes = listOf(CUT_NOTE)) else deck
            }
            else -> PagePreview(h.text.finish(), PageKind.Text, listOfNotNull(CUT_NOTE.takeIf { h.text.truncated }))
        }
    }

    private val SKIPPED = setOf("note", "annotation", "tracked-changes", "notes", "forms", "object", "event-listeners")

    private class Para(val heading: Int?, val base: RunStyle) {
        val html = DocBuffer()
        val plain = DocBuffer()
        var visible = false
    }

    private class ContentHandler : XmlHandler() {
        val text = HtmlOut()
        val sheets = ArrayList<Sheet>()
        val slides = ArrayList<SlideContent>()
        var cut = false

        private val styles = OdfStyles()
        private var inStyles = 0
        private var skip = 0
        private var mode = DocumentFormat.ODT
        private var out = text
        private val paras = ArrayList<Para>()
        private val spans = ArrayList<RunStyle>()
        private val lists = ArrayList<String?>()
        private val deferred = DocBuffer()
        private var frameHasImage = false

        // Spreadsheet state.
        private var grid: SheetGrid? = null
        private var sheetName = ""
        private var sheetBudget = Limits.MAX_DOC_HTML_CHARS
        private var sheetFull = false
        private var row = 1
        private var rowRepeat = 1
        private var col = 0
        private val rowCells = ArrayList<Pair<Int, GridCell>>()
        private var cell: CellState? = null

        // Presentation state.
        private var slideOut: HtmlOut? = null
        private val slideTitle = DocBuffer()
        private var titleFrame = 0
        private var slideBudget = Limits.MAX_DOC_HTML_CHARS

        private class CellState(val repeat: Int, val numeric: Boolean, val value: String?) {
            val text = DocBuffer()
        }

        override fun start(name: String, attrs: Attrs) {
            if (skip > 0 || name in SKIPPED) { skip++; return }
            if (inStyles > 0 || name == "automatic-styles") { inStyles++; styles.start(name, attrs); return }
            when (name) {
                "spreadsheet" -> mode = DocumentFormat.ODS
                "presentation" -> mode = DocumentFormat.ODP
                "page" -> if (mode == DocumentFormat.ODP) startSlide()
                "frame" -> {
                    frameHasImage = false
                    if (attrs["class"] == "title" || titleFrame > 0) titleFrame++
                }
                "table" -> if (mode == DocumentFormat.ODS && grid == null) startSheet(attrs["name"]) else { closeLists(); out.raw("<table>") }
                "table-row" -> if (grid != null) { rowRepeat = attrs["number-rows-repeated"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1; col = 0; rowCells.clear() } else out.raw("<tr>")
                "table-cell", "covered-table-cell" -> if (grid != null) startCell(attrs) else if (name == "table-cell") {
                    val span = attrs["number-columns-spanned"]?.toIntOrNull()?.coerceIn(1, 64) ?: 1
                    out.raw(if (span > 1) "<td colspan=\"$span\">" else "<td>")
                }
                "p", "h" -> paras += Para(
                    heading = if (name == "h") (attrs["outline-level"]?.toIntOrNull() ?: 1).coerceIn(1, 6) else null,
                    base = styles.run(attrs["style-name"]) ?: RunStyle(),
                )
                "span" -> spans += styles.run(attrs["style-name"]) ?: (spans.lastOrNull() ?: paras.lastOrNull()?.base ?: RunStyle())
                "a" -> paras.lastOrNull()?.html?.append("<span class=\"link\">")
                "s" -> append(" ".repeat(attrs["c"]?.toIntOrNull()?.coerceIn(1, 64) ?: 1), "&nbsp;".repeat(attrs["c"]?.toIntOrNull()?.coerceIn(1, 64) ?: 1))
                "tab" -> append("\t", " ")
                "line-break" -> append("\n", "<br>")
                "list" -> lists += attrs["style-name"] ?: lists.lastOrNull()
                "image" -> if (!frameHasImage) {
                    frameHasImage = true
                    image(attrs["href"])
                }
            }
        }

        override fun end(name: String) {
            if (skip > 0) { skip--; return }
            if (inStyles > 0) { inStyles--; styles.end(name); return }
            when (name) {
                "page" -> if (mode == DocumentFormat.ODP) endSlide()
                "frame" -> if (titleFrame > 0) titleFrame--
                "table" -> if (grid != null) endSheet() else out.raw("</table>")
                "table-row" -> if (grid != null) endRow() else out.raw("</tr>")
                "table-cell", "covered-table-cell" -> if (grid != null) endCell() else if (name == "table-cell") { closeLists(); out.raw("</td>") }
                "p", "h" -> endParagraph()
                "span" -> if (spans.isNotEmpty()) spans.removeAt(spans.lastIndex)
                "a" -> paras.lastOrNull()?.html?.append("</span>")
                "list" -> { lists.removeAt(lists.lastIndex); if (lists.isEmpty()) closeLists() }
            }
        }

        override fun text(s: String) {
            if (skip > 0 || inStyles > 0 || paras.isEmpty()) return
            val style = spans.lastOrNull() ?: paras.last().base
            append(s, style.wrap(esc(s)))
        }

        private fun append(plain: String, html: String) {
            val p = paras.lastOrNull() ?: return
            p.plain.append(plain)
            p.html.append(html)
            if (plain.isNotBlank()) p.visible = true
        }

        private fun image(href: String?) {
            val path = href?.let { PackagePaths.resolve("", it) } ?: return
            if (grid != null || titleFrame > 0) return
            val html = imageHtml(path, null)
            val p = paras.lastOrNull()
            if (p != null) { p.html.append(html); p.visible = true } else { closeLists(); out.raw("<figure>$html</figure>") }
        }

        private fun endParagraph() {
            val p = paras.removeAt(paras.lastIndex)
            val c = cell
            if (c != null && paras.isEmpty()) {
                if (c.text.isNotEmpty()) c.text.append('\n')
                c.text.append(p.plain)
                return
            }
            if (titleFrame > 0) {
                if (slideTitle.isNotEmpty()) slideTitle.append(' ')
                slideTitle.append(p.plain.toString().trim())
                return
            }
            if (!p.visible) return
            val html = p.html.toString()
            if (paras.isNotEmpty()) {
                deferred.append(p.heading?.let { "<h$it>$html</h$it>" } ?: "<p>$html</p>")
                return
            }
            when {
                p.heading != null -> { closeLists(); out.raw("<h${p.heading}>$html</h${p.heading}>") }
                lists.isNotEmpty() -> out.listItem(lists.size - 1, styles.ordered(lists.last(), lists.size - 1), html)
                else -> { closeLists(); out.raw("<p>$html</p>") }
            }
            if (deferred.isNotEmpty()) {
                out.raw(deferred.toString())
                deferred.setLength(0)
            }
        }

        private fun closeLists() = out.closeLists()

        private fun startSheet(name: String?) {
            if (sheets.size >= Limits.MAX_DOC_SECTIONS || sheetBudget <= 0) { cut = true; throw StopParsing() }
            grid = SheetGrid()
            sheetName = name ?: "Sheet ${sheets.size + 1}"
            sheetFull = false
            row = 1
        }

        private fun startCell(attrs: Attrs) {
            val type = attrs["value-type"]
            val value = attrs["value"] ?: attrs["date-value"] ?: attrs["time-value"] ?: attrs["boolean-value"] ?: attrs["string-value"]
            cell = CellState(
                repeat = attrs["number-columns-repeated"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                numeric = type == "float" || type == "percentage" || type == "currency",
                value = value,
            )
        }

        private fun endCell() {
            val c = cell ?: return
            cell = null
            val shown = c.text.toString().ifEmpty { c.value ?: "" }
            if (shown.isNotEmpty() && col.toLong() + c.repeat > Limits.MAX_SHEET_COLS) grid?.truncated = true
            if (shown.isNotEmpty() && col < Limits.MAX_SHEET_COLS) {
                for (i in 0 until minOf(c.repeat, Limits.MAX_SHEET_COLS - col)) rowCells += (col + i) to GridCell(shown, c.numeric)
            }
            col = (col.toLong() + c.repeat).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        private fun endRow() {
            val g = grid ?: return
            if (rowCells.isNotEmpty() && !sheetFull) {
                rows@ for (r in 0 until minOf(rowRepeat, Limits.MAX_SHEET_ROWS)) {
                    for ((c, value) in rowCells) if (!g.set(row + r, c, value)) { sheetFull = true; break@rows }
                }
            }
            row = (row.toLong() + rowRepeat).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        private fun endSheet() {
            val g = grid ?: return
            val html = g.toHtml(sheetBudget)
            if (g.htmlTruncated) cut = true
            sheets += Sheet(sheetName, html, g.truncated)
            sheetBudget -= html.length
            grid = null
        }

        private fun startSlide() {
            if (slides.size >= Limits.MAX_DOC_SECTIONS || slideBudget <= 0) { cut = true; throw StopParsing() }
            slideOut = HtmlOut(slideBudget).also { out = it }
            slideTitle.setLength(0)
        }

        private fun endSlide() {
            val o = slideOut ?: return
            slideOut = null
            val html = o.finish()
            slideBudget -= html.length
            slides += SlideContent(slideTitle.toString().trim().ifEmpty { null }, html)
            if (o.truncated) cut = true
            out = text
        }

        /** After an early stop, keeps the sheet or slide that was being read. */
        fun finishOpenParts() {
            if (grid != null) endSheet()
            if (slideOut != null) endSlide()
        }
    }
}
