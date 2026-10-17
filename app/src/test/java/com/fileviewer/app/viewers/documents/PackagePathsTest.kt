package com.fileviewer.app.viewers.documents

import com.fileviewer.app.source.LimitExceededException
import com.fileviewer.app.source.Limits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PackagePathsTest {

    @Test fun resolvesRelativeAndRootedReferences() {
        assertEquals("word/media/a.png", PackagePaths.resolve("word", "media/a.png"))
        assertEquals("ppt/media/a.png", PackagePaths.resolve("ppt/slides", "../media/a.png"))
        assertEquals("xl/sheet.xml", PackagePaths.resolve("anything", "/xl/sheet.xml"))
        assertEquals("OEBPS/a b.png", PackagePaths.resolve("OEBPS/text", "./../a%20b.png#frag"))
    }

    @Test fun refusesTraversalSchemesAndBrokenEscapes() {
        assertNull(PackagePaths.resolve("word", "../../etc/passwd"))
        assertNull(PackagePaths.resolve("a", "..%2F..%2Fsecret"))
        assertNull(PackagePaths.resolve("", "http://example.com/x.png"))
        assertNull(PackagePaths.resolve("", "data:image/png;base64,AAAA"))
        assertNull(PackagePaths.resolve("", "file:///sdcard/x"))
        assertNull(PackagePaths.resolve("", "a%2"))
        assertNull(PackagePaths.resolve("", "#only-fragment"))
    }

    @Test fun urlsRoundTrip() {
        val path = "OEBPS/images/fig 1 é.png"
        val url = PackagePaths.url(path)
        assertEquals("pkg/OEBPS/images/fig%201%20%C3%A9.png", url)
        assertEquals(path, PackagePaths.entryOf(url))
        assertNull(PackagePaths.entryOf("fonts/plex.ttf"))
    }

    @Test fun imagesAreServedFromThePackageWithinTheirLimit() {
        DocFixtures.pkgOf(mapOf("m/a.png" to byteArrayOf(1, 2, 3))).use { pkg ->
            assertArrayEquals(byteArrayOf(1, 2, 3), pkg.image("m/a.png"))
            assertNull(pkg.image("m/missing.png"))
        }
    }

    @Test fun totalUnpackedBytesAreBoundedAcrossParts() {
        val part = ByteArray((Limits.MAX_DOC_PART_BYTES - 1).toInt())
        val parts = (1..(Limits.MAX_DOC_TOTAL_BYTES / Limits.MAX_DOC_PART_BYTES + 1)).associate { "p$it" to part }
        DocFixtures.pkgOf(parts).use { pkg ->
            assertThrows(LimitExceededException::class.java) { parts.keys.forEach { name -> pkg.read(name) { it.readBytes() } } }
            pkg.resetBudget()
            pkg.read("p1") { it.readBytes() }
        }
    }
}
