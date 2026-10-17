package com.fileviewer.app.viewers.code

import com.fileviewer.app.detect.TextSniffer
import com.fileviewer.app.source.Limits
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset

/** Reads and decodes text for the editor. Pure JVM. */
object TextLoader {

    /** Reads at most [max] bytes; returns null when the stream holds more (counted by bytes read, not reported size). */
    fun readCapped(input: InputStream, max: Long = Limits.MAX_EDITOR_TEXT_BYTES): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return out.toByteArray()
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
    }

    /** The charset the sniffer picks for [bytes]. */
    fun detectCharset(bytes: ByteArray): Charset =
        TextSniffer.sniff(bytes, minOf(bytes.size, 64 * 1024), truncated = bytes.size > 64 * 1024).charset

    /** Decodes [bytes], dropping a byte-order mark that matches [charset]. Malformed input becomes U+FFFD. */
    fun decode(bytes: ByteArray, charset: Charset): String {
        val bom = TextSniffer.bom(bytes, minOf(bytes.size, 4))?.takeIf { it.first == charset }?.second ?: 0
        return String(bytes, bom, bytes.size - bom, charset)
    }

    data class Display(val text: String, val splitLines: Boolean)

    /** Inserts line breaks into lines longer than [maxLine] so the editor stays responsive. */
    fun softBreak(text: String, maxLine: Int = Limits.MAX_LINE_DISPLAY_CHARS): Display {
        var run = 0
        var needed = false
        for (c in text) {
            if (c == '\n') run = 0 else if (++run > maxLine) { needed = true; break }
        }
        if (!needed) return Display(text, false)
        val sb = StringBuilder(text.length + text.length / maxLine + 16)
        run = 0
        for (c in text) {
            if (c == '\n') run = 0 else if (run == maxLine) { sb.append('\n'); run = 0 }
            if (c != '\n') run++
            sb.append(c)
        }
        return Display(sb.toString(), true)
    }

    /** Encodings offered in the picker, in this order, when the runtime supports them. */
    val pickable: List<Charset> by lazy {
        listOf(
            "UTF-8", "UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE", "ISO-8859-1", "windows-1252", "windows-1251",
            "KOI8-R", "ISO-8859-2", "ISO-8859-15", "Shift_JIS", "EUC-JP", "GB18030", "GBK", "Big5", "EUC-KR",
        ).filter { Charset.isSupported(it) }.map { Charset.forName(it) }
    }
}
