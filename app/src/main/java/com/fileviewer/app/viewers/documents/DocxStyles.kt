package com.fileviewer.app.viewers.documents

/** Which paragraph styles are headings (styles.xml) and which lists are numbered (numbering.xml). */
class DocxStyles(private val headings: Map<String, Int>, private val numbering: Map<String, Map<Int, Boolean>>) {

    /** 1..6 for a heading style, else null. */
    fun headingLevel(styleId: String?): Int? = styleId?.let { headings[it] ?: guess(it) }

    /** True when list [numId] at [level] is numbered rather than bulleted. */
    fun ordered(numId: String, level: Int): Boolean = numbering[numId]?.get(level) ?: false

    private fun guess(styleId: String): Int? =
        HEADING_ID.matchEntire(styleId)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 6) ?: if (styleId.equals("Title", true)) 1 else null

    companion object {
        private val HEADING_ID = Regex("(?i)heading\\s*(\\d)")
        private val HEADING_NAME = Regex("(?i)heading\\s*(\\d)")

        fun load(pkg: DocPackage, stylesPath: String?, numberingPath: String?): DocxStyles =
            DocxStyles(stylesPath?.let { readStyles(pkg, it) } ?: emptyMap(), numberingPath?.let { readNumbering(pkg, it) } ?: emptyMap())

        private fun readStyles(pkg: DocPackage, path: String): Map<String, Int> {
            class Style(var name: String? = null, var outline: Int? = null, var basedOn: String? = null)
            val styles = HashMap<String, Style>()
            pkg.readIfPresent(path) { input ->
                XmlScan.parse(input, object : XmlHandler() {
                    var current: Style? = null
                    override fun start(name: String, attrs: Attrs) {
                        when (name) {
                            "style" -> current = if (attrs["type"] == "paragraph") Style().also { s -> attrs["styleId"]?.let { styles[it] = s } } else null
                            "name" -> current?.name = attrs["val"]
                            "basedOn" -> current?.basedOn = attrs["val"]
                            "outlineLvl" -> current?.outline = attrs["val"]?.toIntOrNull()
                        }
                    }
                    override fun end(name: String) { if (name == "style") current = null }
                })
            }
            fun level(id: String, depth: Int): Int? {
                val s = styles[id] ?: return null
                s.name?.let { n -> HEADING_NAME.matchEntire(n)?.groupValues?.get(1)?.toIntOrNull()?.let { return it.coerceIn(1, 6) } }
                if (s.name.equals("Title", true)) return 1
                s.outline?.takeIf { it in 0..5 }?.let { return it + 1 }
                return if (depth < 8) s.basedOn?.let { level(it, depth + 1) } else null
            }
            return styles.keys.mapNotNull { id -> level(id, 0)?.let { id to it } }.toMap()
        }

        private fun readNumbering(pkg: DocPackage, path: String): Map<String, Map<Int, Boolean>> {
            val abstract = HashMap<String, HashMap<Int, Boolean>>()
            val nums = HashMap<String, String>()
            pkg.readIfPresent(path) { input ->
                XmlScan.parse(input, object : XmlHandler() {
                    var abstractId: String? = null
                    var numId: String? = null
                    var level = 0
                    override fun start(name: String, attrs: Attrs) {
                        when (name) {
                            "abstractNum" -> abstractId = attrs["abstractNumId"]
                            "lvl" -> level = attrs["ilvl"]?.toIntOrNull() ?: 0
                            "numFmt" -> abstractId?.let { id ->
                                val fmt = attrs["val"] ?: "bullet"
                                abstract.getOrPut(id) { HashMap() }[level] = fmt != "bullet" && fmt != "none"
                            }
                            "num" -> numId = attrs["numId"]
                            "abstractNumId" -> numId?.let { n -> attrs["val"]?.let { nums[n] = it } }
                        }
                    }
                    override fun end(name: String) {
                        if (name == "abstractNum") abstractId = null
                        if (name == "num") numId = null
                    }
                })
            }
            return nums.mapValues { (_, a) -> abstract[a] ?: emptyMap() }
        }
    }
}
