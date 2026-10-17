package com.fileviewer.app.source

import java.io.File
import java.security.MessageDigest

/**
 * One directory per opened source under [root], named by a hash of the source key.
 * File names inside are generated, never taken from entry names.
 */
class MaterializeCache(private val root: File, private val maxBytes: Long = Limits.MAX_CACHE_BYTES) {

    fun dirFor(key: String): File = File(root, hash(key)).apply { mkdirs() }

    fun contentFile(key: String): File = File(dirFor(key), CONTENT)

    /** The completed copy for [key], if one exists; marks it recently used. */
    fun existing(key: String): File? {
        val f = File(File(root, hash(key)), CONTENT)
        if (!f.isFile) return null
        f.parentFile?.setLastModified(System.currentTimeMillis())
        return f
    }

    /** Deletes least-recently-used directories until the cache fits, never touching [keep]. */
    @Synchronized
    fun trim(keep: String? = null) {
        val keepDir = keep?.let { hash(it) }
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return
        var total = dirs.sumOf { dirSize(it) }
        for (dir in dirs.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            if (dir.name == keepDir || dir.listFiles().orEmpty().any { it.name.endsWith(".part") }) continue
            total -= dirSize(dir)
            dir.deleteRecursively()
        }
    }

    fun clear() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun dirSize(dir: File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(16)
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val CONTENT = "content.bin"
        const val PARTIAL = "content.part"

        /** Set once by the Application; viewers use this instance. */
        lateinit var default: MaterializeCache
    }
}
