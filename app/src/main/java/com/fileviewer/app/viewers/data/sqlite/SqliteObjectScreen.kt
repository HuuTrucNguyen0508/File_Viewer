package com.fileviewer.app.viewers.data.sqlite

import com.fileviewer.app.source.Limits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.Family
import com.fileviewer.app.ui.components.Loading
import com.fileviewer.app.ui.components.Message
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.MonoBody
import com.fileviewer.app.viewers.code.CodeEditorView
import com.fileviewer.app.viewers.code.EditorController
import com.fileviewer.app.viewers.code.TextMate
import com.fileviewer.app.viewers.data.DataBar
import com.fileviewer.app.viewers.data.table.DataTable
import kotlinx.coroutines.CancellationException

private sealed interface PageState {
    data object Loading : PageState
    class Ready(val page: DbPage) : PageState
    class Failed(val message: String) : PageState
}

/** One table or view: its rows a page at a time, or its schema. */
@Composable
internal fun ObjectScreen(session: SqliteSession, obj: DbObject, count: Long?, onBack: () -> Unit) {
    var showSchema by rememberSaveable(obj.name) { mutableStateOf(false) }
    var pageIndex by rememberSaveable(obj.name) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ObjectBar(obj, showSchema, onBack) { showSchema = it }
        if (showSchema) Schema(session, obj.name) else Rows(session, obj.name, count, pageIndex) { pageIndex = it }
    }
}

@Composable
private fun ObjectBar(obj: DbObject, showSchema: Boolean, onBack: () -> Unit, onMode: (Boolean) -> Unit) {
    val p = Fv.palette
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to all tables") }
        Text(
            obj.name, style = MonoBody.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize), color = p.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        ModeTab("Rows", !showSchema) { onMode(false) }
        ModeTab("Schema", showSchema) { onMode(true) }
    }
    HorizontalDivider(color = p.rule)
}

/** A tab with a Data-hue edge on top while selected, like the workbook tabs on documents. */
@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = Fv.palette
    Column(Modifier.width(IntrinsicSize.Min).selectable(selected = selected, role = Role.Tab, onClick = onClick).heightIn(min = 48.dp)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) p.family(Family.Data) else p.paper))
        Box(Modifier.padding(horizontal = 14.dp).heightIn(min = 46.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) p.ink else p.muted)
        }
    }
}

@Composable
private fun Rows(session: SqliteSession, name: String, count: Long?, pageIndex: Int, onPage: (Int) -> Unit) {
    val state by produceState<PageState>(PageState.Loading, name, pageIndex) {
        value = PageState.Loading
        value = try {
            PageState.Ready(cancellableQuery { session.page(name, pageIndex, it) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PageState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
    when (val s = state) {
        PageState.Loading -> Loading()
        is PageState.Failed -> Message("Couldn't read the rows", detail = s.message)
        is PageState.Ready -> {
            if (s.page.rows.isEmpty() && pageIndex == 0) return Message("This table has no rows")
            Column(Modifier.fillMaxSize()) {
                DataTable(
                    s.page.columns, s.page.rows, firstNumber = pageIndex * Limits.SQL_PAGE_ROWS + 1,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Pager(pageIndex, s.page, count?.takeIf { it >= 0 }, onPage)
            }
        }
    }
}

@Composable
private fun Pager(pageIndex: Int, page: DbPage, total: Long?, onPage: (Int) -> Unit) {
    val p = Fv.palette
    HorizontalDivider(color = p.rule)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onPage(pageIndex - 1) }, enabled = pageIndex > 0) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous page")
        }
        Text(SqlText.rangeLabel(pageIndex, page.rows.size, total), style = MaterialTheme.typography.bodyMedium, color = p.ink)
        IconButton(onClick = { onPage(pageIndex + 1) }, enabled = page.hasMore) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next page")
        }
    }
}

@Composable
private fun Schema(session: SqliteSession, name: String) {
    val context = LocalContext.current
    val sql by produceState<Result<String>?>(null, name) {
        value = try { Result.success(cancellableQuery { session.schema(name) }) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }
    val scopes by produceState<Map<String, String>?>(null) { value = TextMate.scopes(context) }
    val controller = remember { EditorController() }
    val text = sql
    val map = scopes
    when {
        text == null || map == null -> Loading()
        text.isFailure -> Message("Couldn't read the schema", detail = text.exceptionOrNull()?.message)
        else -> CodeEditorView(
            text = text.getOrThrow().ifEmpty { "-- No CREATE statement is stored for this object." },
            scope = map["sql"], wrap = true, dark = Fv.palette.dark, controller = controller, modifier = Modifier.fillMaxSize(),
        )
    }
}
