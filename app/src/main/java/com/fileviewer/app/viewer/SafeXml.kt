package com.fileviewer.app.viewer

import android.util.Xml
import org.xml.sax.InputSource
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The only way viewers parse XML: DTDs and external entities are off, so a hostile
 * file can't expand entities or reach the filesystem. Frozen contract for phase 2.
 */
object SafeXml {

    fun pullParser(input: InputStream, encoding: String? = null): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(input, encoding)
    }

    fun documentBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            isXIncludeAware = false
            isValidating = false
        }
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        ).forEach { (feature, value) -> runCatching { factory.setFeature(feature, value) } }
        return factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
    }
}
