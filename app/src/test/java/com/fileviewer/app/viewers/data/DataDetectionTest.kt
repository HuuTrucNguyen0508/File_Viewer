package com.fileviewer.app.viewers.data

import com.fileviewer.app.BytesSource
import com.fileviewer.app.detect.CodeLanguages
import com.fileviewer.app.detect.DataFormat
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.detect.FileTypeDetector
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewer.ViewerRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataDetectionTest {
    private fun detect(name: String, text: String) = FileTypeDetector.detect(BytesSource(name, text.toByteArray()))
    private fun viewers(kind: FileKind) = ViewerRegistry.viewersFor(kind, 0, 34)
    private fun data(f: DataFormat) = FileKind.Data(f)

    @Test fun extensionsRouteToDataFormats() {
        assertEquals(data(DataFormat.MARKDOWN), detect("page.mdx", "# Hi\n"))
        assertEquals(data(DataFormat.HTML), detect("doc.xhtml", "<html></html>"))
        assertEquals(data(DataFormat.JSONL), detect("events.ndjson", "{\"a\":1}\n"))
        assertEquals(data(DataFormat.JSONL), detect("events.jsonl", "{\"a\":1}\n"))
        assertEquals(data(DataFormat.CSV), detect("pipes.psv", "a|b\n1|2\n"))
        assertEquals(data(DataFormat.JSON), detect("map.geojson", "{\"type\":\"Point\"}"))
        assertEquals(data(DataFormat.TSV), detect("t.tsv", "a\tb\n"))
    }

    @Test fun extensionlessReadmeIsMarkdownOnlyWhenItLooksLikeMarkdown() {
        assertEquals(data(DataFormat.MARKDOWN), detect("README", "# Project\n\nSome text.\n"))
        assertEquals(data(DataFormat.MARKDOWN), detect("CHANGELOG", "Notes\n\n```\ncode\n```\n"))
        assertEquals(data(DataFormat.MARKDOWN), detect("readme", "See [docs](https://example.com) for more.\n"))
        assertEquals(FileKind.Text(null, "Plain text", Charsets.UTF_8), detect("README", "Just some words.\nNothing special.\n"))
        assertEquals(FileKind.Text(null, "Plain text", Charsets.UTF_8), detect("LICENSE", "# not markdown, not a candidate\n"))
    }

    @Test fun dataViewersAndTheirAlternates() {
        assertEquals(listOf(ViewerId.Rendered, ViewerId.Code, ViewerId.Hex, ViewerId.Info), viewers(data(DataFormat.MARKDOWN)))
        assertEquals(listOf(ViewerId.Rendered, ViewerId.Code, ViewerId.Hex, ViewerId.Info), viewers(data(DataFormat.HTML)))
        assertEquals(listOf(ViewerId.Tree, ViewerId.Code, ViewerId.Hex, ViewerId.Info), viewers(data(DataFormat.JSON)))
        assertEquals(listOf(ViewerId.Tree, ViewerId.Code, ViewerId.Hex, ViewerId.Info), viewers(data(DataFormat.JSONL)))
        assertEquals(listOf(ViewerId.Table, ViewerId.Text, ViewerId.Hex, ViewerId.Info), viewers(data(DataFormat.CSV)))
        assertEquals(listOf(ViewerId.Table, ViewerId.Text, ViewerId.Hex, ViewerId.Info), viewers(data(DataFormat.TSV)))
        assertEquals(listOf(ViewerId.Database, ViewerId.Text, ViewerId.Hex, ViewerId.Info), viewers(data(DataFormat.SQLITE)))
        assertEquals(listOf(ViewerId.Code, ViewerId.Hex, ViewerId.Info), viewers(data(DataFormat.XML)))
        assertEquals(listOf(ViewerId.Code, ViewerId.Hex, ViewerId.Info), viewers(data(DataFormat.YAML)))
    }

    @Test fun sqliteNoLongerCarriesAPlannedNote() {
        assertNull(ViewerRegistry.noteFor(data(DataFormat.SQLITE)))
    }

    @Test fun jsonConfigVariantsStayInCode() {
        assertEquals(listOf(ViewerId.Code, ViewerId.Hex, ViewerId.Info), viewers(CodeLanguages.text("jsonc")))
    }
}
