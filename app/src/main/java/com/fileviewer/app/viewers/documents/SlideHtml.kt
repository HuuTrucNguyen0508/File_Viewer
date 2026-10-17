package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.Limits

/** Lays parsed slides out as cards, with one output budget for the whole deck. */
object SlideHtml {
    fun deck(count: Int, read: (index: Int, budget: Int) -> Pair<SlideContent, Boolean>): PagePreview {
        val shown = minOf(count, Limits.MAX_DOC_SECTIONS)
        val out = HtmlOut()
        var cut = count > shown
        try {
            for (i in 0 until shown) {
                val budget = Limits.MAX_DOC_HTML_CHARS - out.length
                if (budget <= 0) { cut = true; break }
                val (slide, truncated) = read(i, budget)
                out.raw("<section class=\"slide\"><div class=\"block\">Slide <b>${i + 1}</b> of $count</div>")
                slide.title?.takeIf { it.isNotBlank() }?.let { out.raw("<h2>${esc(it)}</h2>") }
                out.raw(slide.bodyHtml.ifBlank { "<p class=\"empty\">No text on this slide.</p>" })
                out.raw("</section>")
                if (truncated) { cut = true; break }
            }
            if (count == 0) out.raw("<p class=\"empty\">This presentation has no slides.</p>")
        } catch (_: StopParsing) {
            cut = true
        }
        return PagePreview(out.finish(), PageKind.Slides, listOfNotNull(CUT_NOTE.takeIf { cut }))
    }
}
