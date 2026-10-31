package com.fileviewer.app

import com.fileviewer.app.source.*
import com.fileviewer.app.viewers.archive.ArchiveTree
import com.fileviewer.app.viewers.misc.font.NameTable
import com.fileviewer.app.viewers.misc.FontFixtures
import com.fileviewer.app.viewers.hex.HexPaging
import com.fileviewer.app.viewers.data.markdown.MarkdownHtml
import org.junit.Assert.*
import org.junit.Test

class AuditMarkdownTest {
    @Test fun markdownContainersAreBounded() {
        assertThrows(LimitExceededException::class.java) { MarkdownHtml.render("- a\n".repeat(30000)) }
    }

    @Test fun singleLineInlineAndNestedContainersAreBounded() {
        for (shape in listOf("*a*".repeat(10000000), "[a]".repeat(10000000), "> ".repeat(10000), "- ".repeat(10000))) {
            assertThrows(LimitExceededException::class.java) { MarkdownHtml.render(shape) }
        }
    }
    @Test fun tablePaddingAmplificationIsBudgeted() {
        val table = "|a".repeat(5000) + "|\n" + "|-".repeat(5000) + "|\n" + "x\n".repeat(5000)
        assertThrows(LimitExceededException::class.java) { MarkdownHtml.render(table) }
    }
    @Test fun referenceOutputAmplificationIsBounded() {
        val text = "[a]: https://example.org/" + "x".repeat(20000) + "\n\n" + "[a] ".repeat(1000)
        assertThrows(RuntimeException::class.java) { MarkdownHtml.render(text) }
        assertTrue(MarkdownHtml.renderResult(text).isFailure)
    }
    @Test(timeout = 10000) fun unmatchedDelimitersUnderBudgetComplete() {
        for (c in listOf("[", "<", "`")) MarkdownHtml.render(c.repeat(1999))
    }
    @Test fun severalMegabytesOfOrdinaryProseStillRender() {
        assertTrue(MarkdownHtml.render(("ordinary prose with familiar words ".repeat(300) + "\n").repeat(400)).contains("ordinary prose"))
    }
}
