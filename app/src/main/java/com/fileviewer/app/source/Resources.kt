package com.fileviewer.app.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

/** Closes resources lost to prompt cancellation while returning from the IO dispatcher. */
suspend fun <T : Closeable> acquireResource(open: suspend () -> T): T {
    var acquired: T? = null
    try {
        return withContext(Dispatchers.IO) { open().also { acquired = it } }
    } catch (t: Throwable) {
        acquired?.let { runCatching { it.close() } }
        throw t
    }
}
