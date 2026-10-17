package com.fileviewer.app.viewers.data

import com.fileviewer.app.viewers.data.json.JsonParser
import com.fileviewer.app.viewers.data.json.JsonSyntaxException
import com.fileviewer.app.viewers.data.json.JsonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonTest {
    private val sample = """{"name":"Ann","age":31,"admin":false,"boss":null,"tags":["a","b"],"address":{"city":"Oslo","geo":[59.9,10.7]}}"""

    private fun error(text: String, lines: Boolean = false): JsonSyntaxException = try {
        JsonParser.parse(text, lines)
        fail("Expected a syntax error for: $text")
        throw AssertionError()
    } catch (e: JsonSyntaxException) {
        e
    }

    @Test fun buildsTypedNodesInDocumentOrder() {
        val doc = JsonParser.parse(sample)
        val n = doc.nodes
        assertEquals(JsonType.Object, n[0].type)
        assertEquals(6, n[0].childCount)
        assertEquals(listOf("name", "age", "admin", "boss", "tags"), n.drop(1).take(5).map { it.label })
        assertEquals(listOf(JsonType.String, JsonType.Number, JsonType.Bool, JsonType.Null, JsonType.Array), n.drop(1).take(5).map { it.type })
        assertEquals("Ann", n[1].text)
        assertEquals("31", n[2].text)
        assertEquals(2, n[5].childCount)
        assertEquals(n.lastIndex, n[0].end)
        assertFalse(doc.truncated)
    }

    @Test fun pathsUseDotsIndexesAndQuotedKeys() {
        val doc = JsonParser.parse("""{"user":{"tags":["x",{"odd key":1}],"a\"b":2}}""")
        val byValue = doc.nodes.indices.associateBy { doc.path(it) }
        assertTrue(byValue.keys.toString(), "$" in byValue)
        assertTrue(byValue.keys.toString(), "$.user.tags[0]" in byValue)
        assertTrue(byValue.keys.toString(), "$.user.tags[1][\"odd key\"]" in byValue)
        assertTrue(byValue.keys.toString(), "$.user[\"a\\\"b\"]" in byValue)
    }

    @Test fun visibleRowsFollowTheExpandedSet() {
        val doc = JsonParser.parse(sample)
        assertEquals(listOf(0), doc.visible(emptySet()).toList())
        val rootOnly = doc.visible(setOf(0)).map { doc.nodes[it].label }
        assertEquals(listOf(null, "name", "age", "admin", "boss", "tags", "address"), rootOnly)
        val all = doc.visible(doc.allContainers())
        assertEquals(doc.nodes.size, all.size)
        val tagsIndex = doc.nodes.indexOfFirst { it.label == "tags" }
        val withTags = doc.visible(setOf(0, tagsIndex)).map { doc.nodes[it].label }
        assertEquals(listOf(null, "name", "age", "admin", "boss", "tags", "0", "1", "address"), withTags)
    }

    @Test fun emptyContainersAreNotExpandable() {
        val doc = JsonParser.parse("""{"a":[],"b":{}}""")
        assertEquals(3, doc.visible(setOf(0)).size)
        assertEquals(0, doc.nodes[1].childCount)
    }

    @Test fun decodesEscapesAndSurrogatePairs() {
        val doc = JsonParser.parse("\"tab\\t quote\\\" slash\\/ \\u00e9 \\uD83D\\uDE00\"")
        assertEquals("tab\t quote\" slash/ \u00e9 \uD83D\uDE00", doc.nodes[0].text)
    }

    @Test fun numbersKeepTheirOriginalText() {
        val doc = JsonParser.parse("[0, -1, 1.50, 2e10, -3.5E-2]")
        assertEquals(listOf("0", "-1", "1.50", "2e10", "-3.5E-2"), doc.nodes.drop(1).map { it.text })
    }

    @Test fun topLevelScalarAndBom() {
        assertEquals(JsonType.Number, JsonParser.parse("\uFEFF 42 ").nodes.single().type)
    }

    @Test fun longStringsAreClipped() {
        val doc = JsonParser.parse("\"${"x".repeat(100)}\"", maxString = 10)
        assertEquals(10, doc.nodes[0].text.length)
        assertTrue(doc.nodes[0].clipped)
    }

    @Test fun nodeLimitStopsParsingAndKeepsTheStart() {
        val doc = JsonParser.parse("[" + (1..50).joinToString(",") + "]", maxNodes = 10)
        assertTrue(doc.truncated)
        assertEquals(10, doc.nodes.size)
        assertEquals(9, doc.nodes[0].end)
        assertEquals(10, doc.visible(setOf(0)).size)
    }

    @Test fun depthLimitIsAnError() {
        val e = try {
            JsonParser.parse("[".repeat(30) + "]".repeat(30), maxDepth = 10)
            null
        } catch (e: JsonSyntaxException) {
            e
        }
        assertTrue(e != null && "deep" in e.reason)
        JsonParser.parse("[".repeat(11) + "]".repeat(11), maxDepth = 10)
    }

    @Test fun errorsReportLineAndColumn() {
        val e = error("{\n  \"a\": 1,\n  \"b\": oops\n}")
        assertEquals(3, e.line)
        assertEquals(8, e.column)
        assertTrue(e.reason, "oops".first().toString() in e.reason)
    }

    @Test fun commonMistakes() {
        assertEquals(1 to 9, error("{\"a\": 1,}").let { it.line to it.column })
        assertTrue(error("[1, 2").reason.contains("end of the file"))
        assertTrue(error("{'a': 1}").reason.contains("quoted key"))
        assertTrue(error("[1] 2").reason.contains("after the JSON value"))
        assertTrue(error("[01]").reason.isNotEmpty())
        assertTrue(error("\"a\nb\"").reason.contains("escaped"))
        assertTrue(error("\"\\x\"").reason.contains("escape"))
        assertTrue(error("").reason.contains("empty"))
        assertTrue(error("[tru]").reason.isNotEmpty())
        assertTrue(error("{\"a\" 1}").reason.contains("':'"))
    }

    @Test fun jsonLinesWrapEachLineInOneArray() {
        val doc = JsonParser.parse("{\"a\":1}\n\n{\"a\":2}\n[3]\n", lines = true)
        assertTrue(doc.lines)
        assertEquals(3, doc.nodes[0].childCount)
        assertEquals(listOf("0", "1", "2"), doc.nodes.filter { it.parent == 0 }.map { it.label })
        assertEquals("$[1].a", doc.path(doc.nodes.indexOfFirst { it.text == "2" }))
    }

    @Test fun jsonLinesErrorPointsAtTheBadLine() {
        val e = error("{\"a\":1}\n{\"a\":}\n", lines = true)
        assertEquals(2, e.line)
        assertEquals(6, e.column)
    }
    @Test fun jsonLinesRejectsMultipleValuesOnOneLine() {
        val e = error("{} {}", lines = true)
        assertEquals(1, e.line)
        assertEquals(4, e.column)
    }

    @Test fun jsonLinesRejectsMultilineValues() {
        val e = error("[1,\n2]", lines = true)
        assertEquals(1, e.line)
    }

    @Test fun truncatedNestedContainerRetainsItsParentChild() {
        val doc = JsonParser.parse("{\"data\":[1,2,3,4]}", maxNodes = 4)
        assertEquals(1, doc.nodes[0].childCount)
        assertEquals(listOf(0), doc.visible(emptySet()).toList())
        assertEquals(listOf(0, 1), doc.visible(setOf(0)).toList())
    }

}
