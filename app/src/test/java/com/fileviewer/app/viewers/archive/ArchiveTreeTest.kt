package com.fileviewer.app.viewers.archive

import com.fileviewer.app.source.ArchiveEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveTreeTest {
    private fun file(path: String) = ArchiveEntry(path, false, 1, null, null)
    private fun dir(path: String) = ArchiveEntry(path, true, null, null, null)

    @Test fun impliedFoldersAndOrdering() {
        val tree = ArchiveTree(listOf(file("b.txt"), file("src/z.kt"), file("src/a/b.kt"), dir("docs/"), file("A.md")))
        assertEquals(listOf("docs", "src", "A.md", "b.txt"), tree.root.sortedChildren().map { it.name })
        val src = tree.find("src")!!
        assertTrue(src.isDirectory)
        assertEquals(listOf("a", "z.kt"), src.sortedChildren().map { it.name })
        assertEquals("src/a/b.kt", tree.find("src/a/b.kt")!!.entry!!.path)
        assertNull(tree.find("missing"))
    }

    @Test fun dotSegmentsAndLeadingSlashesAreIgnored() {
        val tree = ArchiveTree(listOf(file("./x/y.txt"), file("/z.txt")))
        assertEquals(listOf("x", "z.txt"), tree.root.sortedChildren().map { it.name })
    }
}
