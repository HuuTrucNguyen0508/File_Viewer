package com.fileviewer.app.viewers.misc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.MonoBody

/** A titled group heading, matching the Info viewer. */
@Composable
fun MiscSection(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Fv.palette.ink, modifier = modifier.padding(top = 20.dp, bottom = 6.dp))
}

/** A label and value row; long values wrap and stay selectable inside a `SelectionContainer`. */
@Composable
fun MiscFact(label: String, value: String, mono: Boolean = false, valueColor: Color = Fv.palette.ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted, modifier = Modifier.width(112.dp))
        Text(value, style = if (mono) MonoBody else MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(1f))
    }
}

/** A short status word on a tinted ground: "Expired", "Self-signed", "Sensitive". */
@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
