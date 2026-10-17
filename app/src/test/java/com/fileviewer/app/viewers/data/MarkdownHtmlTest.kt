package com.fileviewer.app.viewers.data

import com.fileviewer.app.viewers.data.markdown.MarkdownHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownHtmlTest {
    private fun render(md: String) = MarkdownHtml.render(md)

    @Test fun rendersHeadingsEmphasisAndFencedCode() {
        val html = render("# Title\n\nSome *emphasis* and **bold**.\n\n```kotlin\nval x = 1 < 2\n```\n")
        assertTrue(html, "<h1>Title</h1>" in html)
        assertTrue(html, "<em>emphasis</em>" in html && "<strong>bold</strong>" in html)
        assertTrue(html, "<pre><code class=\"language-kotlin\">val x = 1 &lt; 2\n</code></pre>" in html)
    }

    @Test fun rendersTablesWithAlignment() {
        val html = render("| Name | Qty |\n|:-----|----:|\n| nut  | 3   |\n")
        assertTrue(html, "<table>" in html && "<thead>" in html && "<tbody>" in html)
        assertTrue(html, Regex("<th[^>]*>Name</th>").containsMatchIn(html))
        assertTrue(html, Regex("<td[^>]*(right)[^>]*>3</td>").containsMatchIn(html))
    }

    @Test fun rendersTaskListsAndStrikethrough() {
        val html = render("- [x] done\n- [ ] todo\n\n~~gone~~\n")
        assertEquals(2, Regex("<input type=\"checkbox\"").findAll(html).count())
        assertTrue(html, "checked" in html && "disabled" in html)
        assertTrue(html, "<del>gone</del>" in html)
    }

    @Test fun linksBareUrls() {
        val html = render("See https://example.com/page now")
        assertTrue(html, html.contains("href=\"https://example.com/page\">https://example.com/page</a>"))
    }

    @Test fun rawHtmlIsEscapedNotRun() {
        val html = render("Hello <script>alert(1)</script>\n\n<img src=x onerror=alert(1)>\n\n<div onclick=\"x()\">block</div>\n")
        assertFalse(html, "<script>" in html)
        assertFalse(html, "<img src=x" in html)
        assertFalse(html, "<div onclick" in html)
        assertTrue(html, "&lt;script&gt;" in html)
    }

    @Test fun scriptLinkTargetsAreDropped() {
        val html = render("[click](javascript:alert(1)) and [ok](https://example.com)")
        assertFalse(html, "javascript:" in html)
        assertTrue(html, "href=\"https://example.com\"" in html)
    }

    @Test fun relativeImagesKeepTheirPathForTheFolderResolver() {
        assertTrue(render("![logo](img/logo.png)").contains("<img src=\"img/logo.png\" alt=\"logo\" />"))
    }

    @Test fun frontMatterBecomesItsOwnBlock() {
        val (front, body) = MarkdownHtml.splitFrontMatter("---\ntitle: Hi\ntags: [a]\n---\n# Body\n")
        assertEquals("title: Hi\ntags: [a]", front)
        assertEquals("# Body\n", body)
        val html = render("---\ntitle: <b>Hi</b>\n---\n# Body\n")
        assertTrue(html, "<pre class=\"front-matter\">title: &lt;b&gt;Hi&lt;/b&gt;</pre>" in html)
        assertTrue(html, "<h1>Body</h1>" in html)
    }

    @Test fun horizontalRuleIsNotFrontMatter() {
        assertNull(MarkdownHtml.splitFrontMatter("---\n\nJust text\n").first)
        assertNull(MarkdownHtml.splitFrontMatter("Intro\n---\nmore\n---\n").first)
    }
}
