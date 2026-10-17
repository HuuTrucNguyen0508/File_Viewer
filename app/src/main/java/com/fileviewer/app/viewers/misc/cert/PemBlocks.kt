package com.fileviewer.app.viewers.misc.cert

import java.util.Base64
import java.io.InterruptedIOException

/** One `-----BEGIN label-----` block. Private-key blocks never carry their body. */
sealed interface PemBlock {
    val label: String

    class Data(override val label: String, val der: ByteArray) : PemBlock

    /** [encrypted] is true for PKCS#8 encrypted keys and legacy `Proc-Type: 4,ENCRYPTED` blocks. */
    class Secret(override val label: String, val encrypted: Boolean) : PemBlock

    class Invalid(override val label: String, val reason: String) : PemBlock
}

object PemBlocks {
    private val MARKER = Regex("-----(BEGIN|END) ([A-Z0-9 ]+)-----")

    fun isPem(text: String) = "-----BEGIN " in text

    fun isSecret(label: String) = "PRIVATE KEY" in label

    fun parse(text: String, limit: Int): List<PemBlock> {
        val blocks = ArrayList<PemBlock>()
        var label: String? = null
        var bodyStart = 0
        for (marker in MARKER.findAll(text)) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Parse cancelled")
            val name = marker.groupValues[2]
            if (marker.groupValues[1] == "BEGIN") {
                label = name
                bodyStart = marker.range.last + 1
            } else if (label == name) {
                if (isSecret(name)) {
                    val header = text.substring(bodyStart, minOf(marker.range.first, bodyStart + 200))
                    blocks += PemBlock.Secret(name, name.startsWith("ENCRYPTED") || "ENCRYPTED" in header)
                } else {
                    blocks += decode(name, text.substring(bodyStart, marker.range.first))
                }
                label = null
                if (blocks.size >= limit) break
            }
        }
        return blocks
    }

    private fun decode(label: String, body: String): PemBlock {
        val base64 = body.lineSequence().filter { ':' !in it }.joinToString("").filterNot { it.isWhitespace() }
        return try {
            PemBlock.Data(label, Base64.getDecoder().decode(base64))
        } catch (_: IllegalArgumentException) {
            PemBlock.Invalid(label, "The block isn't valid Base64.")
        }
    }
}
