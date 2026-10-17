package com.fileviewer.app.viewers.data.markdown

import com.fileviewer.app.viewer.html.HtmlPage
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
        val (frontMatter, body) = splitFrontMatter(markdown)
        val head = frontMatter?.let { "<pre class=\"front-matter\">${HtmlPage.escape(it)}</pre>" }.orEmpty()
        return "<style>$CSS</style>$head${renderer.render(parser.parse(body))}"
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
