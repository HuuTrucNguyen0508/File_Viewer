package com.fileviewer.app.viewers.misc.cert

import java.security.MessageDigest

/** Upper-case hex pairs joined by colons, the form certificate tools print. */
fun ByteArray.colonHex(): String = joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

fun ByteArray.digest(algorithm: String): String = MessageDigest.getInstance(algorithm).digest(this).colonHex()
