package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.Limits

/** Excel (XLSX) to one table per sheet: shared and inline strings, numbers, booleans, errors, basic dates. */
object XlsxParser {

    fun parse(pkg: DocPackage): SheetsPreview {
        val book = OoxmlRels.of(pkg, "").byType("officeDocument") ?: "xl/workbook.xml"
        val rels = OoxmlRels.of(pkg, book)
        val info = workbook(pkg, book)
        val strings = rels.byType("sharedStrings")?.let { sharedStrings(pkg, it) } ?: emptyList()
        val formats = rels.byType("styles")?.let { styles(pkg, it, info.date1904) } ?: XlsxFormats(emptyMap(), emptyList(), info.date1904)
        val notes = ArrayList<String>()
        if (info.sheets.size > Limits.MAX_DOC_SECTIONS) notes += "Only the first ${Limits.MAX_DOC_SECTIONS} sheets are shown."
        var budget = Limits.MAX_DOC_HTML_CHARS
        val sheets = buildList {
            for ((name, relId) in info.sheets.take(Limits.MAX_DOC_SECTIONS)) {
                if (budget <= 0) { notes += CUT_NOTE; break }
                val path = rels.path(relId)
                val sheet = if (path == null || !pkg.has(path)) {
                    Sheet(name, "<p class=\"empty\">This sheet has no cells the preview can show.</p>", false)
                } else {
                    val grid = SheetGrid()
                    pkg.read(path) { XmlScan.parse(it, SheetHandler(grid, strings, formats)) }
                    val html = grid.toHtml(budget)
                    if (grid.htmlTruncated && CUT_NOTE !in notes) notes += CUT_NOTE
                    Sheet(name, html, grid.truncated)
                }
                add(sheet)
                budget -= sheet.html.length
            }
        }
        if (sheets.any { it.truncated }) {
            notes += "Large sheets show their first ${Limits.MAX_SHEET_ROWS} rows and ${Limits.MAX_SHEET_COLS} columns."
        }
        return SheetsPreview(sheets, notes)
    }

    private class WorkbookInfo(val sheets: List<Pair<String, String?>>, val date1904: Boolean)

    private fun workbook(pkg: DocPackage, path: String): WorkbookInfo {
        val sheets = ArrayList<Pair<String, String?>>()
        var date1904 = false
        pkg.read(path) { input ->
            XmlScan.parse(input, object : XmlHandler() {
                override fun start(name: String, attrs: Attrs) {
                    when (name) {
                        "workbookPr" -> date1904 = attrs["date1904"].let { it == "1" || it == "true" }
                        "sheet" -> {
                            val hidden = attrs["state"].let { it == "hidden" || it == "veryHidden" }
                            val title = (attrs["name"] ?: "Sheet ${sheets.size + 1}") + if (hidden) " (hidden)" else ""
                            sheets += title to attrs.rel("id")
                        }
                    }
                }
            })
        }
        return WorkbookInfo(sheets, date1904)
    }

    private fun sharedStrings(pkg: DocPackage, path: String): List<String> {
        val list = ArrayList<String>()
        pkg.read(path) { input ->
            XmlScan.parse(input, object : XmlHandler() {
                val sb = DocBuffer()
                var inT = false
                var phonetic = 0
                override fun start(name: String, attrs: Attrs) {
                    when (name) {
                        "si" -> sb.setLength(0)
                        "t" -> inT = phonetic == 0
                        "rPh" -> phonetic++
                    }
                }
                override fun end(name: String) {
                    when (name) {
                        "t" -> inT = false
                        "rPh" -> phonetic--
                        "si" -> {
                            list += sb.toString()
                            if (list.size >= Limits.MAX_SHARED_STRINGS) throw StopParsing()
                        }
                    }
                }
                override fun text(s: String) { if (inT) sb.append(s) }
            })
        }
        return list
    }

    private fun styles(pkg: DocPackage, path: String, date1904: Boolean): XlsxFormats {
        val codes = HashMap<Int, String>()
        val xfs = ArrayList<Int>()
        pkg.read(path) { input ->
            XmlScan.parse(input, object : XmlHandler() {
                var inCellXfs = false
                override fun start(name: String, attrs: Attrs) {
                    when (name) {
                        "numFmt" -> attrs["numFmtId"]?.toIntOrNull()?.let { id -> attrs["formatCode"]?.let { codes[id] = it } }
                        "cellXfs" -> inCellXfs = true
                        "xf" -> if (inCellXfs) xfs += attrs["numFmtId"]?.toIntOrNull() ?: 0
                    }
                }
                override fun end(name: String) { if (name == "cellXfs") inCellXfs = false }
            })
        }
        return XlsxFormats(codes, xfs, date1904)
    }

    private class SheetHandler(val grid: SheetGrid, val strings: List<String>, val formats: XlsxFormats) : XmlHandler() {
        private var row = 0
        private var col = -1
        private var type: String? = null
        private var style: Int? = null
        private val value = DocBuffer()
        private var inValue = false
        private var inInline = false

        override fun start(name: String, attrs: Attrs) {
            when (name) {
                "row" -> { row = attrs["r"]?.toIntOrNull() ?: (row + 1); col = -1 }
                "c" -> {
                    col = attrs["r"]?.let { SheetGrid.columnIndex(it) } ?: (col + 1)
                    attrs["r"]?.let { SheetGrid.rowNumber(it) }?.let { row = it }
                    type = attrs["t"]
                    style = attrs["s"]?.toIntOrNull()
                    value.setLength(0)
                }
                "v" -> inValue = true
                "is" -> inInline = true
                "t" -> if (inInline) inValue = true
            }
        }

        override fun end(name: String) {
            when (name) {
                "v" -> inValue = false
                "t" -> if (inInline) inValue = false
                "is" -> inInline = false
                "c" -> if (!grid.set(row, col, cell())) throw StopParsing()
                "sheetData" -> throw StopParsing()
            }
        }

        override fun text(s: String) { if (inValue) value.append(s) }

        private fun cell(): GridCell {
            val raw = value.toString()
            return when (type) {
                "s" -> GridCell(raw.trim().toIntOrNull()?.let { strings.getOrNull(it) } ?: "", false)
                "str", "inlineStr", "d" -> GridCell(raw, false)
                "b" -> GridCell(when (raw.trim()) { "" -> ""; "1" -> "TRUE"; else -> "FALSE" }, false)
                "e" -> GridCell(raw, false)
                else -> if (raw.isEmpty()) GridCell("", true) else GridCell(formats.display(raw, style), true)
            }
        }
    }
}
