package com.fileviewer.app.viewers.code

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.CodeLanguages
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.detect.sourceLanguage
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.Limits
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.ui.formatSize
import com.fileviewer.app.ui.theme.Fv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

private sealed interface Bytes {
    data object Loading : Bytes
    data class Ready(val data: ByteArray) : Bytes
    data object TooLarge : Bytes
    data class Failed(val message: String) : Bytes
}

/**
 * Text and code. [highlight] false is the forced "Text" view: same editor, no grammar.
 * Files over [Limits.MAX_EDITOR_TEXT_BYTES] open in [PagedTextViewer] instead.
 */
@Composable
fun CodeViewer(source: FileSource, kind: FileKind, highlight: Boolean) {
    val bytes by produceState<Bytes>(Bytes.Loading, source) {
        value = withContext(Dispatchers.IO) {
            try {
                source.openStream().use { TextLoader.readCapped(it) }?.let { Bytes.Ready(it) } ?: Bytes.TooLarge
            } catch (e: Exception) {
                Bytes.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }
    when (val b = bytes) {
        Bytes.Loading -> Loading()
        Bytes.TooLarge -> PagedTextViewer(source, (kind as? FileKind.Text)?.charset)
        is Bytes.Failed -> Message("Couldn't read this file", b.message)
        is Bytes.Ready -> LoadedCode(b.data, kind, highlight)
    }
}

@Composable
private fun LoadedCode(data: ByteArray, kind: FileKind, highlight: Boolean) {
    val context = LocalContext.current
    val dark = Fv.palette.dark
    val tooBigToHighlight = data.size > Limits.MAX_HIGHLIGHT_BYTES
    val defaultLanguage = if (highlight && !tooBigToHighlight) kind.sourceLanguage else null

    var charset by remember(data) { mutableStateOf((kind as? FileKind.Text)?.charset ?: TextLoader.detectCharset(data)) }
    var language by remember(data, highlight) { mutableStateOf(defaultLanguage) }
    var wrap by rememberSaveable { mutableStateOf(kind.sourceLanguage == null) }
    var finding by remember { mutableStateOf(false) }
    var goingToLine by remember { mutableStateOf(false) }
    val controller = remember { EditorController() }

    val scopes by produceState<Map<String, String>?>(null) { value = TextMate.scopes(context) }
    val display by produceState<TextLoader.Display?>(null, data, charset) {
        value = withContext(Dispatchers.Default) { TextLoader.softBreak(TextLoader.decode(data, charset)) }
    }

    Column(Modifier.fillMaxSize()) {
        CodeToolbar(
            languageLabel = language?.let(CodeLanguages::label) ?: "Plain text",
            charset = charset,
            wrap = wrap,
            onFind = { finding = !finding; if (!finding) controller.stopSearch() },
            onGoToLine = { goingToLine = true },
            onWrap = { wrap = !wrap },
            onLanguage = { language = it },
            onCharset = { charset = it },
        )
        if (finding) FindBar(controller, onClose = { finding = false; controller.stopSearch() })
        val notes = listOfNotNull(
            "Highlighting is off for files over ${formatSize(Limits.MAX_HIGHLIGHT_BYTES)}.".takeIf { tooBigToHighlight && highlight },
            "Lines longer than ${Limits.MAX_LINE_DISPLAY_CHARS} characters are split for display.".takeIf { display?.splitLines == true },
        )
        notes.forEach { Note(it) }
        val shown = display
        val scopeMap = scopes
        if (shown == null || scopeMap == null) {
            Loading()
        } else {
            CodeEditorView(
                text = shown.text,
                scope = language?.let { scopeMap[it] },
                wrap = wrap,
                dark = dark,
                controller = controller,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    if (goingToLine) {
        GoToLineDialog(
            lineCount = controller.lineCount,
            onDismiss = { goingToLine = false },
            onGo = { controller.goToLine(it); goingToLine = false },
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Fv.palette.muted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Charset display name as people know it ("UTF-8", "windows-1252"). */
fun Charset.label(): String = name()
