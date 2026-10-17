package com.fileviewer.app.ui

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatSize(bytes: Long?): String {
    if (bytes == null) return "Unknown size"
    if (bytes < 1024) return if (bytes == 1L) "1 byte" else "$bytes bytes"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (v >= 100) "%.0f %s".format(Locale.ROOT, v, units[i]) else "%.1f %s".format(Locale.ROOT, v, units[i])
}

/** Time only for today, date for anything older. */
fun formatDate(millis: Long?): String {
    if (millis == null || millis <= 0) return ""
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val fmt = if (sameDay) DateFormat.getTimeInstance(DateFormat.SHORT) else DateFormat.getDateInstance(DateFormat.MEDIUM)
    return fmt.format(Date(millis))
}

fun formatDateTime(millis: Long?): String =
    if (millis == null || millis <= 0) "Unknown" else DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(millis))
