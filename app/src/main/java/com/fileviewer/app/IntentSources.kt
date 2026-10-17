package com.fileviewer.app

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.fileviewer.app.source.ContentUriSource
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.LocalFileSource
import com.fileviewer.app.source.SeekableSource
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/** Turns VIEW / SEND / SEND_MULTIPLE intents into sources. */
object IntentSources {

    fun from(intent: Intent, resolver: ContentResolver): List<FileSource> = when (intent.action) {
        Intent.ACTION_VIEW -> listOfNotNull(intent.data?.let { source(it, intent.type, resolver) })
        Intent.ACTION_SEND -> {
            val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            val uris = listOfNotNull(stream) + clipUris(intent)
            uris.distinct().take(1).map { source(it, intent.type, resolver) }.ifEmpty {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { listOf(TextSnippetSource(it, intent.getStringExtra(Intent.EXTRA_SUBJECT))) }.orEmpty()
            }
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            val streams = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            (streams + clipUris(intent)).distinct().map { source(it, null, resolver) }
        }
        else -> emptyList()
    }

    fun source(uri: Uri, mime: String?, resolver: ContentResolver): FileSource =
        if (uri.scheme == ContentResolver.SCHEME_FILE && uri.path != null) LocalFileSource(File(uri.path!!))
        else ContentUriSource.from(resolver, uri, mime)

    private fun clipUris(intent: Intent): List<Uri> {
        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }
}

/** Plain text shared without a file, shown as a text file. */
private class TextSnippetSource(text: String, subject: String?) : FileSource {
    private val bytes = text.toByteArray()
    override val key = "text:${text.hashCode()}:${bytes.size}"
    override val name = (subject?.takeIf { it.isNotBlank() }?.take(60) ?: "Shared text") + ".txt"
    override val size: Long = bytes.size.toLong()
    override val mimeHint = "text/plain"
    override val lastModified: Long? = null
    override val displayPath = "Shared text"
    override fun openStream(): InputStream = ByteArrayInputStream(bytes)
    override fun seekableOrNull(): SeekableSource? = null
}
