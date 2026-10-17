package com.fileviewer.app.viewers.info

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.security.MessageDigest

object Hashes {
    val ALGORITHMS = listOf("MD5", "SHA-1", "SHA-256")

    /** One pass over [input] for all [ALGORITHMS]; lowercase hex digests by algorithm name. Cancellable. */
    suspend fun compute(input: InputStream, progress: (Long) -> Unit = {}): Map<String, String> {
        val digests = ALGORITHMS.associateWith { MessageDigest.getInstance(it) }
        val buf = ByteArray(256 * 1024)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            digests.values.forEach { it.update(buf, 0, n) }
            total += n
            progress(total)
        }
        return digests.mapValues { (_, d) -> d.digest().joinToString("") { "%02x".format(it) } }
    }
}
