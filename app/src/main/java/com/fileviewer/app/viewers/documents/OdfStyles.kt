package com.fileviewer.app.viewers.documents

/**
 * The automatic styles of an ODF content.xml: character formatting per style name, and
 * whether each list style level is numbered. Fed by [OdfParser] while it reads the styles section.
 */
class OdfStyles {
    private val text = HashMap<String, RunStyle>()
    private val lists = HashMap<String, HashMap<Int, Boolean>>()
    private var currentStyle: String? = null
    private var currentList: String? = null

    fun run(styleName: String?): RunStyle? = styleName?.let { text[it] }

    /** [level] is 0-based; ODF levels start at 1. */
    fun ordered(listStyle: String?, level: Int): Boolean = listStyle?.let { lists[it]?.get(level + 1) } ?: false

    fun start(name: String, attrs: Attrs) {
        when (name) {
            "style" -> currentStyle = attrs["name"]
            "text-properties" -> currentStyle?.let { s ->
                text[s] = RunStyle(
                    bold = attrs["font-weight"].let { it == "bold" || (it?.toIntOrNull() ?: 0) >= 600 },
                    italic = attrs["font-style"].let { it == "italic" || it == "oblique" },
                    underline = attrs["text-underline-style"].let { it != null && it != "none" },
                    strike = attrs["text-line-through-style"].let { it != null && it != "none" },
                    sup = attrs["text-position"]?.startsWith("super") == true,
                    sub = attrs["text-position"]?.startsWith("sub") == true,
                )
            }
            "list-style" -> currentList = attrs["name"]
            "list-level-style-number" -> level(attrs, true)
            "list-level-style-bullet", "list-level-style-image" -> level(attrs, false)
        }
    }

    fun end(name: String) {
        if (name == "style") currentStyle = null
        if (name == "list-style") currentList = null
    }

    private fun level(attrs: Attrs, ordered: Boolean) {
        val list = currentList ?: return
        val level = attrs["level"]?.toIntOrNull() ?: return
        lists.getOrPut(list) { HashMap() }[level] = ordered
    }
}
