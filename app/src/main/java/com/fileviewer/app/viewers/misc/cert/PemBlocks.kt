package com.fileviewer.app.viewers.misc.cert

import java.util.Base64

/** One `-----BEGIN label-----` block. Private-key blocks never carry their body. */
sealed interface PemBlock {
    val label: String

    class Data(override val label: String, val der: ByteArray) : PemBlock

    /** [encrypted] is true for PKCS#8 encrypted keys and legacy `Proc-Type: 4,ENCRYPTED` blocks. */
    class Secret(override val label: String, val encrypted: Boolean) : PemBlock

    class Invalid(override val label: String, val reason: String) : PemBlock
}

object PemBlocks {
    private val BLOCK = Regex("-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END \\1-----", RegexOption.DOT_MATCHES_ALL)

    fun isPem(text: String) = "-----BEGIN " in text

    fun isSecret(label: String) = "PRIVATE KEY" in label

    /** Blocks in file order, at most [limit]. Text between blocks is ignored. */
    fun parse(text: String, limit: Int): List<PemBlock> =
        BLOCK.findAll(text).take(limit).map { m ->
            val label = m.groupValues[1]
            val body = m.groupValues[2]
            when {
                isSecret(label) -> PemBlock.Secret(label, encrypted = label.startsWith("ENCRYPTED") || "ENCRYPTED" in body.take(200))
                else -> decode(label, body)
            }
        }.toList()

    private fun decode(label: String, body: String): PemBlock {
        val base64 = body.lineSequence().filter { ':' !in it }.joinToString("").filterNot { it.isWhitespace() }
        return try {
            PemBlock.Data(label, Base64.getDecoder().decode(base64))
        } catch (_: IllegalArgumentException) {
            PemBlock.Invalid(label, "The block isn't valid Base64.")
        }
    }
}
