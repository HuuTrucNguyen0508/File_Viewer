package com.fileviewer.app

import com.fileviewer.app.source.*
import com.fileviewer.app.viewers.archive.ArchiveTree
import com.fileviewer.app.viewers.misc.font.NameTable
import com.fileviewer.app.viewers.misc.FontFixtures
import com.fileviewer.app.viewers.hex.HexPaging
import com.fileviewer.app.viewers.data.markdown.MarkdownHtml
import org.junit.Assert.*
import org.junit.Test

class AuditTreeTest {
    @Test fun deepArchivePathIsOmitted() {
        val tree = ArchiveTree(listOf(ArchiveEntry("a/".repeat(300) + "x", false, 0, null, null)))
        assertTrue(tree.root.children.isEmpty())
    }

    @Test fun everyListedEntryFitsAlongsideItsImpliedFolders() {
        val entries = List(Limits.MAX_ARCHIVE_ENTRIES) { ArchiveEntry("d$it/f.txt", false, 0, null, null) }
        val tree = ArchiveTree(entries)
        assertEquals(0, tree.hiddenEntries)
        assertEquals(Limits.MAX_ARCHIVE_ENTRIES, tree.root.children.size)
    }
}
