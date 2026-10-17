package com.fileviewer.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.svg.SvgDecoder
import com.fileviewer.app.source.MaterializeCache
import com.fileviewer.app.viewers.code.TextMate
import java.io.File
import kotlin.concurrent.thread

class FileViewerApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        val root = File(cacheDir, "materialized")
        val session = File(root, java.util.UUID.randomUUID().toString())
        MaterializeCache.default = MaterializeCache(session)
        thread(name = "cache-clear") { root.listFiles()?.filter { it != session }?.forEach { it.deleteRecursively() } }
        TextMate.start(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                add(AnimatedImageDecoder.Factory())
            }
            .build()
}
