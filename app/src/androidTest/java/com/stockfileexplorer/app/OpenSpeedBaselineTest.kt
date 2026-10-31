package com.stockfileexplorer.app

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Measurement only (no speed thresholds): time from launching a screen until its first content is visible.
 * Writes /sdcard/Download/sfe-open-speed.txt so runs can be compared before and after a change.
 * Emulator numbers say where time goes; they are not the numbers of a real phone.
 */
@RunWith(AndroidJUnit4::class)
class OpenSpeedBaselineTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val report = File("/sdcard/Download/sfe-open-speed.txt")

    private fun grant() {
        val fd = inst.uiAutomation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
    private fun launch(vararg extras: Pair<String, Any>) {
        val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        extras.forEach { (k, v) -> when (v) { is String -> i.putExtra(k, v); is Boolean -> i.putExtra(k, v) } }
        context.startActivity(i)
    }
    /** Median of [runs] launches, in ms, until [text] is on screen. */
    private fun timeTo(text: String, runs: Int, vararg extras: Pair<String, Any>): List<Long> {
        val times = (0 until runs).map {
            device.pressHome()
            val start = SystemClock.elapsedRealtime()
            launch(*extras)
            val found = device.wait(Until.hasObject(By.text(text)), 60000)
            val ms = if (found) SystemClock.elapsedRealtime() - start else -1L
            ms
        }
        return times
    }
    private fun log(line: String) { report.appendText(line + "\n") }

    @Test
    fun measureFolderAndAnalysisOpen() {
        grant()
        report.delete()
        val big = File("/sdcard/Download/SpeedBig").apply { deleteRecursively(); mkdirs() }
        repeat(3000) { File(big, "file-%04d.txt".format(it)).writeText("x") }
        val small = File("/sdcard/Download/SpeedSmall").apply { deleteRecursively(); mkdirs() }
        repeat(20) { File(small, "file-%04d.txt".format(it)).writeText("x") }
        // Warm start once so class loading and the first index build are not charged to run 1.
        timeTo("file-0000.txt", 1, "browse_path" to small.absolutePath)
        log("folder 20 files (ms per run): " + timeTo("file-0000.txt", 5, "browse_path" to small.absolutePath))
        log("folder 3000 files (ms per run): " + timeTo("file-0000.txt", 5, "browse_path" to big.absolutePath))
        log("storage analysis title (ms per run): " + timeTo(context.getString(R.string.storage_analysis), 5, "storage_analysis" to true))
        log("storage analysis first rows: ${firstAnalysisContent()}")
        log("Apps list first row (ms per run): " + timeToApps(5))
        big.deleteRecursively(); small.deleteRecursively()
        device.takeScreenshot(File("/sdcard/Download/sfe-open-speed-end.png"))
    }

    /** From launching the dashboard to the app's own row in the Apps list, which needs the whole list. */
    private fun timeToApps(runs: Int): List<Long> = (0 until runs).map {
        device.pressHome()
        launch()
        device.wait(Until.hasObject(By.text(context.getString(R.string.cat_apps))), 30000)
        val start = SystemClock.elapsedRealtime()
        var clicked = false
        repeat(5) { if (!clicked) try { device.findObject(By.text(context.getString(R.string.cat_apps)))?.click(); clicked = true } catch (_: androidx.test.uiautomator.StaleObjectException) { } }
        // The dashboard tile "Main storage" disappears once the Apps screen is up; the app's own label then
        // only matches its row in the list (the dashboard title carries the same label).
        device.wait(Until.gone(By.text(context.getString(R.string.main_storage))), 30000)
        val found = clicked && device.wait(Until.hasObject(By.text(context.getString(R.string.app_name))), 30000)
        if (found) SystemClock.elapsedRealtime() - start else -1L
    }

    private fun firstAnalysisContent(): String {
        device.pressHome()
        val start = SystemClock.elapsedRealtime()
        launch("storage_analysis" to true)
        device.wait(Until.hasObject(By.text(context.getString(R.string.storage_analysis))), 60000)
        val title = SystemClock.elapsedRealtime() - start
        // First stage text or first finished section, whichever is shown first.
        val stage = device.wait(Until.hasObject(By.textStartsWith("Scanning").pkg(context.packageName)), 2000)
        val done = device.wait(Until.gone(By.textContains("…")), 120000)
        val all = SystemClock.elapsedRealtime() - start
        return "title=${title}ms, progress text seen=$stage, all stages finished=$done after ${all}ms"
    }
}
