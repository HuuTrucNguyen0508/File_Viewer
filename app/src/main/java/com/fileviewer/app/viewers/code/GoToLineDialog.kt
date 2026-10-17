package com.fileviewer.app.viewers.code

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/** Shared by the code view (lines) and the hex view (offsets): one number field and a Go button. */
@Composable
fun NumberDialog(
    title: String,
    label: String,
    keyboardType: KeyboardType,
    parse: (String) -> Long?,
    onDismiss: () -> Unit,
    onGo: (Long) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val value = parse(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                isError = text.isNotEmpty() && value == null,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { value?.let(onGo) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onGo) }, enabled = value != null) { Text("Go") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun GoToLineDialog(lineCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    NumberDialog(
        title = "Go to line",
        label = if (lineCount > 0) "Line (1 to $lineCount)" else "Line",
        keyboardType = KeyboardType.Number,
        parse = { s -> s.trim().toLongOrNull()?.takeIf { it >= 1 } },
        onDismiss = onDismiss,
        onGo = { onGo(it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) },
    )
}
