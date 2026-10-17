package com.fileviewer.app.viewers.misc.apk

object AndroidVersions {
    private val NAMES = mapOf(
        21 to "5.0", 22 to "5.1", 23 to "6.0", 24 to "7.0", 25 to "7.1", 26 to "8.0", 27 to "8.1", 28 to "9",
        29 to "10", 30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15", 36 to "16",
    )

    /** "Android 11 (API 30)"; just "API n" for levels this table doesn't know. */
    fun describe(api: Int): String = NAMES[api]?.let { "Android $it (API $api)" } ?: "API $api"
}
