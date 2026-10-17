package com.fileviewer.app.viewers.misc.font

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fileviewer.app.detect.Family
import com.fileviewer.app.detect.FileKind
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.ui.theme.Fv
import com.fileviewer.app.ui.theme.Mono
import com.fileviewer.app.viewers.misc.MiscFact
import com.fileviewer.app.viewers.misc.MiscSection
import java.text.NumberFormat

private const val PANGRAM = "Sphinx of black quartz, judge my vow."
private const val SPECIMEN_WORD = "Hamburgefonstiv"
private const val DIGITS = "0123456789 & @ # % ? ! ( ) [ ] { }"
private val SIZES = listOf(12, 16, 24, 36)


/** The specimen sheet: name in its own face, a size ladder, a try-it field, then the glyph grid. */
@Composable
fun Specimen(source: FileSource, kind: FileKind.Font, font: LoadedFont) {
    val family = remember(font) { FontFamily(font.typeface) }
    val accent = Fv.palette.family(Family.Font)
    val sections = remember(font) { GlyphSets.sections(font.info?.coverage) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var sample by rememberSaveable { mutableStateOf("") }
    val names = font.info?.names

    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(48.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(
                        names?.family ?: source.name.substringBeforeLast('.'),
                        fontFamily = family, fontSize = 40.sp, lineHeight = 46.sp, color = Fv.palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(names?.style, names?.version).joinToString("  "),
                        style = MaterialTheme.typography.bodyMedium, color = Fv.palette.muted,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { FontFacts(kind, font.info) }

            item(span = { GridItemSpan(maxLineSpan) }) { MiscSection("Sizes") }
            items(SIZES, span = { GridItemSpan(maxLineSpan) }) { SizeLine(it, PANGRAM, family, accent) }
            item(span = { GridItemSpan(maxLineSpan) }) { SizeLine(56, SPECIMEN_WORD, family, accent) }
            item(span = { GridItemSpan(maxLineSpan) }) { SizeLine(20, DIGITS, family, accent) }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    MiscSection("Try your own text")
                    OutlinedTextField(
                        value = sample,
                        onValueChange = { sample = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = family, fontSize = 32.sp, lineHeight = 40.sp),
                        placeholder = { Text("Type here. The file isn't changed.", style = MaterialTheme.typography.bodyMedium) },
                    )
                }
            }

            for (section in sections) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MiscSection(
                        "${section.title}  ${NumberFormat.getIntegerInstance().format(section.codePoints.size)}",
                        Modifier.padding(top = 24.dp),
                    )
                }
                items(section.codePoints, key = { it }) { cp -> GlyphCell(cp, family, cp == selected, accent) { selected = cp } }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Text("", Modifier.padding(bottom = 16.dp)) }
        }
        if (selected >= 0) GlyphReadout(selected, family, accent)
    }
}

@Composable
private fun FontFacts(kind: FileKind.Font, info: FontInfo?) {
    val names = info?.names
    val format = buildString {
        append(kind.format.display)
        if (info != null) {
            append(", ").append(info.outlines.lowercase())
            if (info.faceCount > 1) append(". Face 1 of ${info.faceCount}")
        }
    }
    SelectionContainer {
        Column(Modifier.padding(top = 12.dp)) {
            HorizontalDivider(color = Fv.palette.rule)
            names?.fullName?.let { MiscFact("Full name", it) }
            names?.postScript?.let { MiscFact("PostScript", it, mono = true) }
            names?.designer?.let { MiscFact("Designer", it) }
            names?.license?.let { MiscFact("License", it) }
            names?.copyright?.let { MiscFact("Copyright", it) }
            MiscFact("Format", format)
            info?.coverage?.let { MiscFact("Characters", "${NumberFormat.getIntegerInstance().format(it.count)} mapped") }
        }
    }
}

/** One line of the ladder; the size sits in the margin like a note on a type specimen sheet. */
@Composable
private fun SizeLine(size: Int, text: String, family: FontFamily, accent: Color) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "$size", fontFamily = Mono, fontSize = 12.sp, color = accent,
            modifier = Modifier.width(32.dp).padding(top = (size / 6).dp),
        )
        Text(text, fontFamily = family, fontSize = size.sp, lineHeight = (size * 1.25f).sp, color = Fv.palette.ink, modifier = Modifier.weight(1f))
    }
}
