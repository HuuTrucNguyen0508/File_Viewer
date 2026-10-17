package com.fileviewer.app.viewers.misc.font

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.Mono

private fun codePointLabel(cp: Int) = "U+%04X".format(cp)

@Composable
fun GlyphCell(codePoint: Int, family: FontFamily, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.aspectRatio(1f)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else Fv.palette.rule, shape)
            .clickable(role = Role.Button, onClickLabel = "Inspect ${codePointLabel(codePoint)}", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(String(Character.toChars(codePoint)), fontFamily = family, fontSize = 22.sp, color = Fv.palette.ink)
    }
}

/** The tapped character at reading size with its code point and Unicode name, pinned below the grid. */
@Composable
fun GlyphReadout(codePoint: Int, family: FontFamily, accent: Color) {
    Column(Modifier.fillMaxWidth().background(Fv.palette.surface)) {
        HorizontalDivider(color = Fv.palette.rule)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).border(2.dp, accent, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Text(String(Character.toChars(codePoint)), fontFamily = family, fontSize = 34.sp, color = Fv.palette.ink)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(codePointLabel(codePoint), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Fv.palette.ink)
                Text(
                    (Character.getName(codePoint) ?: "Unassigned").lowercase().replaceFirstChar(Char::titlecase),
                    style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted,
                )
            }
        }
    }
}
