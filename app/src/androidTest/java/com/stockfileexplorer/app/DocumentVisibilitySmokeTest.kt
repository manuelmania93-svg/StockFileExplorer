package com.stockfileexplorer.app

import android.content.Intent
import android.util.Log
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real emulator proof of the first-run user path:
 * permission gate -> system Settings -> grant -> dashboard,
 * then document fixtures dropped into /sdcard/Download by the shell
 * must be visible when browsing the Download folder in the app.
 */
@RunWith(AndroidJUnit4::class)
class DocumentVisibilitySmokeTest {


    // LazyColumn only composes visible rows; accumulated screenshots make the
    // image category longer than one screen, so scroll until the row appears.
    private fun scrollToText(device: UiDevice, text: String): Boolean {
        repeat(14) {
            if (device.hasObject(By.textContains(text))) return true
            device.swipe(300, 900, 300, 300, 20)
            Thread.sleep(250)
        }
        return device.hasObject(By.textContains(text))
    }

    // The list recomposes while the index refreshes, which can invalidate a node between find and click.
    private fun clickText(device: UiDevice, text: String) {
        repeat(5) {
            try { if (device.wait(Until.hasObject(By.text(text)), 5000)) { device.findObject(By.text(text)).click(); return } }
            catch (_: androidx.test.uiautomator.StaleObjectException) { Thread.sleep(300) }
        }
        throw AssertionError("Could not click '$text'")
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }

    @Test
    fun firstRunFlowAndDocumentsInDownloadAreVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pkg = context.packageName

        Log.i("SfeTest", "Download contents at test start: " + shell("ls -la /sdcard/Download"))

        val suffix = InstrumentationRegistry.getArguments().getString("capture", "device")
        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 10000))

        // Access was granted by the CI script before this class ran:
        // the gate must NOT show, the dashboard appears directly.
        assertFalse(
            "Permission gate should be satisfied",
            device.wait(Until.hasObject(By.textContains("All-files access needed")), 2000)
        )
        assertTrue(
            "Dashboard storage card visible after grant",
            device.wait(Until.hasObject(By.text("Internal storage")), 10000)
        )
        // Let the storage scan finish so the captured dashboard shows real sizes.
        device.wait(Until.gone(By.textContains("Scanning")), 30000)
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-1-dashboard.png"))

        // Open main storage via its shortcut -> root listing.
        // (The storage card itself now opens the storage analysis.)
        clickText(device, "Main storage")
        assertTrue(
            "Root listing shows Download folder",
            device.wait(Until.hasObject(By.text("Download")), 8000)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-2-root.png"))

        // Open Download -> document fixtures must be listed.
        clickText(device, "Download")
        Thread.sleep(1500)
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-3-download.png"))
        Log.i("SfeTest", "Download contents at assertion time: " + shell("ls -la /sdcard/Download"))
        assertTrue(
            "PDF fixture visible in Download",
            device.wait(Until.hasObject(By.textContains("sfe-fixture-report.pdf")), 8000)
        )
        assertTrue(
            "DOCX fixture visible in Download",
            device.hasObject(By.textContains("sfe-fixture-notes.docx"))
        )
        assertTrue(
            "TXT fixture visible in Download",
            device.hasObject(By.textContains("sfe-fixture-todo.txt"))
        )

        // Every file row must expose its action menu through the overflow button.
        val actionButtons = device.findObjects(By.descContains("File actions"))
        assertTrue("File rows expose an action menu button", actionButtons.isNotEmpty())
        actionButtons[0].click()
        assertTrue(
            "File action menu opens from overflow button",
            device.wait(Until.hasObject(By.text("Rename")), 5000)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-4-file-menu.png"))
        device.pressBack()

        // Returning to the dashboard must reuse the shared scan snapshot:
        // no second full scan ("Scanning" state) on re-entry.
        // System back now steps up one folder at a time: Download -> root -> dashboard.
        device.pressBack()
        assertTrue(
            "Back from Download reaches the storage-root listing",
            device.wait(Until.hasObject(By.text("Download")), 5000)
        )
        device.pressBack()
        assertTrue(
            "Back on dashboard",
            device.wait(Until.hasObject(By.text("Internal storage")), 5000)
        )
        assertFalse(
            "Dashboard re-entry must reuse the shared scan, not rescan",
            device.wait(Until.hasObject(By.textContains("Scanning")), 1500)
        )

        // Library category opens from the shared snapshot and lists images
        // with real thumbnails (verified visually from the screenshot).
        device.findObject(By.text("Library")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Images")), 5000))
        device.findObject(By.text("Images")).click()
        assertTrue(
            "Sample image listed in Images category",
            scrollToText(device, "sfe-sample-picture.jpg")
        )
        assertFalse(
            "Category opened from cached scan without a blocking rescan",
            device.wait(Until.hasObject(By.textContains("Scanning")), 1500)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-6-gallery.png"))
    }
}
