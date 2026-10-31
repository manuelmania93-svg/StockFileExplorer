package com.stockfileexplorer.app.data

import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipFile

/** Never use UnRAR-derived code to develop a RAR-compatible compressor. Extraction only. */
object Archives {
    const val MAX_ENTRIES = 10000
    const val MAX_BYTES = 10L * 1000 * 1000 * 1000
    data class Entry(val name: String, val size: Long, val directory: Boolean)
    private fun rar(file: File) = Archive(file, ArchiveOptions.builder()
        .maxDictionarySize(64L * 1024 * 1024).build())

    fun safeTarget(root: File, name: String): File {
        val path = name.replace('\\', '/')
        require(path.isNotBlank() && !path.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(path)
            && path.split('/').none { it == ".." } && '\u0000' !in path) { "Unsafe archive path" }
        val target = File(root, path).canonicalFile
        require(target.path.startsWith(root.canonicalPath + File.separator)) { "Unsafe archive path" }
        return target
    }

    fun list(file: File): List<Entry> {
        val result = mutableListOf<Entry>()
        if (file.extension.equals("zip", true)) {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    require(result.size < MAX_ENTRIES) { "Archive exceeds 10,000 entries" }
                    safeTarget(File(file.parentFile, "preview"), e.name)
                    result += Entry(e.name, e.size, e.isDirectory)
                }
            }
        } else rar(file).use { archive ->
            require(!archive.isPasswordProtected) { "Password-protected RAR is not supported in this build" }
            for (e in archive.fileHeaders) {
                require(result.size < MAX_ENTRIES) { "Archive exceeds 10,000 entries" }
                require(!e.isSplitBefore && !e.isSplitAfter) { "Split RAR is not supported in this build" }
                require(e.redirection == null) { "Archive links are not supported" }
                safeTarget(File(file.parentFile, "preview"), e.fileName)
                result += Entry(e.fileName, e.fullUnpackSize, e.isDirectory)
            }
        }
        var total = 0L
        for (entry in result) {
            require(entry.size <= MAX_BYTES - total) { "Archive exceeds 10 GB extraction limit" }
            total += entry.size.coerceAtLeast(0)
        }
        return result
    }

    /** A fresh folder is allocated atomically. Existing files are never replaced. */
    fun extract(file: File, cancelled: () -> Unit = {}, progress: (String) -> Unit = {}, destination: File? = null): File {
        val parent = destination ?: file.parentFile
        require(parent?.isDirectory == true && parent.canWrite()) { "Destination folder is not writable" }
        list(file) // Validate the entire entry table before writing.
        val base = file.nameWithoutExtension.take(100).ifBlank { "Archive" }
        var n = 0
        var root: File
        do {
            root = File(parent, base + "_extracted" + if (n == 0) "" else " ($n)")
            n++
            require(n <= 100) { "Cannot create destination folder" }
        } while (!root.mkdir())
        var written = 0L
        fun output(name: String): OutputStream {
            cancelled()
            val target = safeTarget(root, name)
            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Cannot create folder" }
            check(target.createNewFile()) { "Duplicate archive entry: $name" }
            val delegate = target.outputStream()
            return object : OutputStream() {
                override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                override fun write(b: ByteArray, off: Int, len: Int) {
                    cancelled()
                    written += len
                    if (written > MAX_BYTES) throw IOException("10 GB extraction limit reached")
                    if (root.usableSpace < len + 1024 * 1024) throw IOException("Not enough free storage")
                    delegate.write(b, off, len)
                }
                override fun close() = delegate.close()
            }
        }
        try {
            if (file.extension.equals("zip", true)) ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    cancelled()
                    val e = entries.nextElement()
                    progress(e.name)
                    if (e.isDirectory) {
                        val dir = safeTarget(root, e.name)
                        check(dir.isDirectory || dir.mkdirs()) { "Cannot create folder" }
                    } else {
                        val crc = java.util.zip.CRC32()
                        output(e.name).use { out ->
                            zip.getInputStream(e).use { input ->
                                val buffer = ByteArray(32768)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    crc.update(buffer, 0, count)
                                    out.write(buffer, 0, count)
                                }
                            }
                        }
                        require(e.crc == crc.value) { "ZIP checksum mismatch" }
                    }
                }
            } else rar(file).use { archive ->
                for (e in archive.fileHeaders) {
                    cancelled()
                    require(!e.isEncrypted && !e.isSplitBefore && !e.isSplitAfter && e.redirection == null)
                    progress(e.fileName)
                    if (e.isDirectory) {
                        val dir = safeTarget(root, e.fileName)
                        check(dir.isDirectory || dir.mkdirs()) { "Cannot create folder" }
                    } else output(e.fileName).use { archive.extractFile(e, it) }
                }
            }
            return root
        } catch (e: Throwable) {
            root.deleteRecursively() // Only our newly allocated folder, never the input archive.
            throw e
        }
    }
}
