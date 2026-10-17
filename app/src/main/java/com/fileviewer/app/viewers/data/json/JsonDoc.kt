package com.fileviewer.app.viewers.data.json

enum class JsonType { Object, Array, String, Number, Bool, Null }

/**
 * One value, stored in document order. A container's descendants are the nodes right after it, through [end].
 * [label] is the object key, or the array index as text; null for the root.
 */
class JsonNode(
    val type: JsonType,
    val label: String?,
    val parent: Int,
    val depth: Int,
) {
    /** Raw number, boolean or null text, or the decoded string (cut at the limit). */
    var text: String = ""
    var childCount = 0
    var end = 0
    /** A string value that was cut at the limit. */
    var clipped = false

    val isContainer get() = type == JsonType.Object || type == JsonType.Array
}

/**
 * A parsed document. [truncated] means parsing stopped at the node limit; the tree shows what came before it.
 * [lines] marks JSON Lines input, whose root is a synthetic array with one element per line.
 */
class JsonDoc(val nodes: List<JsonNode>, val truncated: Boolean, val lines: Boolean) {

    /** JSONPath-style path to node [index]: `$.user.tags[2]`, `$["odd key"]`. */
    fun path(index: Int): String {
        val parts = ArrayList<String>()
        var i = index
        while (i > 0) {
            val n = nodes[i]
            val parent = nodes[n.parent]
            parts += if (parent.type == JsonType.Array) "[${n.label}]" else key(n.label.orEmpty())
            i = n.parent
        }
        return "$" + parts.asReversed().joinToString("")
    }

    /** Indexes of the rows shown when the nodes in [expanded] are open. */
    fun visible(expanded: Set<Int>): IntArray {
        val out = IntArray(nodes.size)
        var count = 0
        var i = 0
        while (i < nodes.size) {
            out[count++] = i
            val n = nodes[i]
            i = if (n.isContainer && n.childCount > 0 && i !in expanded) n.end + 1 else i + 1
        }
        return out.copyOf(count)
    }

    /** Containers down to [depth] levels below the root, for the opening view. */
    fun containersUpTo(depth: Int): Set<Int> =
        nodes.indices.filterTo(HashSet()) { nodes[it].isContainer && nodes[it].depth <= depth }

    fun allContainers(): Set<Int> = nodes.indices.filterTo(HashSet()) { nodes[it].isContainer }

    companion object {
        private val bareKey = Regex("[A-Za-z_][A-Za-z0-9_]*")

        private fun key(k: String): String =
            if (bareKey.matches(k)) ".$k" else "[\"" + k.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]"
    }
}

/** Invalid JSON; [line] and [column] are 1-based. */
class JsonSyntaxException(val reason: String, val line: Int, val column: Int) :
    Exception("$reason (line $line, column $column)")
