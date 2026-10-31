package com.stockfileexplorer.app.data

import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Physical used/free space of the shared-storage volume, straight from the file system (StatFs).
 * This is separate from the indexed category totals: it needs no file scan, so it can be refreshed
 * right after a delete. Moving an item to the Papierkorb is a same-volume rename and frees nothing;
 * space comes back only when the item is deleted from the bin.
 */
object VolumeStats {
    data class Usage(val totalBytes: Long, val freeBytes: Long) {
        val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    }

    private val _usage = MutableStateFlow<Usage?>(null)
    val usage: StateFlow<Usage?> = _usage

    fun read(root: File = Environment.getExternalStorageDirectory()): Usage {
        val stat = StatFs(root.absolutePath)
        return Usage(stat.totalBytes, stat.availableBytes)
    }

    /** Re-reads the volume and publishes it. Cheap; call from any thread. */
    fun refresh(root: File = Environment.getExternalStorageDirectory()) {
        _usage.value = read(root)
    }

    /** Refreshes now and once more shortly after, because the file system can report freed space a moment late. */
    suspend fun refreshAfterDelete(root: File = Environment.getExternalStorageDirectory()) {
        withContext(Dispatchers.IO) { refresh(root) }
        delay(1500)
        withContext(Dispatchers.IO) { refresh(root) }
    }
}
