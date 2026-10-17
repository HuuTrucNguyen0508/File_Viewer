package com.fileviewer.app.source

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class LocalFileSource(val file: File) : FileSource {
    override val key: String get() = "file:${file.absolutePath}:${file.length()}:${file.lastModified()}"
    override val name: String get() = file.name
    override val size: Long get() = file.length()
    override val mimeHint: String? get() = null
    override val lastModified: Long get() = file.lastModified()
    override val displayPath: String get() = file.absolutePath

    override fun openStream(): InputStream = FileInputStream(file)
    override fun seekableOrNull(): SeekableSource = FileSeekable(file)

    override fun equals(other: Any?) = other is LocalFileSource && other.file == file
    override fun hashCode() = file.hashCode()
}
