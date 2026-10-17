package com.fileviewer.app.viewers.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.fileviewer.app.source.ContentUriSource
import com.fileviewer.app.source.FileSource
import com.fileviewer.app.source.LocalFileSource
import com.fileviewer.app.source.materialize

/** Container and codec facts from the platform, independent of whether playback works. */
object MediaProbe {

    data class Facts(val tags: List<Pair<String, String>>, val tracks: List<String>)

    /** Blocking; stream-only sources are materialized first. */
    suspend fun probe(context: Context, source: FileSource): Facts {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        val seekable = if (source is LocalFileSource || source is ContentUriSource) null else source.materialize()
        try {
            when {
                source is LocalFileSource -> { retriever.setDataSource(source.file.path); extractor.setDataSource(source.file.path) }
                source is ContentUriSource -> { retriever.setDataSource(context, source.uri); extractor.setDataSource(context, source.uri, null) }
                else -> {
                    val path = seekable!!.file!!.path
                    retriever.setDataSource(path); extractor.setDataSource(path)
                }
            }
            val tags = buildList {
                fun tag(key: Int, label: String, format: (String) -> String = { it }) {
                    retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }?.let { add(label to format(it)) }
                }
                tag(MediaMetadataRetriever.METADATA_KEY_TITLE, "Title")
                tag(MediaMetadataRetriever.METADATA_KEY_ARTIST, "Artist")
                tag(MediaMetadataRetriever.METADATA_KEY_ALBUM, "Album")
                tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, "Album artist")
                tag(MediaMetadataRetriever.METADATA_KEY_YEAR, "Year")
                tag(MediaMetadataRetriever.METADATA_KEY_GENRE, "Genre")
                tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER, "Track")
                tag(MediaMetadataRetriever.METADATA_KEY_DURATION, "Duration") { formatDuration(it.toLongOrNull() ?: 0) }
                tag(MediaMetadataRetriever.METADATA_KEY_BITRATE, "Bitrate") { "${(it.toLongOrNull() ?: 0) / 1000} kbps" }
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (w != null && h != null) add("Video size" to "$w × $h")
                tag(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION, "Rotation") { "$it°" }
                tag(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE, "Frame rate") { "$it fps" }
                tag(MediaMetadataRetriever.METADATA_KEY_LOCATION, "Location")
                tag(MediaMetadataRetriever.METADATA_KEY_MIMETYPE, "Container")
            }
            val tracks = (0 until extractor.trackCount).map { i -> describe(extractor.getTrackFormat(i)) }
            return Facts(tags, tracks)
        } finally {
            runCatching { retriever.release() }
            extractor.release()
            seekable?.close()
        }
    }

    private fun describe(f: MediaFormat): String {
        val mime = f.getString(MediaFormat.KEY_MIME) ?: "unknown"
        val parts = mutableListOf(mime)
        if (f.containsKey(MediaFormat.KEY_WIDTH) && f.containsKey(MediaFormat.KEY_HEIGHT)) {
            parts += "${f.getInteger(MediaFormat.KEY_WIDTH)} × ${f.getInteger(MediaFormat.KEY_HEIGHT)}"
        }
        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) parts += "${f.getInteger(MediaFormat.KEY_SAMPLE_RATE)} Hz"
        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            parts += when (val c = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) { 1 -> "mono"; 2 -> "stereo"; else -> "$c channels" }
        }
        if (f.containsKey(MediaFormat.KEY_LANGUAGE)) f.getString(MediaFormat.KEY_LANGUAGE)?.takeIf { it != "und" }?.let { parts += it }
        return parts.joinToString(", ")
    }

    fun formatDuration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}
