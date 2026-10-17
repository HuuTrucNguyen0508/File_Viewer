package com.fileviewer.app.viewers.documents

/** An OOXML part's relationships: id to a package path, or to an external URL. */
class OoxmlRels private constructor(
    private val internal: Map<String, String>,
    private val external: Map<String, String>,
    private val types: List<Pair<String, String>>,
) {
    /** Package path of an internal target; null for missing or traversing targets. */
    fun path(id: String?): String? = id?.let { internal[it] }

    fun url(id: String?): String? = id?.let { external[it] }

    /** The first internal target whose relationship type ends with `/[type]` (e.g. "styles", "officeDocument"). */
    fun byType(type: String): String? = types.firstOrNull { it.first.endsWith("/$type") }?.second

    companion object {
        /** Relationships of [partPath], e.g. `word/_rels/document.xml.rels` for `word/document.xml`; "" for the package. */
        fun of(pkg: DocPackage, partPath: String): OoxmlRels {
            val dir = PackagePaths.dirOf(partPath)
            val relsPath = (if (dir.isEmpty()) "" else "$dir/") + "_rels/" + partPath.substringAfterLast('/') + ".rels"
            val internal = HashMap<String, String>()
            val external = HashMap<String, String>()
            val types = ArrayList<Pair<String, String>>()
            pkg.readIfPresent(relsPath) { input ->
                XmlScan.parse(input, object : XmlHandler() {
                    override fun start(name: String, attrs: Attrs) {
                        if (name != "Relationship") return
                        val id = attrs["Id"] ?: return
                        val target = attrs["Target"] ?: return
                        if (attrs["TargetMode"] == "External") external[id] = target
                        else PackagePaths.resolve(dir, target)?.let {
                            internal[id] = it
                            types += (attrs["Type"] ?: "") to it
                        }
                    }
                })
            }
            return OoxmlRels(internal, external, types)
        }
    }
}

/** OOXML on/off properties: `<w:b/>` is on, `w:val="0|false|off|none"` is off. */
fun Attrs.toggle(): Boolean = when (this["val"]?.lowercase()) {
    "0", "false", "off", "none" -> false
    else -> true
}
