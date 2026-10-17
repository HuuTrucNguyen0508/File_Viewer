package com.fileviewer.app.viewers.data.sqlite

import android.os.CancellationSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Runs a blocking query on the IO pool and cancels it inside SQLite when the caller is cancelled. */
suspend fun <T> cancellableQuery(block: (CancellationSignal) -> T): T = coroutineScope {
    val signal = CancellationSignal()
    val job = async(Dispatchers.IO) { block(signal) }
    try {
        job.await()
    } catch (e: CancellationException) {
        signal.cancel()
        job.cancel()
        throw e
    }
}
