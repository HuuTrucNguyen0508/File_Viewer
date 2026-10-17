package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.Limits

/** Lays parsed slides out as cards, each with a small title block giving its place in the deck. */
object SlideHtml {

    /**
     * [read] parses slide i with an output capped at the HTML budget left; slides after the
     * budget runs out are dropped and noted.
     */
    fun deck(count: Int, read: (index: Int, budget: Int) -> Pair<SlideContent, Boolean>): PagePreview {
        val shown = minOf(count, Limits.MAX_DOC_SECTIONS)
        val sb = StringBuilder()
        var cut = count > shown
        for (i in 0 until shown) {
            val budget = Limits.MAX_DOC_HTML_CHARS - sb.length
            if (budget <= 0) { cut = true; break }
            val (slide, truncated) = read(i, budget)
            sb.append("<section class=\"slide\"><div class=\"block\">Slide <b>${i + 1}</b> of $count</div>")
            slide.title?.takeIf { it.isNotBlank() }?.let { sb.append("<h2>").append(esc(it)).append("</h2>") }
            sb.append(slide.bodyHtml.ifBlank { "<p class=\"empty\">No text on this slide.</p>" })
            sb.append("</section>")
            if (truncated) { cut = true; break }
        }
        if (count == 0) sb.append("<p class=\"empty\">This presentation has no slides.</p>")
        return PagePreview(sb.toString(), PageKind.Slides, listOfNotNull(CUT_NOTE.takeIf { cut }))
    }
}
