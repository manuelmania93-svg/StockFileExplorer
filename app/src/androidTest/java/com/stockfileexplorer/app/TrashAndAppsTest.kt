package com.stockfileexplorer.app

import android.content.Intent
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.Trash
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Papierkorb "leeren" empties only the app's own trash payloads (synthetic fixture
 * only - no real user files), and the Apps shortcut lists the installed apps.
 */
@RunWith(AndroidJUnit4::class)
class TrashAndAppsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val pkg = context.packageName
    private val german get() = context.resources.configuration.locales[0].language == "de"
    private fun label(en: String, de: String) = if (german) de else en
    private val appName = "File Explorer CL"
    private val capture = InstrumentationRegistry.getArguments().getString("capture", "trash")

    private fun waitText(value: String, timeout: Long = 15000) = device.wait(Until.findObject(By.text(value)), timeout)
        ?: throw AssertionError("Missing UI text: $value")
    private fun waitRes(value: String, timeout: Long = 15000) = device.wait(Until.findObject(By.res(value)), timeout)
        ?: throw AssertionError("Missing UI res: $value")
    private fun screenshot(name: String) {
        device.takeScreenshot(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "sfe-$capture-$name.png"))
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
    private fun prepare() {
        finishAll()
        device.executeShellCommand("appops set --uid $pkg MANAGE_EXTERNAL_STORAGE allow")
        assertTrue(Environment.isExternalStorageManager())
    }
    private fun start(configure: Intent.() -> Unit = {}) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.configure()
        context.startActivity(intent)
    }

    @Test
    fun papierkorbEmptyDeletesOnlyTrashPayloads() {
        prepare()
        // Synthetic fixture in Download, moved into the Papierkorb through the app's
        // own trash mechanism. Nothing outside the bin may be touched by emptying.
        val download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val fixture = File(download, "sfe-trash-fixture.txt")
        fixture.writeText("trash fixture")
        assertTrue("fixture moved into Papierkorb", Trash.moveToTrash(fixture))
        assertFalse("fixture left Download", fixture.exists())
        val before = Trash.list()
        assertTrue("fixture listed in Papierkorb", before.any { it.name == "sfe-trash-fixture.txt" })

        start { putExtra("papierkorb", true) }
        waitText(label("Papierkorb", "Papierkorb"))
        waitText("sfe-trash-fixture.txt")
        screenshot("1-trash-with-fixture")

        device.findObject(By.text(label("Empty Papierkorb", "Papierkorb leeren"))).click()
        waitText(label("Empty the Papierkorb?", "Papierkorb leeren?"))
        screenshot("2-empty-confirm")
        device.findObject(By.res("dialog_confirm")).click()

        waitText(label("The Papierkorb is empty.", "Der Papierkorb ist leer."))
        assertTrue("Papierkorb has no entries left", Trash.list().isEmpty())
        assertFalse(
            "fixture payload deleted from the bin",
            Trash.root().walkTopDown().any { it.name == "sfe-trash-fixture.txt" }
        )
        screenshot("3-trash-emptied")

        // Back leaves the Papierkorb to the dashboard instead of exiting.
        device.pressBack()
        waitText(appName)
        if (!device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 5000)) {
            fail("App left the foreground after Back from Papierkorb")
        }
    }

    @Test
    fun installedAppsListShowsInstalledApps() {
        prepare()
        start()
        waitText(appName)

        waitText(label("Apps", "Apps")).click()
        // Rows carry "<package> • <size>"; their first appearance measures the
        // async package-query + icon-decode load time.
        val clickAt = android.os.SystemClock.elapsedRealtime()
        if (!device.wait(Until.hasObject(By.textContains(" • ")), 60000)) {
            screenshot("4-installed-apps-missing")
            throw AssertionError("installed-apps list did not render any rows")
        }
        android.util.Log.i("SfeTest", "Apps list first rows after " + (android.os.SystemClock.elapsedRealtime() - clickAt) + " ms")
        // Code-grounded behavior (InstalledAppsScreen): the whole package list is
        // loaded atomically on Dispatchers.IO - every label AND icon resolved up
        // front - and exposed as ONE sorted list (sortedBy label.lowercase()).
        // There is no per-row lazy loading and no reordering; packages without a
        // label show their package name as title (normal loadLabel behavior), so
        // the own row sorts mid-list under "file explorer control".
        // UiDevice.swipe throttles to 5 ms/step: few-step swipes are flings that
        // overshoot whole screens (run 41028986158 ended at the list bottom),
        // while 40 x 250px under-reached the mid-list row (run 41031503561).
        // Use 100-step drags (real slow drags, ~500 px each) with a bounded
        // settle wait per position, covering 80 x 500 px >> full list length.
        // Match the package subtitle ("<package> • <size>") with the bullet
        // suffix so the ".test" package row cannot false-match. A missing row
        // can never pass: every position check must find it exactly once.
        val ownRow = By.textContains("$pkg • ")
        var found = device.wait(Until.hasObject(ownRow), 1500)
        var drags = 0
        while (!found && drags < 80) {
            device.swipe(540, 1500, 540, 1000, 100)
            drags++
            found = device.wait(Until.hasObject(ownRow), 800)
        }
        if (!found) {
            screenshot("4-installed-apps-missing")
            throw AssertionError("Own app row missing from installed-apps list")
        }
        screenshot("4-installed-apps")

        // Back returns to the dashboard.
        device.pressBack()
        waitText(label("Main storage", "Hauptspeicher"))
        if (!device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 5000)) {
            fail("App left the foreground after Back from Apps")
        }
    }
}
