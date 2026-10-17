package com.fileviewer.app.viewers.data.csv

/** Guesses the delimiter and whether the first row names the columns. */
object CsvSniffer {
    private val candidates = charArrayOf(',', '\t', ';', '|')
    private const val SAMPLE_CHARS = 64 * 1024
    private const val SAMPLE_RECORDS = 30

    /**
     * The candidate that splits the most records into the same number (more than one) of fields.
     * Ties go to [preferred] (from the extension), then to comma, tab, semicolon, pipe.
     */
    fun delimiter(text: String, preferred: Char? = null): Char {
        val counts = Array(candidates.size) { ArrayList<Int>() }
        val current = IntArray(candidates.size)
        var inQuotes = false
        var records = 0
        var content = false
        val end = minOf(text.length, SAMPLE_CHARS)

        fun finishRecord() {
            if (content) {
                for (k in candidates.indices) counts[k] += current[k]
                records++
            }
            current.fill(0)
            content = false
        }

        var i = 0
        while (i < end && records < SAMPLE_RECORDS) {
            val c = text[i]
            if (c == '"') { inQuotes = !inQuotes; content = true }
            else if (!inQuotes && (c == '\n' || c == '\r')) finishRecord()
            else {
                content = true
                if (!inQuotes) candidates.indexOf(c).takeIf { it >= 0 }?.let { current[it]++ }
            }
            i++
        }
        if (records < SAMPLE_RECORDS && i == end) finishRecord()

        var best = preferred ?: ','
        var bestScore = 0.0
        val order = candidates.sortedBy { if (it == preferred) -1 else candidates.indexOf(it) }
        for (c in order) {
            val list = counts[candidates.indexOf(c)]
            if (list.isEmpty()) continue
            val mode = list.groupingBy { it }.eachCount().filterKeys { it > 0 }.maxByOrNull { it.value } ?: continue
            val score = mode.value.toDouble() / list.size + 0.001 * minOf(mode.key, 10)
            if (score > bestScore + 1e-9) { bestScore = score; best = c }
        }
        return best
    }

    /** True when the first row is all text and no cell in it looks like a number, date or empty slot. */
    fun looksLikeHeader(rows: List<List<String>>): Boolean {
        val first = rows.firstOrNull() ?: return false
        if (rows.size < 2 || first.any { it.isBlank() }) return false
        if (first.any { isNumeric(it) }) return false
        return first.map { it.trim().lowercase() }.toSet().size == first.size
    }

    fun isNumeric(s: String): Boolean = s.trim().let { it.isNotEmpty() && it.toDoubleOrNull() != null }

    fun nameOf(delimiter: Char): String = when (delimiter) {
        ',' -> "Comma"; '\t' -> "Tab"; ';' -> "Semicolon"; '|' -> "Pipe"; else -> "'$delimiter'"
    }

    val choices: List<Char> get() = candidates.toList()
}
