package com.fileviewer.app.viewers.documents

/**
 * Document additions to the shared stylesheet (assets/html/base.css), prepended to each
 * fragment. The Document family hue marks the few things that are about the document's
 * structure: a slide's title block and the active sheet.
 */
object DocCss {
    private const val COMMON = """
html { --doc: #A3392F; } html.dark { --doc: #F08A7E; }
.link { color: var(--accent); text-decoration: underline dotted; text-underline-offset: 2px; }
.missing { display: inline-block; border: 1px dashed var(--muted); color: var(--muted); font-size: 0.85em; padding: 0.35em 0.7em; border-radius: 6px; }
.empty { color: var(--muted); }
figure { margin: 0 0 1em; }
td > p, th > p { margin: 0 0 0.4em; } td > p:last-child, th > p:last-child { margin-bottom: 0; }
"""

    private const val SLIDES = """
body { max-width: 80ch; }
.slide { background: var(--code-bg); border: 1px solid var(--rule); border-radius: 10px; padding: 14px 16px 4px; margin: 0 0 16px; overflow: hidden; }
.slide > .block { float: right; margin: 0 0 8px 12px; border: 1px solid var(--doc); color: var(--doc); border-radius: 4px; padding: 1px 8px; font-size: 12px; line-height: 20px; font-variant-numeric: tabular-nums; }
.slide > .block b { font-weight: 600; }
.slide > h2 { margin: 0 0 0.6em; font-size: 1.2em; }
.slide ul, .slide ol { padding-left: 1.3em; }
.slide li { margin: 0 0 0.3em; }
.slide img { border-radius: 4px; }
"""

    private const val SHEET = """
body { max-width: none; padding: 0 0 32px; }
table.sheet { display: table; overflow: visible; border-collapse: separate; border-spacing: 0; margin: 0; font-size: 14px; line-height: 1.35; }
.sheet th, .sheet td { border: 0; border-right: 1px solid var(--rule); border-bottom: 1px solid var(--rule); padding: 5px 8px; max-width: 30ch; white-space: pre-wrap; }
.sheet td.n { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
.sheet thead th, .sheet tbody th { position: sticky; background: var(--code-bg); color: var(--muted); font: 12px/1.35 "JBMono", monospace; font-weight: 400; }
.sheet thead th { top: 0; text-align: center; z-index: 1; min-width: 3ch; }
.sheet tbody th { left: 0; text-align: right; }
.sheet thead th.corner { left: 0; z-index: 2; }
p.empty { padding: 20px 18px; }
"""

    private const val EXTRACTED = """
body p { font: 13px/1.5 "JBMono", monospace; margin: 0 0 0.7em; white-space: pre-wrap; }
"""

    fun page(kind: PageKind, html: String): String = style(
        COMMON + when (kind) {
            PageKind.Text -> ""
            PageKind.Slides -> SLIDES
            PageKind.ExtractedText -> EXTRACTED
        },
    ) + html

    fun sheet(html: String): String = style(COMMON + SHEET) + html

    fun chapter(html: String): String = style(COMMON) + html

    private fun style(css: String) = "<style>$css</style>"
}
