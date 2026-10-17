package com.fileviewer.app.viewers.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.Family
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.viewer.ViewerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * A short statement above a data view: what this view leaves out, what was cut. A bar in the Data hue
 * ties it to the file's tile and chip, like the preview banner on documents. Notes in muted ink follow.
 */
@Composable
fun DataNote(text: String, notes: List<String> = emptyList(), tone: Color = Fv.palette.family(Family.Data)) {
    val p = Fv.palette
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(tone))
        Column(Modifier.padding(start = 10.dp)) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = p.ink)
            notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
    HorizontalDivider(color = p.rule)
}

/** A slim control strip with a rule under it: toggles, counts, a delimiter picker. */
@Composable
fun DataBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
    HorizontalDivider(color = Fv.palette.rule)
}

sealed interface TextState {
    data object Loading : TextState
    data class Ready(val value: LoadedText) : TextState
    data class Failed(val message: String) : TextState
}

/** Loads the file's text off the main thread; the result is cut at [DataLimits.MAX_TEXT_BYTES]. */
@Composable
fun rememberLoadedText(source: FileSource): TextState {
    val state by produceState<TextState>(TextState.Loading, source) {
        value = try {
            TextState.Ready(runInterruptible(Dispatchers.IO) { DataText.load(source) })
        } catch (e: java.io.IOException) {
            TextState.Failed(e.message ?: e.javaClass.simpleName)
        } catch (e: SecurityException) {
            TextState.Failed(e.message ?: "Permission denied")
        }
    }
    return state
}

/** Shows [content] once the text is loaded; a failure points to Hex and Info. */
@Composable
fun WithLoadedText(source: FileSource, onSwitch: (ViewerId) -> Unit, content: @Composable (LoadedText) -> Unit) {
    when (val s = rememberLoadedText(source)) {
        TextState.Loading -> Loading()
        is TextState.Failed -> Message(
            "Couldn't read this file",
            detail = s.message,
            actions = listOf("Show hex" to { onSwitch(ViewerId.Hex) }, "Show info" to { onSwitch(ViewerId.Info) }),
        )
        is TextState.Ready -> content(s.value)
    }
}

/** The note that goes with a cut-off read. */
fun truncationNote(truncated: Boolean): List<String> =
    if (truncated) listOf("Only the first ${DataLimits.MAX_TEXT_BYTES shr 20} MB is shown.") else emptyList()

object DataClipboard {
    /** Copies [text]; Android 13 and later confirm on their own, older versions get a toast. */
    fun copy(context: Context, label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}
