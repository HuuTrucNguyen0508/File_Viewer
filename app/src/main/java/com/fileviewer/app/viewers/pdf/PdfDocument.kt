package com.fileviewer.app.viewers.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.fileviewer.app.source.Limits
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * PdfRenderer allows one open page at a time and is not thread-safe, so every call runs on
 * one serial dispatcher. Rendered bitmaps live in a byte-bounded LRU cache.
 */
class PdfDocument private constructor(private val renderer: PdfRenderer, private val fd: ParcelFileDescriptor) : Closeable {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serial: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Width / height of each page, in points. */
    val aspects: FloatArray = FloatArray(pageCount) { i ->
        renderer.openPage(i).use { it.width.toFloat() / it.height.coerceAtLeast(1) }
    }

    private val cache = object : LruCache<String, Bitmap>(Limits.MAX_PDF_CACHE_BYTES.toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(page: Int, widthPx: Int): Bitmap? = cache.get(key(page, widthPx))

    /** Renders [page] at [widthPx] wide, capped by [Limits.MAX_PDF_PAGE_PX] on the longest edge. */
    suspend fun render(page: Int, widthPx: Int): Bitmap? = withContext(serial) {
        if (closed) return@withContext null
        cache.get(key(page, widthPx))?.let { return@withContext it }
        val aspect = aspects[page]
        var w = widthPx.coerceAtLeast(1)
        var h = (w / aspect).toInt().coerceAtLeast(1)
        val longest = maxOf(w, h)
        if (longest > Limits.MAX_PDF_PAGE_PX) {
            val s = Limits.MAX_PDF_PAGE_PX.toFloat() / longest
            w = (w * s).toInt().coerceAtLeast(1); h = (h * s).toInt().coerceAtLeast(1)
        }
        val bitmap = createBitmap(w, h)
        bitmap.eraseColor(Color.WHITE)
        try {
            renderer.openPage(page).use { it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
        } catch (t: Throwable) { bitmap.recycle(); throw t }
        cache.put(key(page, widthPx), bitmap)
        bitmap
    }

    private fun key(page: Int, widthPx: Int) = "$page@$widthPx"

    override fun close() {
        // Runs after any in-flight render because it goes through the same serial dispatcher.
        CoroutineScope(serial).launch {
            closed = true
            cache.evictAll()
            renderer.close()
            fd.close()
        }
    }

    companion object {
        /** Throws SecurityException for password-protected files. Takes ownership of [fd]. */
        fun open(fd: ParcelFileDescriptor): PdfDocument {
            var renderer: PdfRenderer? = null
            try {
                renderer = PdfRenderer(fd)
                return PdfDocument(renderer, fd)
            } catch (t: Throwable) {
                renderer?.close()
                fd.close()
                throw t
            }
        }
    }
}
