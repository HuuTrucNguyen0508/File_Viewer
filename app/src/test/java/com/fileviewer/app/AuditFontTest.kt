package com.fileviewer.app

import com.fileviewer.app.source.*
import com.fileviewer.app.viewers.archive.ArchiveTree
import com.fileviewer.app.viewers.misc.font.NameTable
import com.fileviewer.app.viewers.misc.FontFixtures
import com.fileviewer.app.viewers.hex.HexPaging
import com.fileviewer.app.viewers.data.markdown.MarkdownHtml
import org.junit.Assert.*
import org.junit.Test

class AuditFontTest {
    @Test fun fontDisplayNameIsBounded() {
        val table = FontFixtures.nameTable(FontFixtures.NameRecord(3, 1, 0x409, 1, "x".repeat(30000)))
        assertTrue(NameTable.parse(table).family!!.length <= 4096)
    }
}
