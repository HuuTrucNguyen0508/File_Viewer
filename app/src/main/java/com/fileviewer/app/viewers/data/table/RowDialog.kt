package com.fileviewer.app.viewers.data.table

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.MonoBody

/** One row in full, each column named above its value; the text can be selected and copied. */
@Composable
fun RowDialog(number: Int, columns: List<String>, row: List<String?>, onDismiss: () -> Unit) {
    val p = Fv.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text("Row $number", style = MaterialTheme.typography.titleLarge) },
        text = {
            SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    columns.forEachIndexed { i, name ->
                        val value = row.getOrNull(i)
                        Text(name, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = if (i == 0) 0.dp else 12.dp))
                        Text(value ?: "NULL", style = MonoBody, color = if (value == null) p.muted else p.ink)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
