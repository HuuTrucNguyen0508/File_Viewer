package com.fileviewer.app.detect

/** Signature checks on the first [Limits.HEADER_BYTES] of a file. Pure; no I/O. */
object Magic {
    /** Label used for OLE2 compound files; the detector refines it by extension (DOC/XLS/PPT). */
    const val OLE2_LABEL = "OLE2 compound document"

    fun detect(h: ByteArray, len: Int): FileKind? {
        val m = Header(h, len)
        return when {
            m.ascii(0, "%PDF-") -> FileKind.Pdf
            m.bytes(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> image(ImageFormat.PNG)
            m.bytes(0, 0xFF, 0xD8, 0xFF) -> image(ImageFormat.JPEG)
            m.ascii(0, "GIF87a") || m.ascii(0, "GIF89a") -> image(ImageFormat.GIF)
            m.ascii(0, "RIFF") && m.ascii(8, "WEBP") -> image(ImageFormat.WEBP)
            m.ascii(0, "RIFF") && m.ascii(8, "WAVE") -> media(MediaFormat.WAV)
            m.ascii(0, "RIFF") && m.ascii(8, "AVI ") -> media(MediaFormat.AVI)
            m.ascii(4, "ftyp") -> isoBmff(m)
            m.bytes(0, 0x1A, 0x45, 0xDF, 0xA3) ->
                media(if (m.contains("webm", 64)) MediaFormat.WEBM else MediaFormat.MKV)
            m.ascii(0, "OggS") -> media(if (m.contains("OpusHead", 128)) MediaFormat.OPUS else MediaFormat.OGG)
            m.ascii(0, "fLaC") -> media(MediaFormat.FLAC)
            m.ascii(0, "ID3") -> media(MediaFormat.MP3)
            m.ascii(0, "#!AMR") -> media(MediaFormat.AMR)
            m.ascii(0, "FLV") && m.u8(3) == 1 -> media(MediaFormat.FLV)
            m.bytes(0, 0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11) -> media(MediaFormat.WMV)
            m.u8(0) == 0x47 && m.u8(188) == 0x47 && m.u8(376) == 0x47 -> media(MediaFormat.MPEG_TS)
            m.bytes(0, 0x00, 0x00, 0x01, 0xBA) -> media(MediaFormat.MPEG_PS)
            m.ascii(0, "MThd") -> FileKind.Binary("MIDI sequence")
            m.bytes(0, 0x50, 0x4B, 0x03, 0x04) || m.bytes(0, 0x50, 0x4B, 0x05, 0x06) ||
                m.bytes(0, 0x50, 0x4B, 0x07, 0x08) -> FileKind.Archive(ArchiveFormat.ZIP)
            m.bytes(0, 0x1F, 0x8B) -> FileKind.Archive(ArchiveFormat.GZIP)
            m.ascii(0, "BZh") && m.u8(3) in '1'.code..'9'.code -> FileKind.Archive(ArchiveFormat.BZIP2)
            m.bytes(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> FileKind.Archive(ArchiveFormat.XZ)
            m.bytes(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> FileKind.Archive(ArchiveFormat.SEVEN_Z)
            m.ascii(0, "Rar!\u001A\u0007") -> FileKind.Archive(ArchiveFormat.RAR)
            m.ascii(257, "ustar") -> FileKind.Archive(ArchiveFormat.TAR)
            m.ascii(0, "!<arch>\n") -> FileKind.Archive(ArchiveFormat.AR)
            m.bytes(0, 0x28, 0xB5, 0x2F, 0xFD) -> FileKind.Binary("Zstandard compressed data")
            m.ascii(0, "SQLite format 3\u0000") -> FileKind.Data(DataFormat.SQLITE)
            m.bytes(0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) -> FileKind.Binary(OLE2_LABEL)
            m.ascii(0, "{\\rtf") -> FileKind.Document(DocumentFormat.RTF)
            m.bytes(0, 0x7F, 0x45, 0x4C, 0x46) -> FileKind.Binary("ELF executable")
            m.ascii(0, "MZ") && m.u32le(60) in 64L..(1L shl 20) -> FileKind.Binary("Windows executable")
            m.bytes(0, 0xFE, 0xED, 0xFA, 0xCE) || m.bytes(0, 0xFE, 0xED, 0xFA, 0xCF) ||
                m.bytes(0, 0xCE, 0xFA, 0xED, 0xFE) || m.bytes(0, 0xCF, 0xFA, 0xED, 0xFE) ->
                FileKind.Binary("Mach-O executable")
            m.bytes(0, 0xCA, 0xFE, 0xBA, 0xBE) ->
                // Java class files carry a major version >= 45 where fat Mach-O stores a small arch count.
                if (m.u16be(6) >= 45) FileKind.Binary("Java class file") else FileKind.Binary("Mach-O universal binary")
            m.ascii(0, "dex\n") -> FileKind.Binary("Dalvik executable")
            m.bytes(0, 0x00, 0x61, 0x73, 0x6D) -> FileKind.Binary("WebAssembly module")
            (m.bytes(0, 0x00, 0x01, 0x00, 0x00) || m.ascii(0, "true")) && m.u16be(4) in 1..64 -> FileKind.Font(FontFormat.TTF)
            m.ascii(0, "OTTO") && m.u16be(4) in 1..64 -> FileKind.Font(FontFormat.OTF)
            m.ascii(0, "ttcf") -> FileKind.Font(FontFormat.TTC)
            m.ascii(0, "wOFF") -> FileKind.Font(FontFormat.WOFF)
            m.ascii(0, "wOF2") -> FileKind.Font(FontFormat.WOFF2)
            m.ascii(0, "8BPS") -> image(ImageFormat.PSD)
            m.ascii(0, "II*\u0000") || m.ascii(0, "MM\u0000*") -> image(ImageFormat.TIFF)
            m.ascii(0, "BM") && m.u32le(14) in BMP_HEADER_SIZES -> image(ImageFormat.BMP)
            m.bytes(0, 0x00, 0x00, 0x01, 0x00) && m.u16le(4) in 1..256 && m.u8(9) == 0 -> image(ImageFormat.ICO)
            mp3FrameSync(m) -> media(MediaFormat.MP3)
            m.u8(0) == 0xFF && (m.u8(1) and 0xF6) == 0xF0 -> media(MediaFormat.AAC)
            else -> null
        }
    }

    private val BMP_HEADER_SIZES = setOf(12L, 40L, 52L, 56L, 64L, 108L, 124L)

    private fun image(f: ImageFormat) = FileKind.Image(f)
    private fun media(f: MediaFormat) = FileKind.Media(f)

    /** MPEG audio frame: 11 sync bits, layer III, valid bitrate and sample-rate indexes. */
    private fun mp3FrameSync(m: Header): Boolean {
        if (m.u8(0) != 0xFF || (m.u8(1) and 0xE0) != 0xE0) return false
        val layer = (m.u8(1) shr 1) and 0x3
        val bitrate = m.u8(2) shr 4
        val rate = (m.u8(2) shr 2) and 0x3
        return layer == 1 && bitrate in 1..14 && rate != 3
    }

    /** ISO base media (MP4 family, HEIF, AVIF): major brand at 8, compatible brands after 16. */
    private fun isoBmff(m: Header): FileKind {
        val boxSize = m.u32be(0).toInt().coerceIn(16, minOf(m.len, 256))
        val brands = buildList {
            add(m.string(8, 4))
            var i = 16
            while (i + 4 <= boxSize) { add(m.string(i, 4)); i += 4 }
        }
        val major = brands.first()
        return when {
            major == "avif" || major == "avis" -> image(ImageFormat.AVIF)
            major in HEIF_BRANDS -> if ("avif" in brands) image(ImageFormat.AVIF) else image(ImageFormat.HEIF)
            major == "qt  " -> media(MediaFormat.MOV)
            major.startsWith("M4A") || major.startsWith("M4B") -> media(MediaFormat.M4A)
            major.startsWith("3g") -> media(MediaFormat.THREE_GP)
            else -> media(MediaFormat.MP4)
        }
    }

    private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1")
}

/** Bounds-checked reads over a header sample. */
internal class Header(private val b: ByteArray, val len: Int) {
    fun u8(i: Int): Int = if (i in 0 until len) b[i].toInt() and 0xFF else -1
    fun u16be(i: Int): Int = if (i + 1 < len) (u8(i) shl 8) or u8(i + 1) else -1
    fun u16le(i: Int): Int = if (i + 1 < len) (u8(i + 1) shl 8) or u8(i) else -1
    fun u32be(i: Int): Long = if (i + 3 < len) (u16be(i).toLong() shl 16) or u16be(i + 2).toLong() else -1
    fun u32le(i: Int): Long = if (i + 3 < len) (u16le(i + 2).toLong() shl 16) or u16le(i).toLong() else -1

    fun bytes(at: Int, vararg expected: Int): Boolean =
        at + expected.size <= len && expected.indices.all { u8(at + it) == expected[it] }

    fun ascii(at: Int, s: String): Boolean =
        at + s.length <= len && s.indices.all { u8(at + it) == s[it].code }

    fun string(at: Int, n: Int): String =
        if (at + n > len) "" else String(CharArray(n) { (b[at + it].toInt() and 0xFF).toChar() })

    fun contains(s: String, within: Int): Boolean {
        val end = minOf(len, within) - s.length
        return (0..end).any { ascii(it, s) }
    }
}
