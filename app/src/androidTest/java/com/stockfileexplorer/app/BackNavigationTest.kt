package com.stockfileexplorer.app

import android.content.Intent
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * System-Back regression: Back walks back exactly one step - folder up, selection off,
 * category to list, destination back to the storage analysis - and only leaves the app at
 * the dashboard, never while a return route still exists.
 */
@RunWith(AndroidJUnit4::class)
class BackNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val pkg = context.packageName
    private val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeBack")
    private val sub = File(root, "sub")
    private val german get() = context.resources.configuration.locales[0].language == "de"
    private fun label(en: String, de: String) = if (german) de else en
    private val appName = "File Explorer CL"

    private fun waitText(value: String, timeout: Long = 15000) = device.wait(Until.findObject(By.text(value)), timeout)
        ?: throw AssertionError("Missing UI text: $value")
    private fun waitRes(value: String, timeout: Long = 15000) = device.wait(Until.findObject(By.res(value)), timeout)
        ?: throw AssertionError("Missing UI res: $value")
    private fun assertForeground() {
        if (!device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 5000)) {
            throw AssertionError("App left the foreground - Back closed it with a return route left")
        }
    }
    private fun screenshot(name: String) {
        // Subdirectory: top-level Download files name-sort before the sfe-fixture-*
        // fixtures and would push them off the first screen for later test classes
        // (CI pulls /sdcard/Download recursively, so artifacts are still uploaded).
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SfeBackShots")
        dir.mkdirs()
        device.takeScreenshot(File(dir, "sfe-back-${if (german) "de" else "en"}-$name.png"))
    }
    private fun finishAll() {
        val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
        instrumentation.runOnMainSync {
            for (stage in androidx.test.runner.lifecycle.Stage.values()) {
                if (stage == androidx.test.runner.lifecycle.Stage.DESTROYED || stage == androidx.test.runner.lifecycle.Stage.PRE_ON_CREATE) continue
                monitor.getActivitiesInStage(stage).forEach { it.finish() }
            }
        }
        device.waitForIdle(); Thread.sleep(1500)
    }
    private fun grantAndFixtures() {
        finishAll()
        device.executeShellCommand("appops set --uid $pkg MANAGE_EXTERNAL_STORAGE allow")
        assertTrue(Environment.isExternalStorageManager())
        root.deleteRecursively()
        sub.mkdirs()
        File(root, "top.txt").writeText("top fixture")
        File(sub, "inner.txt").writeText("inner fixture")
    }
    private fun start(configure: Intent.() -> Unit = {}) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.configure()
        context.startActivity(intent)
    }

    @Test
    fun folderBackStepsUpAndExitsAtEntryFolder() {
        grantAndFixtures()
        start { putExtra("browse_path", root.absolutePath) }
        waitText("top.txt")

        device.findObject(By.text("sub")).click() // navigate one level down
        waitText("inner.txt")
        assertForeground()

        device.pressBack() // one folder up, back at the folder the browser opened at
        waitText("top.txt")
        assertForeground()

        device.pressBack() // entry folder reached: browser closes to the dashboard
        waitText(appName)
        assertForeground()
        screenshot("stepped-up")
    }

    @Test
    fun selectionBackClearsSelectionBeforeNavigating() {
        grantAndFixtures()
        start { putExtra("browse_path", root.absolutePath) }
        waitText("top.txt")

        device.findObject(By.text("top.txt")).longClick()
        waitRes("selection_bar")

        device.pressBack() // first Back clears the selection and stays in the folder
        if (!device.wait(Until.gone(By.res("selection_bar")), 5000)) fail("selection bar still visible after Back")
        waitText("top.txt")
        assertForeground()

        device.pressBack() // second Back: entry folder reached, browser closes to the dashboard
        waitText(appName)
        assertForeground()
        screenshot("selection-then-up")
    }

    @Test
    fun libraryCategoryBackReturnsToCategoryList() {
        grantAndFixtures()
        start()
        waitText(appName)
        // Wait for the tab itself: a bare findObject right after the dashboard title can
        // beat the bottom bar composition (NPE flake at this line on API 35).
        waitText(label("Library", "Bibliothek")).click()
        waitText(label("Images", "Bilder"))

        waitText(label("Documents", "Dokumente")).click()
        waitRes("sort_button") // category file list open

        device.pressBack() // back to the category list, not out of the app
        if (!device.wait(Until.gone(By.res("sort_button")), 5000)) fail("category list not showing after Back")
        waitText(label("Images", "Bilder"))
        assertForeground()
        screenshot("library-back")
    }

    @Test
    fun analysisFolderBackReturnsToAnalysis() {
        grantAndFixtures()
        // Same state as "Open containing folder" from the storage analysis: the browser
        // sits on a folder with the analysis kept underneath.
        start {
            putExtra("browse_path", sub.absolutePath)
            putExtra("storage_analysis", true)
        }
        waitText("inner.txt")

        device.pressBack() // Back returns to the analysis, not up the folder tree
        waitText(label("Storage analysis", "Speicheranalyse"))
        assertForeground()
        screenshot("analysis-folder-back")
    }

    @Test
    fun analysisDestinationBackRestoresAnalysis() {
        grantAndFixtures()
        start { putExtra("storage_analysis", true) }
        waitText(label("Storage analysis", "Speicheranalyse"))

        // Wait for the report list, then scroll with CONTROLLED drags: UiScrollable
        // swipes fling this short list straight to its top/bottom (evidence: both
        // analysis-row-unstable screenshots show the list pinned at an extreme),
        // which is what kept losing the already-found row.
        val list = device.wait(Until.findObject(By.scrollable(true)), 90000)
        if (list == null) {
            screenshot("analysis-list-missing")
            throw AssertionError("analysis report list missing")
        }
        val found = try {
            list.scrollUntil(androidx.test.uiautomator.Direction.DOWN, Until.hasObject(By.res("analysis_category:DOCUMENTS")))
        } catch (_: Exception) { false }
        if (!found) {
            screenshot("analysis-row-missing")
            try {
                device.executeShellCommand("uiautomator dump /sdcard/Download/sfe-back-${if (german) "de" else "en"}-hierarchy.xml")
            } catch (_: Exception) { }
            throw AssertionError("Documents category row not found after scrolling")
        }
        // Prove the row is fully on screen, clear of the bottom bar, and settled
        // (two identical bounds reads 500ms apart, no fling), then tap exactly
        // once. A lost tap on a settled row is a product bug and must fail loud.
        val rb = run {
            var previous: android.graphics.Rect? = null
            val deadline = android.os.SystemClock.elapsedRealtime() + 30000
            var settled: android.graphics.Rect? = null
            while (android.os.SystemClock.elapsedRealtime() < deadline && settled == null) {
                device.waitForIdle()
                val b = try { device.findObject(By.res("analysis_category:DOCUMENTS"))?.visibleBounds } catch (_: androidx.test.uiautomator.StaleObjectException) { null }
                if (b == null || b.height() < 40) {
                    screenshot("analysis-row-unstable")
                    throw AssertionError("Documents row left the screen after being found")
                }
                if (b.centerY() > device.displayHeight * 3 / 4) {
                    try { list.scroll(androidx.test.uiautomator.Direction.DOWN, 0.2f) } catch (_: Exception) { }
                    previous = null
                    Thread.sleep(400)
                    continue
                }
                if (b == previous) settled = b else { previous = b; Thread.sleep(500) }
            }
            settled ?: run {
                screenshot("analysis-row-unstable")
                throw AssertionError("Documents row never settled to a stable on-screen position")
            }
        }
        device.click(rb.centerX(), rb.centerY())
        if (!device.wait(Until.hasObject(By.res("sort_button")), 8000)) {
            screenshot("analysis-tap-lost")
            fail("Tap on the settled Documents row did not open the category overlay")
        }

        device.pressBack() // returns to the analysis with its report kept
        if (!device.wait(Until.gone(By.res("sort_button")), 5000)) fail("analysis not showing after Back")
        waitText(label("Storage analysis", "Speicheranalyse"))
        assertTrue(
            "analysis report sections kept",
            UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(label("Categories", "Kategorien"))
        )
        assertForeground()

        device.pressBack() // and from the analysis itself, Back reaches the dashboard
        waitText(appName)
        assertForeground()
        screenshot("analysis-back")
    }
}
