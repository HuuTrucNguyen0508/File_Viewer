package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.LimitExceededException
import com.fileviewer.app.source.Limits
import com.fileviewer.app.viewer.SafeXml
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InterruptedIOException

/** Element events by local name; namespaces are ignored so strict and transitional OOXML read alike. */
abstract class XmlHandler {
    open fun start(name: String, attrs: Attrs) {}
    open fun end(name: String) {}
    open fun text(s: String) {}
}

/** Attribute lookup by local name, optionally restricted to namespaces whose URI contains [nsHint]. */
class Attrs internal constructor(private val a: Attributes) {
    operator fun get(local: String): String? {
        for (i in 0 until a.length) if (a.getLocalName(i).ifEmpty { a.getQName(i) } == local && a.getURI(i).isEmpty()) return a.getValue(i)
        for (i in 0 until a.length) if (a.getLocalName(i).ifEmpty { a.getQName(i).substringAfter(':') } == local) return a.getValue(i)
        return null
    }

    fun ns(nsHint: String, local: String): String? {
        for (i in 0 until a.length) if (a.getLocalName(i) == local && a.getURI(i).contains(nsHint)) return a.getValue(i)
        return null
    }

    /** OOXML relationship ids (`r:id`, `r:embed`), in either the transitional or the strict namespace. */
    fun rel(local: String): String? = ns("relationships", local)
}

/** Thrown by a handler to stop parsing early once it has what it needs. */
class StopParsing : RuntimeException(null, null, false, false)

object XmlScan {

    /** Streams [input] into [handler]. Rejects DOCTYPEs, bounds nesting, honors thread interruption. */
    fun parse(input: InputStream, handler: XmlHandler) {
        val reader = SafeXml.xmlReader()
        reader.contentHandler = object : DefaultHandler() {
            var depth = 0
            override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Parse cancelled")
                if (++depth > Limits.MAX_DOC_NESTING) throw LimitExceededException("XML nesting", Limits.MAX_DOC_NESTING.toLong())
                handler.start(localName.ifEmpty { qName.substringAfter(':') }, Attrs(attributes))
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                depth--
                handler.end(localName.ifEmpty { qName.substringAfter(':') })
            }

            override fun characters(ch: CharArray, start: Int, length: Int) = handler.text(String(ch, start, length))
        }
        try {
            reader.parse(InputSource(input))
        } catch (_: StopParsing) {
        } catch (e: SAXException) {
            (e.exception as? java.io.IOException)?.let { throw it }
            (e.exception as? StopParsing)?.let { return }
            throw DocumentException("The file contains XML the app won't read: ${e.message}")
        }
    }

    /**
     * Removes a plain `<!DOCTYPE ...>` (EPUB 2 OPF/NCX files carry one) so [parse] accepts the file.
     * A DOCTYPE with an internal subset is left in place, so [parse] still rejects it.
     */
    fun withoutDoctype(bytes: ByteArray): InputStream {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val start = head.indexOf("<!DOCTYPE")
        if (start < 0) return ByteArrayInputStream(bytes)
        val end = head.indexOf('>', start)
        if (end < 0 || head.indexOf('[', start).let { it in start until end }) return ByteArrayInputStream(bytes)
        val out = ByteArray(bytes.size - (end + 1 - start))
        System.arraycopy(bytes, 0, out, 0, start)
        System.arraycopy(bytes, end + 1, out, start, bytes.size - end - 1)
        return ByteArrayInputStream(out)
    }
}
