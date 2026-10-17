package com.fileviewer.app.viewers.documents

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Display text for numeric XLSX cells: dates and times, percentages, fixed decimals with
 * thousands separators, and Excel's "General" number display. Other formats show the number.
 */
class XlsxFormats(private val codes: Map<Int, String>, private val styleFormats: List<Int>, private val date1904: Boolean) {

    fun display(raw: String, styleIndex: Int?): String {
        val v = raw.trim().toDoubleOrNull() ?: return raw
        val id = styleIndex?.let { styleFormats.getOrNull(it) } ?: 0
        val code = codes[id] ?: BUILTIN[id] ?: "General"
        val shape = shapeOf(code)
        return when {
            shape.date || shape.time -> date(v, shape) ?: general(v)
            shape.percent -> general(v * 100) + "%"
            shape.decimals != null -> fixed(v, shape.decimals, shape.grouping)
            else -> general(v)
        }
    }

    private data class Shape(val date: Boolean, val time: Boolean, val seconds: Boolean, val percent: Boolean, val decimals: Int?, val grouping: Boolean)

    private val shapes = HashMap<String, Shape>()

    private fun shapeOf(code: String): Shape = shapes.getOrPut(code) {
        // Only the first (positive) section matters; quoted text, [colors] and escapes aren't format letters.
        val bare = code.substringBefore(';').replace(Regex("\"[^\"]*\"|\\\\.|_.|\\*."), "")
        val noBrackets = bare.replace(Regex("\\[[^\\]]*]"), "")
        val lower = noBrackets.lowercase()
        val elapsed = Regex("\\[[hms]+]", RegexOption.IGNORE_CASE).containsMatchIn(bare)
        val hasDate = lower.any { it == 'y' || it == 'd' } || (lower.contains('m') && !lower.contains('h') && !lower.contains('s') && !lower.contains('0'))
        val hasTime = lower.contains('h') || lower.contains('s') || elapsed
        val fixed = Regex("^[#,0]*0(\\.(0+))?$").find(noBrackets.trim().replace(Regex("[$€£¥]"), ""))
        Shape(
            date = hasDate && !lower.contains("general"),
            time = hasTime && !lower.contains("general"),
            seconds = lower.contains('s'),
            percent = lower.contains('%'),
            decimals = fixed?.let { it.groupValues[2].length },
            grouping = fixed != null && noBrackets.contains(','),
        )
    }

    private fun date(serial: Double, shape: Shape): String? {
        if (serial < 0 || serial > 2_958_465) return null
        var days = floor(serial).toLong()
        val seconds = ((serial - days) * 86_400).roundToLong().let { if (it >= 86_400) { days++; 0L } else it }
        val base = when {
            date1904 -> LocalDate.of(1904, 1, 1)
            days < 60 -> LocalDate.of(1899, 12, 31) // before Excel's phantom 1900-02-29
            else -> LocalDate.of(1899, 12, 30)
        }
        val dt = LocalDateTime.of(base.plusDays(days), java.time.LocalTime.ofSecondOfDay(seconds))
        val pattern = when {
            shape.date && shape.time -> if (shape.seconds) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd HH:mm"
            shape.date -> "yyyy-MM-dd"
            else -> if (shape.seconds) "HH:mm:ss" else "HH:mm"
        }
        return dt.format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT))
    }

    private fun fixed(v: Double, decimals: Int, grouping: Boolean): String {
        val pattern = (if (grouping) "#,##0" else "0") + (if (decimals > 0) "." + "0".repeat(decimals) else "")
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.ROOT)).format(v)
    }

    companion object {
        /** Excel's built-in format ids that aren't spelled out in styles.xml. */
        private val BUILTIN = mapOf(
            0 to "General", 1 to "0", 2 to "0.00", 3 to "#,##0", 4 to "#,##0.00", 9 to "0%", 10 to "0.00%",
            11 to "0.00E+00", 12 to "# ?/?", 13 to "# ??/??", 14 to "yyyy-mm-dd", 15 to "d-mmm-yy", 16 to "d-mmm",
            17 to "mmm-yy", 18 to "h:mm AM/PM", 19 to "h:mm:ss AM/PM", 20 to "h:mm", 21 to "h:mm:ss", 22 to "yyyy-mm-dd h:mm",
            37 to "#,##0 ;(#,##0)", 38 to "#,##0 ;[Red](#,##0)", 39 to "#,##0.00;(#,##0.00)", 40 to "#,##0.00;[Red](#,##0.00)",
            45 to "mm:ss", 46 to "[h]:mm:ss", 47 to "mmss.0", 48 to "##0.0E+0", 49 to "@",
        )

        /** Excel's General display: up to 15 significant digits, no trailing zeros. */
        fun general(v: Double): String {
            if (v.isNaN() || v.isInfinite()) return v.toString()
            if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
            val bd = BigDecimal(v).round(MathContext(15)).stripTrailingZeros()
            return if (abs(v) >= 1e-9 && abs(v) < 1e15) bd.toPlainString() else bd.toString()
        }
    }
}
