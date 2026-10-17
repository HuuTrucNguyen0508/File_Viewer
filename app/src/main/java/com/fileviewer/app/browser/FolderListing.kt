package com.fileviewer.app.browser

import java.io.File

enum class SortBy(val label: String) { Name("Name"), Size("Size"), Date("Date modified") }

/** Directory listing for the browser: folders first, then files, in the chosen order. Pure. */
object FolderListing {
    fun list(dir: File, sort: SortBy, descending: Boolean, showHidden: Boolean): List<File> {
        val entries = dir.listFiles()?.filter { showHidden || !it.name.startsWith(".") }.orEmpty()
        val byName = compareBy<File, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        val order: Comparator<File> = when (sort) {
            SortBy.Name -> byName
            SortBy.Size -> compareBy<File> { if (it.isDirectory) 0L else it.length() }.then(byName)
            SortBy.Date -> compareBy<File> { it.lastModified() }.then(byName)
        }
        val directed = if (descending) order.reversed() else order
        val (dirs, files) = entries.partition { it.isDirectory }
        return dirs.sortedWith(if (sort == SortBy.Size) byName else directed) + files.sortedWith(directed)
    }
}
