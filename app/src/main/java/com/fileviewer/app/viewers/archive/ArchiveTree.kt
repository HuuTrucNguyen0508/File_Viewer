package com.fileviewer.app.viewers.archive

import com.fileviewer.app.source.ArchiveEntry

/** Folder tree built from flat entry paths; folders that have no entry of their own are implied. Pure. */
class ArchiveTree(entries: List<ArchiveEntry>) {
    class Node(val name: String, val path: String, val entry: ArchiveEntry?) {
        val children = LinkedHashMap<String, Node>()
        val isDirectory: Boolean get() = entry?.isDirectory ?: true

        /** Folders first, then files, each by name ignoring case. */
        fun sortedChildren(): List<Node> = children.values.sortedWith(
            compareBy<Node>({ !it.isDirectory }, { it.name.lowercase() }),
        )
    }

    val root = Node("", "", null)

    init {
        for (e in entries) {
            val parts = e.path.split('/').filter { it.isNotEmpty() && it != "." }
            if (parts.isEmpty()) continue
            var node = root
            parts.forEachIndexed { i, part ->
                val last = i == parts.lastIndex
                val path = parts.take(i + 1).joinToString("/")
                val existing = node.children[part]
                node = when {
                    existing == null -> Node(part, path, if (last) e else null).also { node.children[part] = it }
                    last && existing.entry == null && !e.isDirectory -> Node(part, path, e).also { replacement ->
                        replacement.children.putAll(existing.children); node.children[part] = replacement
                    }
                    else -> existing
                }
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
