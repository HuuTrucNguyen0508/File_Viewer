package com.fileviewer.app.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import java.io.File

object StorageAccess {
    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    /** The system "All files access" page for this app. */
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    data class Location(val label: String, val dir: File)

    /** Internal storage, Downloads, and any mounted SD card or USB drive. */
    fun locations(context: Context): List<Location> {
        val sm = context.getSystemService(StorageManager::class.java)
        val volumes = sm.storageVolumes.mapNotNull { v ->
            val dir = v.directory ?: return@mapNotNull null
            Location(if (v.isPrimary) "Internal storage" else v.getDescription(context), dir)
        }
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return volumes.take(1) + Location("Downloads", downloads) + volumes.drop(1)
    }
}
