package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.Limits
import java.nio.charset.Charset

/** EPUB 2 and 3: container.xml, then the OPF package's spine, with titles from the nav document or NCX. */
object EpubParser {

    fun parse(pkg: DocPackage): BookPreview {
        val opfPath = rootFile(pkg) ?: throw DocumentException("This EPUB doesn't say where its contents are (META-INF/container.xml).")
        val opf = readOpf(pkg, opfPath)
        val dir = PackagePaths.dirOf(opfPath)
        val titles = HashMap<String, String>()
        opf.navHref?.let { PackagePaths.resolve(dir, it) }?.takeIf { pkg.has(it) }?.let { navTitles(pkg, it, titles) }
        if (titles.isEmpty()) opf.ncxHref?.let { PackagePaths.resolve(dir, it) }?.takeIf { pkg.has(it) }?.let { ncxTitles(pkg, it, titles) }
        val chapters = opf.spine.mapNotNull { href -> PackagePaths.resolve(dir, href) }
            .filter { pkg.has(it) }
            .distinct()
        val notes = listOfNotNull("Only the first ${Limits.MAX_DOC_SECTIONS} chapters are listed.".takeIf { chapters.size > Limits.MAX_DOC_SECTIONS })
        if (chapters.isEmpty()) throw DocumentException("This EPUB has no readable chapters.")
        return BookPreview(opf.title, chapters.take(Limits.MAX_DOC_SECTIONS).map { Chapter(titles[it], it) }, notes)
    }

    /** One chapter as a safe fragment, images pointing into the package. */
    fun chapter(pkg: DocPackage, chapter: Chapter): ChapterHtml.Result {
        pkg.resetBudget()
        val bytes = pkg.bytes(chapter.path)
        return ChapterHtml.sanitize(decode(bytes), chapter.path)
    }

    private fun rootFile(pkg: DocPackage): String? {
        var path: String? = null
        pkg.readIfPresent("META-INF/container.xml") { input ->
            XmlScan.parse(XmlScan.withoutDoctype(input.readBytes()), object : XmlHandler() {
                override fun start(name: String, attrs: Attrs) {
                    if (name == "rootfile" && path == null) path = attrs["full-path"]?.let { PackagePaths.resolve("", it) }
                }
            })
        }
        return path
    }

    private class Opf(val title: String?, val spine: List<String>, val navHref: String?, val ncxHref: String?)

    private fun readOpf(pkg: DocPackage, path: String): Opf {
        val items = HashMap<String, Pair<String, String>>()
        val spine = ArrayList<String>()
        var navHref: String? = null
        var tocId: String? = null
        var title: String? = null
        XmlScan.parse(XmlScan.withoutDoctype(pkg.bytes(path)), object : XmlHandler() {
            val sb = StringBuilder()
            var inTitle = false
            override fun start(name: String, attrs: Attrs) {
                when (name) {
                    "title" -> if (title == null) { inTitle = true; sb.setLength(0) }
                    "item" -> {
                        val id = attrs["id"] ?: return
                        val href = attrs["href"] ?: return
                        items[id] = href to (attrs["media-type"] ?: "")
                        if (attrs["properties"]?.split(' ')?.contains("nav") == true) navHref = href
                    }
                    "spine" -> tocId = attrs["toc"]
                    "itemref" -> attrs["idref"]?.let { id ->
                        items[id]?.takeIf { (href, type) -> isChapter(href, type) }?.let { spine += it.first }
                    }
                }
            }
            override fun end(name: String) {
                if (name == "title" && inTitle) { inTitle = false; title = sb.toString().trim().ifEmpty { null } }
            }
            override fun text(s: String) { if (inTitle) sb.append(s) }
        })
        val ncx = tocId?.let { items[it]?.first } ?: items.values.firstOrNull { it.second == "application/x-dtbncx+xml" }?.first
        return Opf(title, spine, navHref, ncx)
    }

    private fun isChapter(href: String, type: String) =
        type == "application/xhtml+xml" || type == "text/html" || href.substringAfterLast('.').lowercase() in setOf("xhtml", "html", "htm")

    private fun ncxTitles(pkg: DocPackage, path: String, into: MutableMap<String, String>) {
        val dir = PackagePaths.dirOf(path)
        XmlScan.parse(XmlScan.withoutDoctype(pkg.bytes(path)), object : XmlHandler() {
            val label = StringBuilder()
            var inText = false
            override fun start(name: String, attrs: Attrs) {
                when (name) {
                    "navLabel" -> label.setLength(0)
                    "text" -> inText = true
                    "content" -> attrs["src"]?.let { PackagePaths.resolve(dir, it) }?.let { target ->
                        val t = label.toString().trim()
                        if (t.isNotEmpty()) into.putIfAbsent(target, t)
                    }
                }
            }
            override fun end(name: String) { if (name == "text") inText = false }
            override fun text(s: String) { if (inText) label.append(s) }
        })
    }

    private fun navTitles(pkg: DocPackage, path: String, into: MutableMap<String, String>) {
        val dir = PackagePaths.dirOf(path)
        HtmlTokens.scan(decode(pkg.bytes(path)), object : HtmlTokenSink() {
            var navDepth = 0
            var tocSeen = false
            var href: String? = null
            val label = StringBuilder()
            override fun start(name: String, attrs: Map<String, String>, selfClosing: Boolean) {
                if (name == "nav" && !selfClosing) {
                    if (navDepth > 0 || (!tocSeen && attrs["type"]?.split(' ')?.contains("toc") != false)) { navDepth++; tocSeen = true }
                }
                if (navDepth > 0 && name == "a") { href = attrs["href"]; label.setLength(0) }
            }
            override fun end(name: String) {
                if (name == "nav" && navDepth > 0) navDepth--
                if (name == "a" && href != null) {
                    val target = PackagePaths.resolve(dir, href!!)
                    val t = HtmlTokens.decodeEntities(label.toString()).replace(Regex("\\s+"), " ").trim()
                    if (target != null && t.isNotEmpty()) into.putIfAbsent(target, t)
                    href = null
                }
            }
            override fun text(raw: String) { if (href != null) label.append(raw) }
        })
    }

    /** BOM, then the XML declaration's encoding, then UTF-8. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val head = String(bytes, 0, minOf(bytes.size, 256), Charsets.ISO_8859_1)
        val declared = Regex("encoding=[\"']([A-Za-z0-9._-]+)[\"']").find(head)?.groupValues?.get(1)
        val cs = declared?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, cs)
    }
}
