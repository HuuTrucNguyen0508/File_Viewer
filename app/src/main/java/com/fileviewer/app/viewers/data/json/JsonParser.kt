package com.fileviewer.app.viewers.data.json

import com.fileviewer.app.source.Limits
import java.io.InterruptedIOException

/** Strict RFC 8259 parser that builds a flat [JsonDoc]. Recursion is bounded by the depth limit. */
object JsonParser {

    /** @throws JsonSyntaxException with the line and column of the first problem. */
    fun parse(
        text: String,
        lines: Boolean = false,
        maxNodes: Int = Limits.MAX_JSON_NODES,
        maxDepth: Int = Limits.MAX_JSON_DEPTH,
        maxString: Int = Limits.MAX_JSON_STRING_CHARS,
    ): JsonDoc = Reader(text, maxNodes, maxDepth, maxString).run(lines)

    private class NodeLimit : RuntimeException(null, null, false, false)

    private class Reader(val s: String, val maxNodes: Int, val maxDepth: Int, val maxString: Int) {
        val nodes = ArrayList<JsonNode>()
        val open = ArrayList<Int>()
        var pos = 0
        var lineEnd = Int.MAX_VALUE
        val end get() = minOf(s.length, lineEnd)

        fun run(lines: Boolean): JsonDoc {
            if (s.startsWith("\uFEFF")) pos = 1
            var truncated = false
            try {
                if (lines) readLines() else {
                    skipSpace()
                    if (pos >= end) fail("The file is empty")
                    value(null, -1, 0)
                    skipSpace()
                    if (pos < s.length) fail("Unexpected content after the JSON value")
                }
            } catch (_: NodeLimit) {
                truncated = true
                for (i in open) {
                    nodes[i].end = nodes.lastIndex
                    nodes[i].childCount = nodes.count { it.parent == i }
                }
            }
            return JsonDoc(nodes, truncated, lines)
        }

        fun readLines() {
            val root = add(JsonType.Array, "lines", -1, 0)
            open += root
            skipSpace()
            while (pos < s.length) {
                lineEnd = s.indexOf('\n', pos).let { if (it < 0) s.length else it }
                value(nodes[root].childCount.toString(), root, 1)
                nodes[root].childCount++
                skipSpace()
                if (pos < lineEnd) fail("Expected one JSON value per line")
                pos = minOf(s.length, lineEnd + 1)
                lineEnd = Int.MAX_VALUE
                skipSpace()
            }
            nodes[root].end = nodes.lastIndex
            open.clear()
        }

        fun add(type: JsonType, label: String?, parent: Int, depth: Int): Int {
            if (nodes.size >= maxNodes) throw NodeLimit()
            nodes += JsonNode(type, label, parent, depth).also { it.end = nodes.size }
            return nodes.lastIndex
        }

        fun value(label: String?, parent: Int, depth: Int) {
            checkInterrupted()
            if (depth > maxDepth) fail("Nested more than $maxDepth levels deep")
            if (pos >= end) fail(if (lineEnd < s.length) "Expected a complete JSON value on this line" else "Unexpected end of the file")
            when (val c = s[pos]) {
                '{' -> container(JsonType.Object, label, parent, depth)
                '[' -> container(JsonType.Array, label, parent, depth)
                '"' -> {
                    val i = add(JsonType.String, label, parent, depth)
                    val (str, clipped) = string()
                    nodes[i].text = str
                    nodes[i].clipped = clipped
                }
                't' -> literal("true", JsonType.Bool, label, parent, depth)
                'f' -> literal("false", JsonType.Bool, label, parent, depth)
                'n' -> literal("null", JsonType.Null, label, parent, depth)
                else -> if (c == '-' || c in '0'..'9') number(label, parent, depth) else fail("Unexpected ${describe(c)}")
            }
        }

        fun container(type: JsonType, label: String?, parent: Int, depth: Int) {
            val closer = if (type == JsonType.Object) '}' else ']'
            val index = add(type, label, parent, depth)
            open += index
            pos++
            skipSpace()
            if (pos < end && s[pos] == closer) {
                pos++
            } else {
                var count = 0
                while (true) {
                    skipSpace()
                    if (pos >= end) fail(if (lineEnd < s.length) "Expected a complete JSON value on this line" else "Unexpected end of the file")
                    val childLabel: String
                    if (type == JsonType.Object) {
                        if (s[pos] != '"') fail("Expected a quoted key but found ${describe(s[pos])}")
                        childLabel = string().first
                        skipSpace()
                        if (pos >= end) fail(if (lineEnd < s.length) "Expected a complete JSON value on this line" else "Unexpected end of the file")
                        if (s[pos] != ':') fail("Expected ':' after the key but found ${describe(s[pos])}")
                        pos++
                        skipSpace()
                    } else childLabel = count.toString()
                    value(childLabel, index, depth + 1)
                    count++
                    nodes[index].childCount = count
                    skipSpace()
                    if (pos >= end) fail(if (lineEnd < s.length) "Expected a complete JSON value on this line" else "Unexpected end of the file")
                    val c = s[pos]
                    if (c == ',') { pos++; continue }
                    if (c == closer) { pos++; break }
                    fail("Expected ',' or '$closer' but found ${describe(c)}")
                }
            }
            nodes[index].end = nodes.lastIndex
            open.removeAt(open.lastIndex)
        }

        fun literal(word: String, type: JsonType, label: String?, parent: Int, depth: Int) {
            if (!s.startsWith(word, pos)) fail("Unexpected ${describe(s[pos])}")
            val end = pos + word.length
            if (end < s.length && s[end].isLetterOrDigit()) { pos = end; fail("Unexpected ${describe(s[end])}") }
            add(type, label, parent, depth).let { nodes[it].text = word }
            pos = end
        }

        fun number(label: String?, parent: Int, depth: Int) {
            val start = pos
            if (s[pos] == '-') pos++
            if (pos >= end) fail(if (lineEnd < s.length) "Expected a complete JSON value on this line" else "Unexpected end of the file")
            when {
                s[pos] == '0' -> pos++
                s[pos] in '1'..'9' -> while (pos < end && s[pos] in '0'..'9') pos++
                else -> fail("Expected a digit but found ${describe(s[pos])}")
            }
            if (pos < end && s[pos] == '.') {
                pos++
                digits("after the decimal point")
            }
            if (pos < end && (s[pos] == 'e' || s[pos] == 'E')) {
                pos++
                if (pos < end && (s[pos] == '+' || s[pos] == '-')) pos++
                digits("in the exponent")
            }
            add(JsonType.Number, label, parent, depth).let { nodes[it].text = s.substring(start, pos) }
        }

        fun digits(where: String) {
            if (pos >= s.length || s[pos] !in '0'..'9') fail("Expected a digit $where")
            while (pos < end && s[pos] in '0'..'9') pos++
        }

        /** Reads a string at [pos] (on the opening quote). Returns the text and whether it was cut. */
        fun string(): Pair<String, Boolean> {
            pos++
            val sb = StringBuilder()
            var clipped = false
            while (true) {
                checkInterrupted()
                if (pos >= end) fail("The string is never closed")
                val c = s[pos]
                when {
                    c == '"' -> { pos++; return sb.toString() to clipped }
                    c < ' ' -> fail("A line break or control character inside a string must be escaped")
                    c == '\\' -> {
                        pos++
                        if (pos >= end) fail("The string is never closed")
                        val e = s[pos]
                        val decoded: Char = when (e) {
                            '"', '\\', '/' -> e
                            'b' -> '\b'; 'f' -> '\u000C'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                            'u' -> {
                                if (pos + 4 >= s.length) fail("Incomplete \\u escape")
                                val hex = s.substring(pos + 1, pos + 5)
                                val v = hex.toIntOrNull(16)
                                if (v == null || hex.any { it == '+' || it == '-' }) fail("Invalid \\u escape")
                                pos += 4
                                v.toChar()
                            }
                            else -> fail("Invalid escape \\$e")
                        }
                        pos++
                        if (sb.length < maxString) sb.append(decoded) else clipped = true
                    }
                    else -> {
                        if (sb.length < maxString) sb.append(c) else clipped = true
                        pos++
                    }
                }
            }
        }

        fun skipSpace() {
            while (pos < end) {
                checkInterrupted()
                val c = s[pos]
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++ else break
            }
        }

        private fun checkInterrupted() {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Parse cancelled")
        }

        fun describe(c: Char): String = when {
            c == '\n' || c == '\r' -> "line break"
            c < ' ' -> "control character"
            else -> "'$c'"
        }

        fun fail(reason: String): Nothing {
            var line = 1
            var lineStart = 0
            val at = minOf(pos, s.length)
            for (i in 0 until at) if (s[i] == '\n') { line++; lineStart = i + 1 }
            throw JsonSyntaxException(reason, line, at - lineStart + 1)
        }
    }
}
