package com.fileviewer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fileviewer.app.detect.ExtensionTable
import com.fileviewer.app.detect.Family
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.Mono

/**
 * The app's signature mark: the file's own extension, set in mono on its family's hue.
 * Solid in light mode; a tinted glass tile with colored text in dark mode.
 */
@Composable
fun FileTile(label: String, family: Family, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val p = Fv.palette
    val hue = p.family(family)
    val shape = RoundedCornerShape(size * 0.2f)
    val text = label.uppercase().take(4)
    val fontSize = when {
        text.length <= 2 -> size.value * 0.34f
        text.length == 3 -> size.value * 0.3f
        else -> size.value * 0.25f
    }
    Box(
        modifier
            .size(size)
            .background(if (p.dark) hue.copy(alpha = 0.16f) else hue, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = fontSize.sp, letterSpacing = (-0.02).em),
            color = if (p.dark) hue else p.surface,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
fun FolderTile(modifier: Modifier = Modifier, size: Dp = 40.dp, icon: ImageVector = Icons.Outlined.Folder) {
    val p = Fv.palette
    Box(
        modifier.size(size).border(1.5.dp, p.rule, RoundedCornerShape(size * 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = p.muted, modifier = Modifier.size(size * 0.5f))
    }
}

/** Tile for a file name without running detection: extension text, family from the name table. */
@Composable
fun NameTile(fileName: String, modifier: Modifier = Modifier, size: Dp = 40.dp, family: Family? = null) {
    val ext = fileName.substringAfterLast('.', "").takeIf { fileName.lastIndexOf('.') > 0 && it.length in 1..5 }
    val fam = family ?: ExtensionTable.byName(fileName)?.family ?: if (ext == null) Family.Text else Family.Binary
    FileTile(ext ?: shortLabel(fam), fam, modifier, size)
}

fun shortLabel(f: Family): String = when (f) {
    Family.Code -> "</>"
    Family.Text -> "TXT"
    Family.Image -> "IMG"
    Family.Document -> "DOC"
    Family.Media -> "AV"
    Family.Archive -> "ZIP"
    Family.Data -> "DATA"
    Family.Font -> "Aa"
    Family.Binary -> "BIN"
}
