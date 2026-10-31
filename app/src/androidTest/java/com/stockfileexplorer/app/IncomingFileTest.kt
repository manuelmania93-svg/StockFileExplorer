package com.stockfileexplorer.app

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Files opened from another app: the app is offered for the supported types and shows a content:// file. */
@RunWith(AndroidJUnit4::class)
class IncomingFileTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)

    private fun offered(mime: String): Boolean {
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse("content://example.test/f"), mime)
        return context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.packageName == context.packageName }
    }

    @Test
    fun appIsOfferedForSupportedTypes() {
        listOf("text/plain", "application/pdf", "image/jpeg", "video/mp4", "application/zip", "application/vnd.rar").forEach {
            assertTrue("Not offered for $it", offered(it))
        }
    }

    @Test
    fun contentUriTextFileOpensInViewer() {
        val dir = File(context.cacheDir, "incoming-test").apply { mkdirs() }
        val file = File(context.getExternalFilesDir(null), "titan-sample.txt").apply { writeText("incoming-text-marker\nsecond line") }
        // The FileProvider hands out content:// Uris for the app's own external files only if mapped; fall back to /sdcard.
        val shared = File("/sdcard/Download/sfe-incoming-sample.txt").apply { writeText("incoming-text-marker\nsecond line") }
        val uri = FileProvider.getUriForFile(context, "com.stockfileexplorer.app.fileprovider", shared)
        assertEquals("content", uri.scheme)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/plain")
            .setClass(context, IncomingFileActivity::class.java)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("Text viewer did not show the file", device.wait(Until.hasObject(By.textContains("incoming-text-marker")), 15000))
        device.takeScreenshot(File("/sdcard/Download/sfe-incoming-text.png"))
        file.delete(); dir.deleteRecursively(); shared.delete()
    }
}
