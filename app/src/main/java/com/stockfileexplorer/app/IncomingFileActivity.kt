package com.stockfileexplorer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stockfileexplorer.app.theme.AppTheme
import java.io.File

/**
 * Receives files other apps open with this app ("Open with"). A content:// Uri is copied once into
 * cacheDir/incoming and shown by the existing viewer; a file:// Uri to a readable file is shown in place.
 * Only the file types the in-app viewers support are accepted.
 */
class IncomingFileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (intent?.action != Intent.ACTION_VIEW || uri == null) return finish()
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.incoming_opening)) }
                }
            }
        }
        Thread {
            val result = try { resolve(uri) } catch (e: Exception) { null }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when {
                    result == null -> toast(R.string.incoming_failed)
                    ViewerActivity.kind(result) == null -> toast(R.string.incoming_unsupported)
                    else -> ViewerActivity.open(this, result)
                }
                finish()
            }
        }.start()
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()

    private fun resolve(uri: Uri): File? {
        if (uri.scheme == "file") {
            val f = File(uri.path ?: return null)
            return if (f.isFile && f.canRead()) f else null
        }
        if (uri.scheme != "content") return null
        val name = copyName(uri, contentResolver.getType(uri))
        // Archives are copied into Download instead of the cache: "Extract all" writes beside the archive,
        // and a result in the cache would be pruned. Other types are only viewed, so the cache is enough.
        if (ViewerActivity.kind(File(name)) == "archive") return copyToDownload(uri, name)
        val dir = File(cacheDir, "incoming")
        pruneOld(dir)
        dir.mkdirs()
        // A fresh numbered folder keeps two files with the same name apart.
        val target = File(File(dir, System.currentTimeMillis().toString()).apply { mkdirs() }, name)
        contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
        return target
    }

    private fun copyToDownload(uri: Uri, name: String): File? {
        val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        downloads.mkdirs()
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var target = File(downloads, name)
        var n = 1
        while (target.exists()) { target = File(downloads, "$stem ($n)" + if (ext.isEmpty()) "" else ".$ext"); n++ }
        contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
        return target
    }

    private fun pruneOld(dir: File) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    private fun copyName(uri: Uri, mime: String?): String {
        var display: String? = null
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) display = it.getString(0)
        }
        // Keep only the last path segment: a provider-supplied name must not point outside the folder.
        val base = (display ?: uri.lastPathSegment ?: "file").substringAfterLast('/').substringAfterLast('\\').ifBlank { "file" }
        return fixExtension(base, mime)
    }

    companion object {
        /** Adds the extension that matches [mime] when [name] has none the viewer recognizes. */
        fun fixExtension(name: String, mime: String?): String {
            if (ViewerActivity.kind(File(name)) != null) return name
            val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: return name
            return "$name.$ext"
        }
    }
}
