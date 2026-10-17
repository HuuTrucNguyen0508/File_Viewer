package com.fileviewer.app.detect

/** Tells Markdown from plain prose for files with no extension, such as `README`. */
object MarkdownSniff {
    private val heading = Regex("^#{1,6}[ \\t]+\\S")
    private val link = Regex("\\[[^\\]\\n]+]\\([^)\\n]+\\)")
    private val names = setOf("readme", "changelog", "contributing", "history", "release_notes", "releasenotes")

    fun isCandidateName(fileName: String) = fileName.lowercase() in names

    /** True when the first line is a heading, or the sample holds a fenced code block or an inline link. */
    fun looksLikeMarkdown(sample: String): Boolean {
        val firstLine = sample.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        return heading.containsMatchIn(firstLine) || "\n```" in "\n$sample" || link.containsMatchIn(sample)
    }
}
