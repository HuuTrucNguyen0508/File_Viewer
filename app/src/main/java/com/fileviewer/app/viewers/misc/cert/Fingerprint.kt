package com.fileviewer.app.viewers.misc.cert

import java.security.MessageDigest

/** Upper-case hex pairs joined by colons, the form certificate tools print. */
fun ByteArray.colonHex(): String = joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

fun ByteArray.digest(algorithm: String): String = MessageDigest.getInstance(algorithm).digest(this).colonHex()

/** "17 Oct 2026" in the device's locale. */
fun java.time.Instant.day(): String = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date.from(this))
