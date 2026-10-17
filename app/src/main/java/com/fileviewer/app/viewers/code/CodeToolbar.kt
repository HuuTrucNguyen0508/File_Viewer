package com.fileviewer.app.viewers.code

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fileviewer.app.detect.CodeLanguages
import com.fileviewer.app.ui.theme.Fv
import java.nio.charset.Charset

@Composable
fun CodeToolbar(
    languageLabel: String,
    charset: Charset,
    wrap: Boolean,
    onFind: () -> Unit,
    onGoToLine: () -> Unit,
    onWrap: () -> Unit,
    onLanguage: (String?) -> Unit,
    onCharset: (Charset) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onFind) { Icon(Icons.Outlined.Search, contentDescription = "Find in file") }
        IconButton(onClick = onGoToLine) { Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Go to line") }
        IconToggleButton(checked = wrap, onCheckedChange = { onWrap() }) {
            Icon(
                Icons.AutoMirrored.Outlined.WrapText,
                contentDescription = if (wrap) "Turn off word wrap" else "Turn on word wrap",
                tint = if (wrap) Fv.palette.accent else Fv.palette.muted,
            )
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            PickerButton(languageLabel, "Syntax") { close ->
                DropdownMenuItem(text = { Text("Plain text") }, onClick = { onLanguage(null); close() })
                CodeLanguages.pickable.forEach { id ->
                    DropdownMenuItem(text = { Text(CodeLanguages.label(id)) }, onClick = { onLanguage(id); close() })
                }
            }
            PickerButton(charset.label(), "Encoding") { close ->
                TextLoader.pickable.forEach { cs ->
                    DropdownMenuItem(text = { Text(cs.label()) }, onClick = { onCharset(cs); close() })
                }
            }
        }
    }
}

@Composable
private fun PickerButton(label: String, description: String, items: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Fv.palette.ink, maxLines = 1)
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = description, modifier = Modifier.size(18.dp), tint = Fv.palette.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { items { open = false } }
    }
}
