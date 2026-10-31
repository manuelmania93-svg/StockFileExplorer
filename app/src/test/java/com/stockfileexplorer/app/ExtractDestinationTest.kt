package com.stockfileexplorer.app

import com.stockfileexplorer.app.data.Archives
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtractDestinationTest {
    private fun zip(dir: File): File {
        val f = File(dir, "sample.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("a/hello.txt")); z.write("hi".toByteArray()); z.closeEntry()
        }
        return f
    }

    @Test fun extractsBesideArchiveByDefault() {
        val dir = Files.createTempDirectory("src").toFile()
        val out = Archives.extract(zip(dir))
        assertEquals(dir.canonicalPath, out.parentFile!!.canonicalPath)
        assertEquals("sample_extracted", out.name)
        assertEquals("hi", File(out, "a/hello.txt").readText())
    }

    @Test fun extractsIntoChosenFolderInNamedSubfolder() {
        val dir = Files.createTempDirectory("src").toFile()
        val dest = Files.createTempDirectory("dest").toFile()
        val out = Archives.extract(zip(dir), destination = dest)
        assertEquals(dest.canonicalPath, out.parentFile!!.canonicalPath)
        assertEquals("sample_extracted", out.name)
        assertTrue(File(out, "a/hello.txt").isFile)
        assertFalse(File(dir, "sample_extracted").exists())
    }
}
