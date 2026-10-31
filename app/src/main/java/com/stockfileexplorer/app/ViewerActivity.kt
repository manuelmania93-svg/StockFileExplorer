package com.stockfileexplorer.app

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.stockfileexplorer.app.theme.AppTheme
import com.stockfileexplorer.app.ui.ViewerScreen
import java.io.File

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra("path") ?: return finish()
        val file = File(path)
        val context = (intent.getStringArrayListExtra("paths") ?: arrayListOf()).map { File(it) }
        val files = swipeSet(file, context)
        setContent { AppTheme { Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) { ViewerScreen(files, files.indexOf(file).coerceAtLeast(0), onBack = { finish() }) } } }
    }
    companion object {
        fun kind(file: File): String? = when (file.extension.lowercase()) {
            "zip", "rar" -> "archive"
            "pdf" -> "pdf"
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif" -> "image"
            "txt", "log", "md", "json", "xml", "csv", "ini", "conf", "cfg", "py", "js", "yml", "yaml", "properties" -> "text"
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "ts" -> "video"
            else -> null
        }
        /** Files you can swipe through next to [file]: pictures and videos of the list it was opened from
         *  (or of its own folder when no list is known). Other kinds open alone. */
        fun swipeSet(file: File, context: List<File>): List<File> {
            val k = kind(file)
            if (k != "image" && k != "video") return listOf(file)
            val source = if (context.any { it.absolutePath == file.absolutePath }) context
                else (file.parentFile?.listFiles()?.sortedBy { it.name.lowercase() } ?: listOf(file))
            val media = source.filter { it.isFile && kind(it).let { x -> x == "image" || x == "video" } }
            return if (media.any { it.absolutePath == file.absolutePath }) media else listOf(file)
        }
        fun open(context: Context, file: File, siblings: List<File> = emptyList()): Boolean {
            if (kind(file) == null) return false
            val set = swipeSet(file, siblings)
            val at = set.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0)
            // Keep the intent small: at most 1000 paths around the opened file.
            val from = (at - 500).coerceAtLeast(0)
            val window = set.subList(from, minOf(set.size, from + 1000))
            context.startActivity(Intent(context, ViewerActivity::class.java)
                .putExtra("path", file.absolutePath)
                .putStringArrayListExtra("paths", ArrayList(window.map { it.absolutePath })))
            return true
        }
    }
}
