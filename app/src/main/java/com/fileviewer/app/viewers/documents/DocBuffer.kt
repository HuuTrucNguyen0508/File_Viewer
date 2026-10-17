package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.Limits

/** Bounds intermediate text before it reaches the final HTML writer. */
class DocBuffer(private val cap: Int = Limits.MAX_DOC_HTML_CHARS) : CharSequence, Appendable {
    private val text = StringBuilder()
    override val length get() = text.length
    override fun get(index: Int) = text[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)
    override fun toString() = text.toString()

    override fun append(value: CharSequence?): DocBuffer = append(value, 0, value?.length ?: 4)

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): DocBuffer {
        if (endIndex - startIndex > cap - length) throw DocumentException("A text block is too large to preview. Open Text to read it.")
        text.append(value, startIndex, endIndex)
        return this
    }

    override fun append(value: Char): DocBuffer {
        if (length >= cap) throw DocumentException("A text block is too large to preview. Open Text to read it.")
        text.append(value)
        return this
    }

    fun setLength(length: Int) { text.setLength(length) }
}
