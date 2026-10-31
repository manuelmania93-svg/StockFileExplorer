package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.Archives
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BuiltInViewersTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val root = File("/sdcard/Download/ViewerSamples")
    private fun fixtures() {
        val fd = inst.uiAutomation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        root.deleteRecursively()
        root.mkdirs()
        File(root,"rename-me.mp4").writeText("rename fixture")
        File(root,"occupied.txt").writeText("keep existing")
        inst.context.assets.list("viewers")!!.forEach { name ->
            inst.context.assets.open("viewers/$name").use { input -> File(root, name).outputStream().use { input.copyTo(it) } }
        }
    }
    private fun waitText(text: String) {
        if (device.wait(Until.hasObject(By.textContains(text)), 15000)) return
        // Evidence only: keep what was on screen when the expected text never appeared.
        runCatching {
            screenshot("failure-" + text.filter { it.isLetterOrDigit() })
            File("/sdcard/Download/sfe-viewers-failure-texts.txt").writeText(
                "expected=$text\nvisibleTexts=" + device.findObjects(By.pkg(device.currentPackageName ?: "")).mapNotNull { it.text }.take(60) + "\n" +
                "currentPackage=" + device.currentPackageName + "\n")
        }
        throw AssertionError("Expected '$text'")
    }
    // A recomposing list can invalidate a node between find and click: re-find and click again.
    private fun clickStable(text: String) {
        repeat(5) {
            try { if (device.wait(Until.hasObject(By.text(text)), 8000)) { device.findObject(By.text(text)).click(); return } }
            catch (_: androidx.test.uiautomator.StaleObjectException) { Thread.sleep(300) }
        }
        throw AssertionError("Could not click '$text'")
    }
    private fun screenshot(name: String) {
        val mode = InstrumentationRegistry.getArguments().getString("capture", "viewers")
        device.takeScreenshot(File("/sdcard/Download/sfe-$mode-$name.png"))
    }
    private fun mainAndDownload() {
        val fd = inst.uiAutomation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        device.pressHome()
        // Let the Home transition finish first; otherwise it can land after the launch below and bury it.
        device.waitForIdle()
        Thread.sleep(800)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        var shown = device.wait(Until.hasObject(By.text("Internal storage")), 15000)
        if (!shown) {
            // The launch can be swallowed while the previous test's activity is torn down: launch once more.
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            shown = device.wait(Until.hasObject(By.text("Internal storage")), 20000)
        }
        if (!shown) {
            screenshot("dashboard-missing")
            throw AssertionError("Dashboard absent: " + device.currentPackageName)
        }
        // The storage card opens the analysis now; the folder browser opens via its shortcut.
        clickStable("Main storage")
        waitText("Download");clickStable("Download")
        FileActionPreferences.saveBrowserSort(context, "NAME")
        // Other suites leave sibling folders in Download, so ViewerSamples can sit below the fold.
        run {
            val end = android.os.SystemClock.uptimeMillis() + 30000
            while (!device.hasObject(By.text("ViewerSamples")) && android.os.SystemClock.uptimeMillis() < end) {
                // Down-then-up scan: a long fling can carry the row past the screen, so come back too.
                repeat(6) { if (!device.hasObject(By.text("ViewerSamples"))) device.swipe(300, 900, 300, 300, 20) }
                repeat(8) { if (!device.hasObject(By.text("ViewerSamples"))) device.swipe(300, 300, 300, 900, 20) }
            }
        }
        waitText("ViewerSamples");clickStable("ViewerSamples")
    }
    private fun open(name: String) {
        // The index keeps refreshing while the test runs and can reset the list's scroll
        // position mid-swipe, so poll with a time budget instead of fixed swipe counts.
        // Poll with the proven down-then-up scan inside a time budget; the index refresh
        // can reset the list's scroll position mid-scan.
        val deadline = android.os.SystemClock.uptimeMillis() + 60000
        while (!device.hasObject(By.text(name)) && android.os.SystemClock.uptimeMillis() < deadline) {
            repeat(10) { if (!device.hasObject(By.text(name))) device.swipe(300, 900, 300, 300, 20) }
            repeat(12) { if (!device.hasObject(By.text(name))) device.swipe(300, 300, 300, 900, 20) }
        }
        waitText(name)
        // A click can land while the index refresh recomposes the list and get
        // swallowed (seen on the Library > Images route). Click and wait for the
        // viewer window; retry once on a fresh node when nothing opened.
        if (device.findObject(By.text(name))?.clickAndWait(Until.newWindow(), 10000) != true) {
            waitText(name)
            device.findObject(By.text(name))?.clickAndWait(Until.newWindow(), 10000)
        }
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 5000))
    }
    @Test fun openRenderPlayAndExtractThroughFolderUi() {
        fixtures();mainAndDownload()
        open("sample-picture.jpg");waitText("Rotate");assertTrue(device.wait(Until.hasObject(By.desc("Picture preview")),15000))
        screenshot("picture")
        device.findObject(By.desc("Picture preview")).pinchOpen(0.5f)
        screenshot("picture-zoomed")
        waitText("Rotate");device.findObject(By.text("Rotate")).click();screenshot("picture-rotated")
        device.pressBack()
        open("sample-reader.pdf");waitText("1 / 2");assertTrue(device.wait(Until.hasObject(By.desc("PDF page 1")),15000));screenshot("pdf-page1")
        device.findObject(By.text("Next")).click();waitText("2 / 2");assertTrue(device.wait(Until.hasObject(By.desc("PDF page 2")),15000));screenshot("pdf-page2");device.findObject(By.desc("PDF page 2")).pinchOpen(0.5f);screenshot("pdf-page2-zoomed")
        device.pressBack()
        open("sample-video.mp4");waitText("Playing");Thread.sleep(800);screenshot("video-playing")
        waitText("Pause");device.findObject(By.text("Pause")).click();waitText("Paused");screenshot("video-paused")
        // Seek through the visible Compose slider, not a player test hook.
        val width = device.displayWidth
        device.click(width * 3 / 4, 170 * device.displayHeight / 640)
        waitText("Play");device.findObject(By.text("Play")).click();waitText("Playing");screenshot("video-resumed")
        device.pressBack()
        mainAndDownload()
        for (name in listOf("sample-bundle.zip", "sample-rar4.rar", "sample-rar5.rar")) {
            open(name);assertTrue(device.wait(Until.hasObject(By.text(java.util.regex.Pattern.compile("[0-9]+ entries"))),15000));screenshot(name.substringBeforeLast('.') + "-contents")
            device.findObject(By.text("Extract here")).click();waitText("Extracted to")
            screenshot(name.substringBeforeLast('.') + "-extracted")
            val output = root.listFiles()!!.filter { it.isDirectory && it.name.startsWith(name.substringBeforeLast('.')+"_extracted") }.maxBy { it.lastModified() }
            if (name.endsWith(".zip")) assertEquals("ZIP extraction works.\n", File(output,"notes/hello.txt").readText())
            else {
                assertEquals("file1\r\n", File(output,"FILE1.TXT").readText())
                assertEquals("file2\r\n", File(output,"FILE2.TXT").readText())
            }
            device.pressBack()
        }
    }
    @Test fun libraryAndRecentOpenInternallyAndErrorsStayInApp() {
        fixtures()
        mainAndDownload()
        open("broken-picture.jpg");waitText("Cannot open this file");screenshot("broken-picture-error");device.pressBack()
        open("broken-video.mp4");waitText("Cannot open this file");screenshot("broken-video-error");device.pressBack()
        open("sample-picture.png");waitText("Rotate");assertTrue(device.wait(Until.hasObject(By.desc("Picture preview")),15000));screenshot("png-picture");device.pressBack()
        open("broken.pdf");waitText("Cannot open this file");screenshot("broken-pdf-error");device.pressBack()
        open("unsafe.zip");waitText("Cannot open this file");screenshot("unsafe-zip-error");device.pressBack()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        // Recent files is a Library tile now.
        waitText("Library")
        device.findObject(By.text("Library")).click()
        waitText("Recent files")
        device.findObject(By.text("Recent files")).click()
        open("sample-picture.jpg");waitText("Rotate");screenshot("recent-picture-route");device.pressBack()
        // Category route through the Library tab grid.
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        waitText("Library")
        device.findObject(By.text("Library")).click()
        waitText("Images")
        device.findObject(By.text("Images")).click()
        open("sample-picture.jpg");waitText("Rotate");screenshot("library-picture-route");device.pressBack()
    }
    @Test fun wholeFilenameIncludingExtensionRename() {
        fixtures();mainAndDownload()
        openInList("rename-me.mp4").longClick()
        // Long-press selects the file directly (CX behavior); single-file rename lives
        // in the floating quick-actions card on the right. Match it by resource tags (the
        // language-proof form SelectionBarTest already proves) rather than English text.
        val bar = device.wait(Until.findObject(By.res("selection_bar")), 8000)
        if (bar == null) {
            runCatching {
                File("/sdcard/Download/sfe-viewers-failure-selectionbar.txt").writeText(
                    "visibleTexts=" + device.findObjects(By.pkg(device.currentPackageName ?: "")).mapNotNull { it.text }.take(60) + "\n")
            }
        }
        assertNotNull("Selection bar after long press", bar)
        assertNotNull(
            "Quick actions popup after long press",
            device.wait(Until.findObject(By.res("selection_quick_popup")), 5000)
        )
        val renameAction = device.wait(Until.findObject(By.res("quick_rename")), 5000)
        assertNotNull("Rename action in quick actions popup", renameAction)
        renameAction.click()
        val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
        assertEquals("rename-me.mp4", input.text)
        screenshot("rename-whole-name")
        input.text = "renamed.txt"
        device.wait(Until.hasObject(By.text("Rename")), 5000)
        val button = device.findObjects(By.text("Rename")).maxBy { it.visibleBounds.centerY() }
        val bounds = button.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
        openInList("renamed.txt")
        waitText("renamed.txt")
        assertFalse(File(root,"rename-me.mp4").exists())
        assertEquals("rename fixture", File(root,"renamed.txt").readText())
        screenshot("renamed-extension-result")
        assertFalse(com.stockfileexplorer.app.data.Rename.move(File(root,"renamed.txt"),"occupied.txt"))
        assertEquals("keep existing",File(root,"occupied.txt").readText())
        assertFalse(com.stockfileexplorer.app.data.Rename.move(File(root,"renamed.txt"),"../escape.txt"))
    }
    private fun openInList(name: String): androidx.test.uiautomator.UiObject2 {
        val deadline = android.os.SystemClock.uptimeMillis() + 60000
        while (!device.hasObject(By.text(name)) && android.os.SystemClock.uptimeMillis() < deadline) {
            repeat(12) { if (!device.hasObject(By.text(name))) device.swipe(300,900,300,300,20) }
            repeat(12) { if (!device.hasObject(By.text(name))) device.swipe(300,300,300,900,20) }
        }
        return device.wait(Until.findObject(By.text(name)),5000) ?: throw AssertionError("Missing $name")
    }
    @Test fun archiveSafetyAndRepeatExtraction() {
        fixtures()
        val zip = File(root,"sample-bundle.zip")
        val first = Archives.extract(zip)
        File(first,"notes/hello.txt").writeText("KEEP")
        val second = Archives.extract(zip)
        assertNotEquals(first.canonicalPath, second.canonicalPath)
        assertEquals("KEEP", File(first,"notes/hello.txt").readText())
        assertEquals("ZIP extraction works.\n", File(second,"notes/hello.txt").readText())
        try { Archives.extract(File(root,"unsafe.zip"));fail("Unsafe path was accepted") } catch (_: Exception) {}
        assertFalse(File(root,"escaped.txt").exists())
        try { Archives.extract(File(root,"duplicate.zip"));fail("Duplicate path was accepted") } catch (_: Exception) {}
        assertFalse(File(root,"duplicate_extracted").exists())
        try { Archives.extract(File(root,"corrupt.zip"));fail("Corrupt ZIP was accepted") } catch (_: IllegalArgumentException) {}
        assertFalse(File(root,"corrupt_extracted").exists())
        var calls = 0
        try { Archives.extract(zip, { if (++calls > 2) throw java.util.concurrent.CancellationException() });fail("Cancellation ignored") }
        catch (_: java.util.concurrent.CancellationException) {}
        for (name in listOf("compressed-solid-rar4.rar", "compressed-solid-rar5.rar")) {
            val out = Archives.extract(File(root,name))
            assertEquals(9, out.listFiles()!!.size)
            for (i in 1..9) assertEquals("file$i\n", File(out,"file$i.txt").readText())
        }
        try { Archives.extract(File(root,"encrypted-rar5.rar"));fail("Encrypted RAR was accepted") } catch (_: Exception) {}
        for (path in listOf("/absolute", "C:/escape", "a/../../escape", "..\\escape")) {
            try { Archives.safeTarget(root,path);fail("Unsafe path $path accepted") } catch (_: IllegalArgumentException) {}
        }
    }
}
