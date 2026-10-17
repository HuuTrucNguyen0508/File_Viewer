package com.fileviewer.app.viewers.data

import com.fileviewer.app.source.LocalFileSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SiblingFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun normalizeKeepsPathsInsideTheFolder() {
        assertEquals("a.png", SiblingFiles.normalize("a.png"))
        assertEquals("img/a.png", SiblingFiles.normalize("./img//a.png"))
        assertEquals("my pic.png", SiblingFiles.normalize("my%20pic.png"))
        assertEquals("a+b.png", SiblingFiles.normalize("a+b.png"))
        assertEquals("a.png", SiblingFiles.normalize("a.png?v=2#top"))
    }

    @Test fun normalizeRefusesEscapesAndForeignAddresses() {
        listOf("../secret.png", "img/../../x.png", "/etc/passwd", "%2e%2e/x.png", "..%2Fx.png", "C:/x.png", "a\\b.png", "http://evil/x.png", "")
            .forEach { assertNull(it, SiblingFiles.normalize(it)) }
    }

    @Test fun onlyPageResourceTypesAreServed() {
        val page = tmp.newFile("page.html")
        tmp.newFile("style.css").writeText("body{}")
        tmp.newFile("notes.txt").writeText("secret")
        tmp.newFile("run.js").writeText("alert(1)")
        assertNotNull(SiblingFiles.open(LocalFileSource(page), "style.css"))
        assertNull(SiblingFiles.open(LocalFileSource(page), "notes.txt"))
        assertNull(SiblingFiles.open(LocalFileSource(page), "run.js"))
        assertEquals("text/css", SiblingFiles.mimeOf("style.CSS"))
        assertNull(SiblingFiles.mimeOf("noext"))
    }

    @Test fun readsFromSubfoldersOfThePageFolderOnly() {
        val dir = tmp.newFolder("site")
        val page = File(dir, "index.md").apply { writeText("x") }
        File(dir, "img").mkdir()
        File(dir, "img/logo.png").writeBytes(byteArrayOf(1, 2, 3))
        tmp.newFile("outside.png").writeBytes(byteArrayOf(9))
        assertArrayEquals(byteArrayOf(1, 2, 3), SiblingFiles.open(LocalFileSource(page), "img/logo.png")!!.readBytes())
        assertNull(SiblingFiles.open(LocalFileSource(page), "../outside.png"))
        assertNull(SiblingFiles.open(LocalFileSource(page), "missing.png"))
    }

    @Test fun symlinkOutOfTheFolderIsRefused() {
        val dir = tmp.newFolder("site")
        val page = File(dir, "index.html").apply { writeText("x") }
        val outside = tmp.newFile("secret.png").apply { writeBytes(byteArrayOf(7)) }
        java.nio.file.Files.createSymbolicLink(File(dir, "link.png").toPath(), outside.toPath())
        assertNull(SiblingFiles.open(LocalFileSource(page), "link.png"))
    }
}
