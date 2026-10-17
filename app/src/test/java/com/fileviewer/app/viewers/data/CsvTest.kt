package com.fileviewer.app.viewers.data

import com.fileviewer.app.viewers.data.csv.CsvParser
import com.fileviewer.app.viewers.data.csv.CsvSniffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    private fun parse(text: String, d: Char = ',', rows: Int = 1000, cols: Int = 100, cell: Int = 1000) =
        CsvParser.parse(text, d, rows, cols, cell)

    @Test fun plainRows() {
        val t = parse("a,b,c\n1,2,3\n")
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "2", "3")), t.rows)
        assertEquals(3, t.columns)
    }

    @Test fun quotesDelimitersAndDoubledQuotes() {
        val t = parse("name,note\n\"Smith, Jo\",\"said \"\"hi\"\"\"\n")
        assertEquals(listOf("Smith, Jo", "said \"hi\""), t.rows[1])
    }

    @Test fun embeddedLineBreaksStayInTheCell() {
        val t = parse("id,text\r\n1,\"line one\r\nline two\"\r\n2,x\r\n")
        assertEquals(3, t.rows.size)
        assertEquals("line one\r\nline two", t.rows[1][1])
        assertEquals(listOf("2", "x"), t.rows[2])
    }

    @Test fun lineEndingsCrLfAndBareCr() {
        assertEquals(3, parse("a\r\nb\rc").rows.size)
    }

    @Test fun lastRowWithoutNewlineAndBlankLinesSkipped() {
        val t = parse("a,b\n\n1,2")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), t.rows)
    }

    @Test fun emptyFieldsAreKept() {
        assertEquals(listOf("", "x", "", ""), parse(",x,,\n").rows[0])
        assertEquals(listOf("", ""), parse("\"\",\n").rows[0])
    }

    @Test fun raggedRowsReportTheWidestRow() {
        val t = parse("a,b,c\n1\n1,2,3,4\n")
        assertEquals(4, t.columns)
        assertEquals(listOf("1"), t.rows[1])
    }

    @Test fun quoteInsideUnquotedFieldIsLiteral() {
        assertEquals(listOf("5\" pipe", "ok"), parse("5\" pipe,ok\n").rows[0])
    }

    @Test fun rowLimitCutsAndSaysSo() {
        val t = parse((1..10).joinToString("\n") { "r$it,x" }, rows = 4)
        assertEquals(4, t.rows.size)
        assertTrue(t.truncatedRows)
        assertFalse(parse("a\nb\nc\n", rows = 3).truncatedRows)
    }

    @Test fun columnLimitCutsAndSaysSo() {
        val t = parse("1,2,3,4,5\n", cols = 3)
        assertEquals(listOf("1", "2", "3"), t.rows[0])
        assertEquals(3, t.columns)
        assertTrue(t.truncatedColumns)
    }

    @Test fun cellLimitCutsAndMarksTheCell() {
        val t = parse("${"x".repeat(50)},short\n", cell = 10)
        assertEquals("x".repeat(10) + "\u2026", t.rows[0][0])
        assertEquals("short", t.rows[0][1])
        assertTrue(t.truncatedCells)
        assertFalse(parse("${"x".repeat(10)}\n", cell = 10).truncatedCells)
    }

    @Test fun tabDelimiter() {
        assertEquals(listOf("a b", "c"), parse("a b\tc\n", '\t').rows[0])
    }

    @Test fun sniffsCommonDelimiters() {
        assertEquals(',', CsvSniffer.delimiter("a,b,c\n1,2,3\n4,5,6\n"))
        assertEquals(';', CsvSniffer.delimiter("a;b;c\n1,5;2;3\n4,0;5;6\n"))
        assertEquals('\t', CsvSniffer.delimiter("a\tb\tc\n1\t2\t3\n"))
        assertEquals('|', CsvSniffer.delimiter("a|b|c\n1|2|3\n"))
    }

    @Test fun sniffIgnoresDelimitersInsideQuotes() {
        assertEquals(';', CsvSniffer.delimiter("\"a,b,c\";x\n\"1,2,3\";y\n"))
    }

    @Test fun sniffPrefersTheExtensionHintOnTies() {
        val tie = "a,b\tc\n1,2\t3\n"
        assertEquals(',', CsvSniffer.delimiter(tie))
        assertEquals('\t', CsvSniffer.delimiter(tie, '\t'))
        assertEquals('|', CsvSniffer.delimiter("a|b,c\n1|2,3\n", '|'))
    }

    @Test fun singleColumnFileFallsBackToComma() {
        assertEquals(',', CsvSniffer.delimiter("alpha\nbeta\ngamma\n"))
    }

    @Test fun headerDetection() {
        assertTrue(CsvSniffer.looksLikeHeader(parse("name,age\nann,31\n").rows))
        assertFalse(CsvSniffer.looksLikeHeader(parse("1,2\n3,4\n").rows))
        assertFalse(CsvSniffer.looksLikeHeader(parse("a,a\nb,c\n").rows))
        assertFalse(CsvSniffer.looksLikeHeader(parse("only,one\n").rows))
    }
}
