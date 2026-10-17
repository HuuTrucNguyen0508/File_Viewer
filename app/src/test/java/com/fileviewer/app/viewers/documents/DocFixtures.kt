package com.fileviewer.app.viewers.documents

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.ByteArrayOutputStream

/** Builds small document packages in memory. */
object DocFixtures {
    const val NS_W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
    const val NS_R = "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
    const val NS_A = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
    const val NS_P = "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\""
    const val NS_S = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
    const val ODF_NS = "xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" " +
        "xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" " +
        "xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" " +
        "xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\" " +
        "xmlns:fo=\"urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0\" " +
        "xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\" " +
        "xmlns:presentation=\"urn:oasis:names:tc:opendocument:xmlns:presentation:1.0\" " +
        "xmlns:xlink=\"http://www.w3.org/1999/xlink\""

    fun zip(parts: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipArchiveOutputStream(bytes).use { z ->
            for ((name, body) in parts) {
                z.putArchiveEntry(ZipArchiveEntry(name))
                z.write(body)
                z.closeArchiveEntry()
            }
        }
        return bytes.toByteArray()
    }

    fun pkg(vararg parts: Pair<String, String>): DocPackage = pkgOf(parts.associate { (k, v) -> k to v.toByteArray() })

    fun pkgOf(parts: Map<String, ByteArray>): DocPackage = DocPackage.open(SeekableInMemoryByteChannel(zip(parts)))

    fun rels(vararg rels: Triple<String, String, String>): String =
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            rels.joinToString("") { (id, type, target) ->
                val external = if (target.startsWith("http")) " TargetMode=\"External\"" else ""
                "<Relationship Id=\"$id\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/$type\" Target=\"$target\"$external/>"
            } + "</Relationships>"

    fun rootRels(main: String) = rels(Triple("rId1", "officeDocument", main))
}
