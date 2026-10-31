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

    fun checkComplexity(text: String) {
        var tokens = 0L
        var lines = 1L
        var lineTokens = 0
        var containers = 0
        var pipes = 0
        var maxPipes = 0
        fun check() {
            if (tokens + maxPipes * lines > Limits.MAX_MARKDOWN_COMPLEXITY ||
                lineTokens > Limits.MAX_MARKDOWN_LINE_TOKENS || containers > Limits.MAX_MARKDOWN_CONTAINERS)
                throw LimitExceededException("Markdown complexity", Limits.MAX_MARKDOWN_COMPLEXITY.toLong())
        }
        for (i in text.indices) {
            val ch = text[i]
            if (ch == '\n') {
                tokens++; lines++; lineTokens = 0; containers = 0; pipes = 0
            } else {
                // Include autolink punctuation and escapes as well as CommonMark delimiters.
                if (ch in "*_~`[]<>!#|\\:/.@&;=-") { tokens++; lineTokens++ }
                if (ch == '|' ) { pipes++; maxPipes = maxOf(maxPipes, pipes) }
                if (ch == '>' || (ch in "-+*" && text.getOrNull(i + 1) == ' ')) containers++
            }
            check()
        }
    }

    private class BoundedHtml : Appendable {
        private val text = StringBuilder()
        override fun append(csq: CharSequence?): Appendable = append(csq ?: "null", 0, csq?.length ?: 4)
        override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
            if (end - start > Limits.MAX_MARKDOWN_HTML_CHARS - text.length)
                throw LimitExceededException("Markdown HTML", Limits.MAX_MARKDOWN_HTML_CHARS.toLong())
            text.append(csq ?: "null", start, end)
            return this
        }
        override fun append(c: Char): Appendable {
            if (text.length >= Limits.MAX_MARKDOWN_HTML_CHARS)
                throw LimitExceededException("Markdown HTML", Limits.MAX_MARKDOWN_HTML_CHARS.toLong())
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
