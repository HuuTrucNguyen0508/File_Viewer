package com.fileviewer.app.viewers.code

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.fileviewer.app.R
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

/** Imperative handle for find and go-to-line on the editor currently on screen. */
@Stable
class EditorController {
    internal var editor: CodeEditor? = null

    val lineCount: Int get() = editor?.lineCount ?: 0

    /** Highlights matches of [query]; returns the number found. */
    fun search(query: String, ignoreCase: Boolean = true): Int {
        val e = editor ?: return 0
        if (query.isEmpty()) { e.searcher.stopSearch(); return 0 }
        e.searcher.search(query, EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, ignoreCase))
        return e.searcher.matchedPositionCount
    }

    fun matchCount(): Int = editor?.searcher?.takeIf { it.hasQuery() }?.matchedPositionCount ?: 0
    fun currentMatch(): Int = editor?.searcher?.takeIf { it.hasQuery() }?.currentMatchedPositionIndex ?: -1
    fun next() { editor?.searcher?.takeIf { it.hasQuery() }?.gotoNext() }
    fun previous() { editor?.searcher?.takeIf { it.hasQuery() }?.gotoPrevious() }
    fun stopSearch() { editor?.searcher?.stopSearch() }

    /** Moves to 1-based [line], clamped to the document. */
    fun goToLine(line: Int) {
        val e = editor ?: return
        val target = (line - 1).coerceIn(0, maxOf(0, e.lineCount - 1))
        e.setSelection(target, 0)
        e.ensurePositionVisible(target, 0)
    }
}

private var monoTypeface: Typeface? = null

private fun mono(context: Context): Typeface =
    monoTypeface ?: (ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE).also { monoTypeface = it }

/**
 * Read-only Sora editor. [scope] is a TextMate scope name, or null for plain text.
 * TextMate setup must have finished before this composes.
 */
@Composable
fun CodeEditorView(
    text: String,
    scope: String?,
    wrap: Boolean,
    dark: Boolean,
    controller: EditorController,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CodeEditor(ctx).apply {
                isEditable = false
                setSoftKeyboardEnabled(false)
                typefaceText = mono(ctx)
                typefaceLineNumber = mono(ctx)
                setTextSize(13f)
                setLineSpacing(2f, 1.1f)
                isCursorAnimationEnabled = false
                setHighlightCurrentLine(false)
                setPinLineNumber(true)
                setDividerWidth(0f)
                TextMate.applyTheme(dark)
                colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                controller.editor = this
            }
        },
        update = { editor ->
            TextMate.applyTheme(dark)
            if (editor.tag != scope to text) {
                editor.setEditorLanguage(scope?.let { TextMateLanguage.create(it, false) } ?: EmptyLanguage())
                editor.setText(text)
                editor.tag = scope to text
            }
            if (editor.isWordwrap != wrap) editor.setWordwrap(wrap)
            controller.editor = editor
        },
        onRelease = { editor ->
            if (controller.editor === editor) controller.editor = null
            editor.release()
        },
    )
}
