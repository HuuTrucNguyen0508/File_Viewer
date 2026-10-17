package com.fileviewer.app.viewers.hex

import android.util.LruCache
import com.fileviewer.app.source.SeekableSource
import com.fileviewer.app.source.readFully

/** Keeps a few recently shown 64 KB pages; never the whole file. */
class HexPageCache(private val src: SeekableSource, val paging: HexPaging, maxPages: Int = 32) {
    private val pages = object : LruCache<Long, ByteArray>(maxPages) {}

    /** The page holding [page]'s bytes; reads it on a miss. Blocking. */
    fun page(page: Long): ByteArray = pages.get(page) ?: run {
        val buf = ByteArray(paging.pageLength(page))
        val n = src.readFully(paging.pageStart(page), buf)
        (if (n == buf.size) buf else buf.copyOf(n)).also { pages.put(page, it) }
    }

    fun cached(page: Long): ByteArray? = pages.get(page)
}
