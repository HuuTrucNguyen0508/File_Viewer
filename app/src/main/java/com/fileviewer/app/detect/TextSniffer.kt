package com.fileviewer.app.detect

import java.nio.charset.Charset

/** Result of sniffing a byte sample. [bomLength] bytes at the start are a byte-order mark. */
data class TextSniff(val isText: Boolean, val charset: Charset, val bomLength: Int = 0)

/**
 * Decides whether a sample is text and in which encoding.
 * Order: BOM, BOM-less UTF-16 (alternating NULs), UTF-8 validity (tolerating a sequence cut
 * off by the sample end when [truncated]), then single-byte text with few control characters.
 */
object TextSniffer {
    val UTF_32LE: Charset = Charset.forName("UTF-32LE")
    val UTF_32BE: Charset = Charset.forName("UTF-32BE")
    val LATIN_1: Charset = Charsets.ISO_8859_1

    private const val MAX_CONTROL_RATIO = 0.02

    fun sniff(sample: ByteArray, length: Int = sample.size, truncated: Boolean = false): TextSniff {
        if (length == 0) return TextSniff(true, Charsets.UTF_8)
        bom(sample, length)?.let { (cs, n) -> return TextSniff(decodedLooksLikeText(sample, n, length, cs), cs, n) }
        utf16WithoutBom(sample, length)?.let { cs -> return TextSniff(decodedLooksLikeText(sample, 0, length, cs), cs) }

        var nul = 0
        for (i in 0 until length) if (sample[i] == 0.toByte()) nul++
        if (nul > 0) return TextSniff(false, LATIN_1)

        return when (Utf8Validator.check(sample, length)) {
            Utf8Validator.Result.VALID -> TextSniff(controlRatioOk(sample, length), Charsets.UTF_8)
            Utf8Validator.Result.INCOMPLETE_AT_END ->
                if (truncated) TextSniff(controlRatioOk(sample, length), Charsets.UTF_8)
                else TextSniff(controlRatioOk(sample, length), LATIN_1)
            Utf8Validator.Result.INVALID -> TextSniff(controlRatioOk(sample, length), LATIN_1)
        }
    }

    /** The charset and length of a leading byte-order mark. UTF-32 is checked before UTF-16. */
    fun bom(b: ByteArray, len: Int): Pair<Charset, Int>? {
        fun at(i: Int) = if (i < len) b[i].toInt() and 0xFF else -1
        return when {
            at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF -> Charsets.UTF_8 to 3
            at(0) == 0xFF && at(1) == 0xFE && at(2) == 0 && at(3) == 0 -> UTF_32LE to 4
            at(0) == 0 && at(1) == 0 && at(2) == 0xFE && at(3) == 0xFF -> UTF_32BE to 4
            at(0) == 0xFF && at(1) == 0xFE -> Charsets.UTF_16LE to 2
            at(0) == 0xFE && at(1) == 0xFF -> Charsets.UTF_16BE to 2
            else -> null
        }
    }

    /** Mostly-ASCII UTF-16 has NULs in every other byte: odd positions for LE, even for BE. */
    private fun utf16WithoutBom(b: ByteArray, len: Int): Charset? {
        val n = len - (len % 2)
        if (n < 4) return null
        var evenNul = 0
        var oddNul = 0
        for (i in 0 until n) if (b[i] == 0.toByte()) { if (i % 2 == 0) evenNul++ else oddNul++ }
        val half = n / 2.0
        return when {
            oddNul / half > 0.3 && evenNul / half < 0.05 -> Charsets.UTF_16LE
            evenNul / half > 0.3 && oddNul / half < 0.05 -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun decodedLooksLikeText(b: ByteArray, start: Int, len: Int, cs: Charset): Boolean {
        val unit = if (cs == UTF_32LE || cs == UTF_32BE) 4 else if (cs == Charsets.UTF_8) 1 else 2
        val usable = start + (len - start) / unit * unit
        val text = String(b, start, usable - start, cs)
        if (text.isEmpty()) return true
        var bad = 0
        for (c in text) if (c == '\u0000' || c == '�' || (c < ' ' && c !in ALLOWED_CONTROLS)) bad++
        return bad.toDouble() / text.length <= MAX_CONTROL_RATIO
    }

    private fun controlRatioOk(b: ByteArray, len: Int): Boolean {
        var bad = 0
        for (i in 0 until len) {
            val c = b[i].toInt() and 0xFF
            if ((c < 0x20 && c.toChar() !in ALLOWED_CONTROLS) || c == 0x7F) bad++
        }
        return bad.toDouble() / len <= MAX_CONTROL_RATIO
    }

    /** Tab, LF, CR, form feed, backspace, and ESC (ANSI colors in logs). */
    private val ALLOWED_CONTROLS = setOf('\t', '\n', '\r', '\u000C', '\b', '\u001B')
}

internal object Utf8Validator {
    enum class Result { VALID, INVALID, INCOMPLETE_AT_END }

    fun check(b: ByteArray, len: Int): Result {
        var i = 0
        while (i < len) {
            val c = b[i].toInt() and 0xFF
            val need = when {
                c < 0x80 -> 0
                c in 0xC2..0xDF -> 1
                c in 0xE0..0xEF -> 2
                c in 0xF0..0xF4 -> 3
                else -> return Result.INVALID
            }
            if (i + need >= len && need > 0) {
                for (k in i + 1 until len) if (!isCont(b[k])) return Result.INVALID
                return Result.INCOMPLETE_AT_END
            }
            for (k in 1..need) if (!isCont(b[i + k])) return Result.INVALID
            if (need == 2) {
                val c1 = b[i + 1].toInt() and 0xFF
                if ((c == 0xE0 && c1 < 0xA0) || (c == 0xED && c1 > 0x9F)) return Result.INVALID
            } else if (need == 3) {
                val c1 = b[i + 1].toInt() and 0xFF
                if ((c == 0xF0 && c1 < 0x90) || (c == 0xF4 && c1 > 0x8F)) return Result.INVALID
            }
            i += need + 1
        }
        return Result.VALID
    }

    private fun isCont(b: Byte) = (b.toInt() and 0xC0) == 0x80
}
