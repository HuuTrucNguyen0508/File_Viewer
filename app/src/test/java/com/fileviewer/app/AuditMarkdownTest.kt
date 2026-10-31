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
    @Test fun midLineMarkersAndLongOrdinaryMarkupRender() {
        for (shape in listOf("words " + "> ".repeat(65), "words " + " - ".repeat(65),
            "*ordinary* ".repeat(5000))) assertTrue(MarkdownHtml.render(shape).isNotEmpty())
    }
    @Test fun orderedAndMixedPrefixNestingIsBounded() {
        for (prefix in listOf("1. ", "> - 1. "))
            assertThrows(LimitExceededException::class.java) { MarkdownHtml.render(prefix.repeat(65) + "a") }
    }
    @Test fun oneStrayPipeDoesNotTurnProseIntoATable() {
        assertTrue(MarkdownHtml.render("a|b\n" + "ordinary prose\n".repeat(50000)).isNotEmpty())
    }
    @Test fun budgetMessageUsesWorkUnits() {
        val error = assertThrows(LimitExceededException::class.java) { MarkdownHtml.render("- a\n".repeat(30000)) }
        assertTrue(error.message!!.contains("work units"))
        assertFalse(error.message!!.contains("KB"))
    }
    @Test fun softBreakNodesAndRealTablePaddingAreBounded() {
        assertThrows(LimitExceededException::class.java) { MarkdownHtml.render("a\n".repeat(100000)) }
        val table = "|a".repeat(100) + "|\n" + "|-".repeat(100) + "|\n" + "|x|\n".repeat(1000)
        assertThrows(LimitExceededException::class.java) { MarkdownHtml.render(table) }
    }
    @Test(timeout = 10000) fun nearBudgetAdversarialShapesComplete() {
        for (shape in listOf("*a* ".repeat(179900), "- a\n".repeat(9728),
            "[".repeat(359900), "<".repeat(359900), "`".repeat(359900)))
            assertTrue(MarkdownHtml.render(shape).isNotEmpty())
    }
    @Test fun markdownContainersAreBounded() {
        assertThrows(LimitExceededException::class.java) { MarkdownHtml.render("- a\n".repeat(30000)) }
    }

    @Test fun singleLineInlineAndNestedContainersAreBounded() {
        for (shape in listOf("*a*".repeat(10000000), "[a]".repeat(10000000), "> ".repeat(10000), "- ".repeat(10000))) {
            assertThrows(LimitExceededException::class.java) { MarkdownHtml.render(shape) }
        }
    }
    @Test fun tablePaddingAmplificationIsBudgeted() {
        val table = "|a".repeat(5000) + "|\n" + "|-".repeat(5000) + "|\n" + "|x|\n".repeat(5000)
        assertThrows(LimitExceededException::class.java) { MarkdownHtml.render(table) }
    }
    @Test fun pipeFreeLazyTableRowsAreAlsoBudgeted() {
        val text = "|a".repeat(100) + "|\n" + "|-".repeat(100) + "|\n" + "ordinary prose\n".repeat(50000)
        assertThrows(LimitExceededException::class.java) { MarkdownHtml.render(text) }
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
