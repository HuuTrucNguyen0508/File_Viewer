package com.fileviewer.app.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.fileviewer.app.source.ContentUriSource
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.LocalFileSource
import com.fileviewer.app.source.extension
import com.fileviewer.app.source.materialize

object ShareActions {
    private const val AUTHORITY = "com.fileviewer.app.files"

    fun mimeOf(source: FileSource): String =
        source.mimeHint ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(source.extension) ?: "application/octet-stream"

    /** A URI another app can read: the original content URI, or a FileProvider URI for paths and cached entries. */
    suspend fun uriFor(context: Context, source: FileSource): Uri = when (source) {
        is ContentUriSource -> source.uri
        is LocalFileSource -> FileProvider.getUriForFile(context, AUTHORITY, source.file, source.name)
        else -> source.materialize().use { s ->
            FileProvider.getUriForFile(context, AUTHORITY, s.file ?: error("materialized file has no path"), source.name)
        }
    }

    fun share(context: Context, uri: Uri, mime: String) {
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
    }

    fun openWith(context: Context, uri: Uri, mime: String) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(view, null))
    }

    fun copyPath(context: Context, source: FileSource) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Path", source.displayPath))
    }
}
