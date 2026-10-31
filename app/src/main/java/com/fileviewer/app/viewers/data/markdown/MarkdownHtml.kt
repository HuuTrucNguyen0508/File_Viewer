package com.fileviewer.app.viewers.data.markdown

import com.fileviewer.app.viewer.html.HtmlPage
import com.fileviewer.app.source.Limits
import com.fileviewer.app.source.LimitExceededException
import kotlinx.coroutines.CancellationException
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Markdown to an HTML fragment for the shared page wrapper. GitHub-style tables, task lists,
 * strikethrough and bare links are on. Raw HTML in the source is escaped, not run, and
 * `javascript:` style link targets are dropped.
 */
object MarkdownHtml {
    private val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
        AutolinkExtension.create(),
    )
    private val parser: Parser = Parser.builder().extensions(extensions).build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .build()

    fun render(markdown: String): String {
        checkComplexity(markdown)
        val (frontMatter, body) = splitFrontMatter(markdown)
        val head = frontMatter?.let { "<pre class=\"front-matter\">${HtmlPage.escape(it)}</pre>" }.orEmpty()
        val out = BoundedHtml()
        out.append("<style>$CSS</style>").append(head)
        renderer.render(parser.parse(body), out)
        return out.toString()
    }

    /** Owns parser/renderer failures, including VM resource failures; cancellation belongs to the caller. */
    fun renderResult(markdown: String): Result<String> = try {
        Result.success(render(markdown))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    } catch (e: OutOfMemoryError) {
        Result.failure(e)
    } catch (e: StackOverflowError) {
        Result.failure(e)
    }

    fun checkComplexity(text: String) { complexity(text) }

    /** Conservative parser-work estimate, not a byte count. Table padding is charged per body row. */
    fun complexity(text: String): Long {
        var cost = 0L
        var previousColumns = 0
        var tableColumns = 0
        for (line in text.lineSequence()) {
            cost += Limits.MARKDOWN_LINE_COST
            val prefix = containerPrefix(line)
            if (prefix > Limits.MAX_MARKDOWN_CONTAINERS)
                throw MarkdownBudgetException("Markdown container nesting exceeds ${Limits.MAX_MARKDOWN_CONTAINERS}")
            val trimmed = line.trimStart()
            if (trimmed.isNotEmpty() && (prefix > 0 || trimmed[0] in "#`~<["))
                cost += Limits.MARKDOWN_BLOCK_COST
            var pipes = 0
            for (ch in line) {
                if (ch in "*_~`[]<>!#|\\:/.@&;=-") cost++
                if (ch == '|') pipes++
            }
            if (cost > Limits.MAX_MARKDOWN_COMPLEXITY)
                throw MarkdownBudgetException("Markdown complexity exceeds ${Limits.MAX_MARKDOWN_COMPLEXITY} work units")
            val columns = tableColumns(line)
            val delimiter = previousColumns > 0 && previousColumns == columns && pipes > 0 &&
                trimmed.any { it == '-' } && trimmed.all { it in "|:- \t\r" }
            if (delimiter) {
                tableColumns = columns
            } else if (tableColumns > 0) {
                // CommonMark also pads pipe-free lazy continuation rows until a blank or new block.
                if (trimmed.isEmpty() || trimmed == "|" || prefix > 0 || trimmed.startsWith('#')) tableColumns = 0
                else cost += tableColumns.toLong() * Limits.MARKDOWN_TABLE_CELL_COST
            }
            previousColumns = columns
            if (cost > Limits.MAX_MARKDOWN_COMPLEXITY)
                throw MarkdownBudgetException("Markdown complexity exceeds ${Limits.MAX_MARKDOWN_COMPLEXITY} work units")
        }
        return cost
    }

    private fun tableColumns(line: String): Int {
        val row = line.trim()
        if (row.isEmpty()) return 0
        var pipes = 0
        for (i in row.indices) if (row[i] == '|' && (i == 0 || row[i - 1] != '\\')) pipes++
        if (pipes == 0) return 0
        return pipes + 1 - (if (row.startsWith('|')) 1 else 0) -
            (if (row.endsWith('|') && row.getOrNull(row.lastIndex - 1) != '\\') 1 else 0)
    }

    private fun containerPrefix(line: String): Int {
        var at = 0
        var count = 0
        while (at < line.length) {
            while (at < line.length && (line[at] == ' ' || line[at] == '\t')) at++
            if (at == line.length) break
            if (line[at] == '>') { at++; count++; continue }
            if (line[at] in "-+*" && line.getOrNull(at + 1)?.isWhitespace() == true) {
                at += 2; count++; continue
            }
            val start = at
            while (at < line.length && line[at].isDigit()) at++
            if (at > start && line.getOrNull(at) in listOf('.', ')') &&
                line.getOrNull(at + 1)?.isWhitespace() == true) {
                at += 2; count++; continue
            }
            break
        }
        return count
    }

    class MarkdownBudgetException(message: String) : LimitExceededException(message)

    private class BoundedHtml : Appendable {
        private val text = StringBuilder()
        override fun append(csq: CharSequence?): Appendable = append(csq ?: "null", 0, csq?.length ?: 4)
        override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
            if (end - start > Limits.MAX_MARKDOWN_HTML_CHARS - text.length)
                throw LimitExceededException("Markdown HTML exceeds ${Limits.MAX_MARKDOWN_HTML_CHARS} characters")
            text.append(csq ?: "null", start, end)
            return this
        }
        override fun append(c: Char): Appendable {
            if (text.length >= Limits.MAX_MARKDOWN_HTML_CHARS)
                throw LimitExceededException("Markdown HTML exceeds ${Limits.MAX_MARKDOWN_HTML_CHARS} characters")
            text.append(c)
            return this
        }
        override fun toString() = text.toString()
    }

    /** A leading `---` block (Jekyll, Hugo, Obsidian) is shown as its own box instead of a rule and a heading. */
    fun splitFrontMatter(text: String): Pair<String?, String> {
        if (!text.startsWith("---")) return null to text
        val firstBreak = text.indexOf('\n')
        if (firstBreak < 0 || text.substring(3, firstBreak).isNotBlank()) return null to text
        val close = Regex("\\n---[ \\t]*(\\r?\\n|$)").find(text, firstBreak)
            ?: return null to text
        val front = text.substring(firstBreak + 1, close.range.first).trimEnd('\r')
        return front to text.substring(close.range.last + 1)
    }

    /** What the shared stylesheet doesn't cover: task lists, strikethrough, front matter. */
    private const val CSS = """
li:has(> input[type=checkbox]) { list-style: none; margin-left: -1.3em; }
input[type=checkbox] { margin: 0 0.5em 0 0; accent-color: var(--accent); }
del { color: var(--muted); }
.front-matter { color: var(--muted); border-left: 3px solid var(--accent); border-radius: 0 8px 8px 0; }
th[align=right], td[align=right] { text-align: right; }
th[align=center], td[align=center] { text-align: center; }
"""
}
