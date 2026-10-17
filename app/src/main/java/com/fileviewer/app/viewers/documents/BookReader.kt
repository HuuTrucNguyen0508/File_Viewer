package com.fileviewer.app.viewers.documents

import android.webkit.WebResourceResponse
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.viewer.html.HtmlFragmentView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Reads an EPUB one chapter at a time, with previous/next and a chapter list. */
@Composable
fun BookReader(key: String, book: BookPreview, pkg: DocPackage, resolver: ((String) -> WebResourceResponse?)?) {
    var index by rememberSaveable(key) { mutableIntStateOf(0) }
    var listOpen by rememberSaveable(key) { mutableStateOf(false) }
    val at = index.coerceIn(0, book.chapters.lastIndex)
    val chapter = book.chapters[at]
    val loaded by produceState<Result<ChapterHtml.Result>?>(null, chapter) {
        value = null
        value = runInterruptible(Dispatchers.IO) { runCatching { EpubParser.chapter(pkg, chapter) } }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val r = loaded
            when {
                r == null -> Loading()
                r.isFailure -> Message("This chapter couldn't be read", detail = r.exceptionOrNull()?.message)
                else -> {
                    val c = r.getOrThrow()
                    val cut = if (c.truncated) "<p class=\"empty\">${esc(CUT_NOTE)}</p>" else ""
                    HtmlFragmentView(DocCss.chapter(c.html + cut), Modifier.fillMaxSize(), chapterTitle(chapter, at), resolver)
                }
            }
        }
        ChapterBar(book, at, onMove = { index = it }, onList = { listOpen = true })
    }
    if (listOpen) ChapterList(book, at, onPick = { index = it; listOpen = false }, onDismiss = { listOpen = false })
}

private fun chapterTitle(c: Chapter, i: Int) = c.title ?: "Section ${i + 1}"

@Composable
private fun ChapterBar(book: BookPreview, at: Int, onMove: (Int) -> Unit, onList: () -> Unit) {
    val p = Fv.palette
    HorizontalDivider(color = p.rule)
    Row(Modifier.fillMaxWidth().background(p.paper).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMove(at - 1) }, enabled = at > 0) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous chapter")
        }
        Column(
            Modifier.weight(1f).clickable(onClickLabel = "Show chapters", onClick = onList).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(chapterTitle(book.chapters[at], at), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text("${at + 1} of ${book.chapters.size}", style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
        IconButton(onClick = { onMove(at + 1) }, enabled = at < book.chapters.lastIndex) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next chapter")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterList(book: BookPreview, at: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = Fv.palette
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Text(
            book.title ?: "Chapters",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        val list = rememberLazyListState(initialFirstVisibleItemIndex = (at - 3).coerceAtLeast(0))
        LazyColumn(state = list) {
            itemsIndexed(book.chapters) { i, c ->
                val current = i == at
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(i) }.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.bodyMedium, color = if (current) p.accent else p.muted, modifier = Modifier.width(40.dp))
                    Text(
                        chapterTitle(c, i),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (current) p.accent else p.ink,
                        fontWeight = if (current) FontWeight.SemiBold else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
