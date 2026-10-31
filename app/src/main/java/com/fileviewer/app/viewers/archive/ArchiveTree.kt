package com.fileviewer.app.viewers.archive

import com.fileviewer.app.source.ArchiveEntry
import com.fileviewer.app.source.Limits

/** Folder tree built from flat entry paths; folders that have no entry of their own are implied. Pure. */
class ArchiveTree(entries: List<ArchiveEntry>) {
    class Node(val name: String, private val parent: Node?, var entry: ArchiveEntry?) {
        val path: String get() = generateSequence(this) { it.parent }.takeWhile { it.parent != null }
            .map { it.name }.toList().asReversed().joinToString("/")
        val children = LinkedHashMap<String, Node>()
        val isDirectory: Boolean get() = entry?.isDirectory ?: true
        fun sortedChildren(): List<Node> = children.values.sortedWith(
            compareBy<Node>({ !it.isDirectory }, { it.name.lowercase() }),
        )
    }

    val root = Node("", null, null)
    var hiddenEntries = 0
        private set

    init {
        var nodes = 0
        for (e in entries) {
            if (e.path.length > Limits.MAX_ARCHIVE_PATH_CHARS) { hiddenEntries++; continue }
            val parts = e.path.split('/').filter { it.isNotEmpty() && it != "." }
            if (parts.isEmpty()) continue
            if (parts.size > Limits.MAX_ARCHIVE_PATH_DEPTH) { hiddenEntries++; continue }
            var existing: Node? = root
            var missing = 0
            for (part in parts) {
                existing = existing?.children?.get(part)
                if (existing == null) missing++
            }
            if (missing > Limits.MAX_ARCHIVE_TREE_NODES - nodes) { hiddenEntries++; continue }
            var node = root
            parts.forEachIndexed { i, part ->
                val last = i == parts.lastIndex
                node = node.children.getOrPut(part) { nodes++; Node(part, node, null) }
                if (last && (node.entry == null || !e.isDirectory)) node.entry = e
            }
        }
    }

    /** The node at [path] ("" is the root), or null when it doesn't exist. */
    fun find(path: String): Node? {
        if (path.isEmpty()) return root
        var node = root
        for (part in path.split('/')) node = node.children[part] ?: return null
        return node
    }
}
