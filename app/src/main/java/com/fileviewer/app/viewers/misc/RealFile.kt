package com.fileviewer.app.viewers.misc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.SeekableSource
import com.fileviewer.app.source.materialize
import com.fileviewer.app.ui.components.CopyProgress
import com.fileviewer.app.ui.components.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.io.File

sealed interface RealFileState {
    data class Copying(val copied: Long, val total: Long?) : RealFileState
    data class Ready(val file: File) : RealFileState
    data class Failed(val message: String) : RealFileState
}

/** Hides the source's seekable view so [materialize] always produces a real cached file. */
private class FileCopyOf(private val inner: FileSource) : FileSource by inner {
    override val key = "file:${inner.key}"
    override fun seekableOrNull(): SeekableSource? = null
}

/**
 * A path on disk for APIs that only take paths (PackageManager, Typeface). Local files are used in place;
 * content URIs and archive entries are copied into the cache first, with progress.
 */
@Composable
fun rememberRealFile(source: FileSource): RealFileState {
    val state by produceState<RealFileState>(RealFileState.Copying(0, source.size), source) {
        var copy: SeekableSource? = null
        try {
            val direct = withContext(Dispatchers.IO) { source.seekableOrNull()?.use { it.file } }
            if (direct != null) {
                value = RealFileState.Ready(direct)
            } else {
                var lastReported = 0L
                copy = FileCopyOf(source).materialize { copied ->
                    if (copied - lastReported >= 1 shl 20) {
                        lastReported = copied
                        value = RealFileState.Copying(copied, source.size)
                    }
                }
                value = RealFileState.Ready(checkNotNull(copy.file))
            }
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value = RealFileState.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            copy?.close()
        }
    }
    return state
}

/** Shows progress or the failure, then [content] once a real file exists. */
@Composable
fun WithRealFile(source: FileSource, content: @Composable (File) -> Unit) {
    when (val s = rememberRealFile(source)) {
        is RealFileState.Copying -> CopyProgress(s.copied, s.total)
        is RealFileState.Failed -> Message("Couldn't read this file", detail = s.message)
        is RealFileState.Ready -> content(remember(s.file) { s.file })
    }
}
