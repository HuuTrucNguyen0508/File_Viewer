package com.fileviewer.app.viewers.misc.cert

/** An X.500 name split into attribute/value pairs, in the order the certificate lists them. */
class DistinguishedName(val parts: List<Pair<String, String>>) {
    val commonName: String? get() = parts.firstOrNull { it.first == "CN" }?.second

    /** The name for a list heading: the common name, else the organization, else the whole name. */
    val summary: String get() = commonName ?: parts.firstOrNull { it.first == "O" }?.second ?: parts.joinToString(", ") { it.second }

    override fun toString(): String = parts.joinToString(", ") { "${it.first}=${it.second}" }

    companion object {
        /** Reads the RFC 2253 form `X500Principal.getName` returns, including escaped commas. */
        fun parse(rfc2253: String): DistinguishedName {
            val parts = ArrayList<Pair<String, String>>()
            val current = StringBuilder()
            var escaped = false
            fun flush() {
                val text = current.toString()
                current.clear()
                val eq = text.indexOf('=')
                if (eq > 0) parts.add(text.substring(0, eq).trim().uppercase() to text.substring(eq + 1).trim())
            }
            for (c in rfc2253) {
                when {
                    escaped -> { current.append(c); escaped = false }
                    c == '\\' -> escaped = true
                    c == ',' || c == '+' -> flush()
                    else -> current.append(c)
                }
            }
            flush()
            return DistinguishedName(parts)
        }

        private val LABELS = mapOf(
            "CN" to "Common name", "O" to "Organization", "OU" to "Unit", "C" to "Country", "ST" to "State",
            "L" to "Locality", "STREET" to "Street", "DC" to "Domain component", "UID" to "User ID",
            "SERIALNUMBER" to "Serial number", "EMAIL" to "Email",
        )

        fun label(attribute: String): String = LABELS[attribute] ?: attribute
    }
}
