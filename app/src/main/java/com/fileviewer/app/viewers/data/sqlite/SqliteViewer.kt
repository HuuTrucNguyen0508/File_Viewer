package com.fileviewer.app.viewers.data.sqlite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.Family
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.LocalFileSource
import com.fileviewer.app.source.LimitExceededException
import com.fileviewer.app.source.acquireResource
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.MonoBody
import com.fileviewer.app.viewer.ViewerId
import com.fileviewer.app.viewers.data.DataBar
import com.fileviewer.app.viewers.data.DataNote
import com.fileviewer.app.viewers.misc.Badge
import com.fileviewer.app.viewers.misc.WithRealFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runInterruptible
import java.io.File

private sealed interface Opened {
    data object Opening : Opened
    class Ready(val session: SqliteSession, val objects: List<DbObject>) : Opened
    class Failed(val message: String) : Opened
}

/** Browses a SQLite database read-only: tables and views with row counts, schema, rows 100 at a time. */
@Composable
fun SqliteViewer(source: FileSource, onSwitch: (ViewerId) -> Unit) {
    WithRealFile(source) { file -> Browser(file, source !is LocalFileSource, onSwitch) }
}

@Composable
private fun Browser(file: File, warnMissingWal: Boolean, onSwitch: (ViewerId) -> Unit) {
    val context = LocalContext.current
    val opened by produceState<Opened>(Opened.Opening, file) {
        var session: SqliteSession? = null
        try {
            session = acquireResource { runInterruptible { SqliteSession.open(context, file) } }
            value = Opened.Ready(session, runInterruptible(Dispatchers.IO) { session.objects() })
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value = Opened.Failed((e.message ?: e.javaClass.simpleName) + if (e is LimitExceededException) "." else ". It may be encrypted or damaged.")
        } finally {
            session?.close()
        }
    }
    when (val o = opened) {
        Opened.Opening -> Loading()
        is Opened.Failed -> Message(
            "This database couldn't be opened",
            detail = o.message,
            actions = listOf("Show hex" to { onSwitch(ViewerId.Hex) }, "Show info" to { onSwitch(ViewerId.Info) }),
        )
        is Opened.Ready -> Column(Modifier.fillMaxSize()) {
            if (warnMissingWal && o.session.missingWal) DataNote("Only the main database is available. Changes still in its WAL file aren't included.")
            Column(Modifier.weight(1f)) { Objects(o.session, o.objects) }
        }
    }
}

@Composable
private fun Objects(session: SqliteSession, objects: List<DbObject>) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val counts = remember(session) { mutableStateMapOf<String, Long>() }
    LaunchedEffect(session) {
        for (obj in objects) {
            counts[obj.name] = try { cancellableQuery { session.count(obj.name, it) } } catch (e: CancellationException) { throw e } catch (_: Exception) { -1L }
        }
    }
    BackHandler(enabled = selected != null) { selected = null }
    val current = objects.firstOrNull { it.name == selected }
    if (current != null) {
        ObjectScreen(session, current, counts[current.name], onBack = { selected = null })
    } else {
        ObjectList(objects, counts) { selected = it.name }
    }
}

@Composable
private fun ObjectList(objects: List<DbObject>, counts: Map<String, Long>, onOpen: (DbObject) -> Unit) {
    if (objects.isEmpty()) return Message("This database has no tables or views")
    val p = Fv.palette
    val tables = objects.count { !it.isView }
    val views = objects.size - tables
    Column(Modifier.fillMaxSize()) {
        DataBar {
            Text(
                listOfNotNull(
                    "$tables ${if (tables == 1) "table" else "tables"}",
                    "$views ${if (views == 1) "view" else "views"}".takeIf { views > 0 },
                ).joinToString(", "),
                style = MaterialTheme.typography.bodySmall, color = p.muted,
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(objects, key = { it.name }) { obj ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(obj) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        obj.name,
                        style = MonoBody.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        color = if (SqlText.isInternal(obj.name)) p.muted else p.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (obj.isView) Badge("view", p.family(Family.Data), Modifier.padding(start = 8.dp))
                    Text(
                        when (val n = counts[obj.name]) { null -> "\u2026"; -1L -> "\u2013"; else -> SqlText.rowsLabel(n) },
                        style = MaterialTheme.typography.bodySmall, color = p.muted,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                HorizontalDivider(color = p.rule)
            }
        }
    }
}
