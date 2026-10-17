package com.fileviewer.app.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.SeekableSource
import com.fileviewer.app.source.materialize
import com.fileviewer.app.ui.components.CopyProgress
import com.fileviewer.app.ui.components.Message
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.awaitCancellation

sealed interface SeekState {
    data class Copying(val copied: Long, val total: Long?) : SeekState
    data class Ready(val seekable: SeekableSource) : SeekState
    data class Failed(val message: String) : SeekState
}

/** Seekable access for viewers that need it, materializing stream-only sources with progress. */
@Composable
fun rememberSeekable(source: FileSource, requireDescriptor: Boolean = false): SeekState {
    val state by produceState<SeekState>(SeekState.Copying(0, source.size), source) {
        var opened: SeekableSource? = null
        try {
            var lastReported = 0L
            opened = source.materialize(requireDescriptor = requireDescriptor) { copied ->
                if (copied - lastReported >= 1 shl 20) {
                    lastReported = copied
                    value = SeekState.Copying(copied, source.size)
                }
            }
            value = SeekState.Ready(opened)
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value = SeekState.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            opened?.close()
        }
    }
    return state
}

/** Shows progress or the failure, and [content] once the source is seekable. */
@Composable
fun WithSeekable(source: FileSource, requireDescriptor: Boolean = false, content: @Composable (SeekableSource) -> Unit) {
    when (val s = rememberSeekable(source, requireDescriptor)) {
        is SeekState.Copying -> CopyProgress(s.copied, s.total)
        is SeekState.Failed -> Message("Couldn't read this file", detail = s.message)
        is SeekState.Ready -> content(s.seekable)
    }
}
