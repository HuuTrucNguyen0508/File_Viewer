package com.fileviewer.app.viewers.code

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.fileviewer.app.ui.theme.Fv
import kotlinx.coroutines.delay

@Composable
fun FindBar(controller: EditorController, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val count = controller.matches.count
    val current = controller.matches.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        delay(200)
        controller.search(query)
    }
    fun step(forward: Boolean) {
        if (forward) controller.next() else controller.previous()
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Find") },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { step(true) }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        Text(
            when {
                query.isEmpty() -> ""
                count == 0 -> "No matches"
                current >= 0 -> "${current + 1} of $count"
                else -> "$count found"
            },
            style = MaterialTheme.typography.labelMedium,
            color = Fv.palette.muted,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = { step(false) }, enabled = count > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Previous match") }
        IconButton(onClick = { step(true) }, enabled = count > 0) { Icon(Icons.Outlined.KeyboardArrowDown, "Next match") }
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close find") }
    }
}
