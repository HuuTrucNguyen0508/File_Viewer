package com.fileviewer.app.browser

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Recently opened files. Only durable references are stored: paths from the in-app browser,
 * and URIs whose read permission was persisted. Temporary intent grants are never stored.
 */
class RecentsStore(context: Context) {
    data class Recent(val isUri: Boolean, val value: String, val name: String, val openedAt: Long)

    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    fun all(): List<Recent> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let { Recent(it.getBoolean("uri"), it.getString("value"), it.getString("name"), it.getLong("at")) }
        }
    }.getOrDefault(emptyList())

    fun add(isUri: Boolean, value: String, name: String) {
        val updated = listOf(Recent(isUri, value, name, System.currentTimeMillis())) + all().filterNot { it.value == value }
        save(updated.take(MAX))
    }

    fun remove(value: String) = save(all().filterNot { it.value == value })

    private fun save(list: List<Recent>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("uri", it.isUri).put("value", it.value).put("name", it.name).put("at", it.openedAt)) }
        prefs.edit { putString(KEY, arr.toString()) }
    }

    private companion object {
        const val KEY = "items"
        const val MAX = 30
    }
}
