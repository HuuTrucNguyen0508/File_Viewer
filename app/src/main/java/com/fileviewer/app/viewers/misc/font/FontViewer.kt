package com.fileviewer.app.viewers.misc.font

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.detect.FontFormat
import com.fileviewer.app.source.FileSeekable
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewers.misc.WithRealFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A font the platform accepted, with whatever the file's tables told us. */
class LoadedFont(val typeface: Typeface, val info: FontInfo?)

/** Specimen sheet for TTF, OTF and the first face of a TTC. WOFF is tried; Android may refuse it. */
@Composable
fun FontViewer(source: FileSource, kind: FileKind.Font, onSwitch: (ViewerId) -> Unit) {
    WithRealFile(source) { file ->
        val loaded by produceState<Result<LoadedFont>?>(null, file) { value = withContext(Dispatchers.IO) { runCatching { load(file) } } }
        val result = loaded ?: return@WithRealFile Loading()
        result.onFailure {
            Message(
                "This font can't be previewed",
                detail = if (kind.format == FontFormat.WOFF) {
                    "Android can't load WOFF fonts. File info and raw bytes are available."
                } else {
                    "The system rejected it: ${it.message ?: it.javaClass.simpleName}"
                },
                actions = listOf("Show info" to { onSwitch(ViewerId.Info) }, "Show hex" to { onSwitch(ViewerId.Hex) }),
            )
        }
        result.onSuccess { Specimen(source, kind, it) }
    }
}

private fun load(file: File): LoadedFont {
    val info = FileSeekable(file).use { FontInfoReader.read(ByteRange.of(it)) }
    val font = Font.Builder(file).setTtcIndex(0).build()
    val typeface = Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build()).setSystemFallback("sans-serif").build()
    return LoadedFont(typeface, info)
}
