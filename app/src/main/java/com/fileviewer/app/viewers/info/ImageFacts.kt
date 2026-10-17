package com.fileviewer.app.viewers.info

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.fileviewer.app.source.FileSource

/** Pixel size and EXIF fields worth showing. Blocking. */
object ImageFacts {
    fun read(source: FileSource): List<Pair<String, String>> = buildList {
        runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            source.openStream().use { BitmapFactory.decodeStream(it, null, opts) }
            if (opts.outWidth > 0) add("Pixels" to "${opts.outWidth} × ${opts.outHeight}")
            opts.outMimeType?.let { add("Decoder type" to it) }
        }
        runCatching {
            val exif = source.openStream().use { ExifInterface(it) }
            fun tag(t: String, label: String, format: (String) -> String = { it }) {
                exif.getAttribute(t)?.takeIf { it.isNotBlank() }?.let { add(label to format(it)) }
            }
            tag(ExifInterface.TAG_MAKE, "Camera make")
            tag(ExifInterface.TAG_MODEL, "Camera model")
            tag(ExifInterface.TAG_LENS_MODEL, "Lens")
            tag(ExifInterface.TAG_DATETIME_ORIGINAL, "Taken")
            tag(ExifInterface.TAG_EXPOSURE_TIME, "Exposure") { v -> v.toDoubleOrNull()?.let { if (it < 1) "1/${Math.round(1 / it)} s" else "$it s" } ?: v }
            tag(ExifInterface.TAG_F_NUMBER, "Aperture") { "f/$it" }
            tag(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "ISO")
            tag(ExifInterface.TAG_FOCAL_LENGTH, "Focal length") { v -> rational(v)?.let { "%.1f mm".format(it) } ?: v }
            tag(ExifInterface.TAG_SOFTWARE, "Software")
            tag(ExifInterface.TAG_ARTIST, "Artist")
            tag(ExifInterface.TAG_COPYRIGHT, "Copyright")
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            if (orientation > 1) add("Orientation" to "Rotated ${exif.rotationDegrees}°" + if (exif.isFlipped) ", flipped" else "")
            exif.latLong?.let { (lat, lon) -> add("GPS" to "%.6f, %.6f".format(lat, lon)) }
        }
    }

    private fun rational(v: String): Double? {
        val (n, d) = v.split('/').takeIf { it.size == 2 } ?: return v.toDoubleOrNull()
        return n.toDoubleOrNull()?.let { num -> d.toDoubleOrNull()?.takeIf { it != 0.0 }?.let { num / it } }
    }
}
