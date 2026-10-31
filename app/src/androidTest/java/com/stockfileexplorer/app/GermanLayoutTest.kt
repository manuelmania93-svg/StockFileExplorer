package com.stockfileexplorer.app

import android.content.Intent
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
 * German-locale layout proof for the dashboard size summary, file action
 * menu and image category, captured on a narrow emulator display, plus one
 * enlarged-font dashboard shot. Requires the CI script to have switched the
 * emulator locale to de-AT and granted all-files access.
 */
@RunWith(AndroidJUnit4::class)
class GermanLayoutTest {


    // LazyColumn only composes visible rows; accumulated screenshots make the
    // image category longer than one screen, so scroll until the row appears.
    // Slow, short drags: a fast flick flings the list several pages and jumps
    // past the target rows before they are ever composed/checked (evidence:
    // sfe-german-download-missing.png showed the list at its end while the
    // fixture sits a few rows below the first screen).
    private fun slowScrollToText(device: UiDevice, text: String): Boolean {
        repeat(120) {
            if (device.hasObject(By.textContains(text))) return true
            device.swipe(540, 1400, 540, 1300, 100)
            Thread.sleep(300)
        }
        return device.hasObject(By.textContains(text))
    }

    private fun scrollToText(device: UiDevice, text: String): Boolean {
        repeat(40) {
            if (device.hasObject(By.textContains(text))) return true
            device.swipe(300, 900, 300, 300, 20)
            Thread.sleep(250)
        }
        return device.hasObject(By.textContains(text))
    }

    // Fast swipes first (a slow drag that starts on a row can register as a long press and select it,
    // which happened once Download held many more evidence screenshots), slow drags only as a fallback.
    private fun findFixture(device: UiDevice, text: String): Boolean =
        device.wait(Until.hasObject(By.textContains(text)), 8000) || scrollToText(device, text) || slowScrollToText(device, text)

    private fun clearSelection(device: UiDevice) {
        if (device.hasObject(By.res("selection_bar"))) { device.pressBack(); device.wait(Until.gone(By.res("selection_bar")), 3000) }
    }

    @Test
    fun germanDashboardMenuAndGalleryRender() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val suffix = InstrumentationRegistry.getArguments().getString("capture", "german")

        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val device = UiDevice.getInstance(instrumentation)
        // Evidence for locale/grant diagnosis before the first German assertion.
        android.util.Log.i("SfeTest", "Locale at German test start: " + java.util.Locale.getDefault())
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-0-preassert.png"))
        assertTrue(device.wait(Until.hasObject(By.text("Interner Speicher")), 10000))
        device.wait(Until.gone(By.textContains("Suche läuft")), 30000)
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-1-dashboard.png"))

        // Enlarged system font: layout must stay usable (labels may ellipsize,
        // sizes must stay visible).
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.3")
        Thread.sleep(2000)
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-1b-dashboard-largefont.png"))
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.0")
        Thread.sleep(2000)
        assertTrue(
            "Dashboard still visible after font scale restore",
            device.wait(Until.hasObject(By.text("Interner Speicher")), 5000)
        )

        // Folder browser in German: action menu opens from the overflow button.
        // (The "Interner Speicher" card now opens the storage analysis; the folder
        // browser opens via the "Hauptspeicher" shortcut.)
        device.findObject(By.text("Hauptspeicher")).click()
        assertTrue("Download folder row visible in the root listing",
            device.wait(Until.hasObject(By.text("Download")), 8000) || scrollToText(device, "Download").also { if (!it) device.takeScreenshot(File("/sdcard/Download/sfe-german-root-download-missing.png")) })
        device.findObject(By.text("Download")).click()
        // Name-sorted screenshots from earlier test classes (sfe-back-*) push the
        // fixtures off the first screen; scroll like the gallery part below does.
        // If still missing, capture evidence before failing.
        if (!findFixture(device, "sfe-fixture-report.pdf")) {
            device.takeScreenshot(File("/sdcard/Download/sfe-german-download-missing.png"))
            throw AssertionError("PDF fixture visible in Download")
        }
        val actionButtons = device.findObjects(By.descContains("Dateiaktionen"))
        assertTrue("File rows expose an action menu button", actionButtons.isNotEmpty())
        actionButtons[0].click()
        assertTrue(
            "File action menu opens in German",
            device.wait(Until.hasObject(By.text("Umbenennen")), 5000)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-2-file-menu.png"))
        device.pressBack()

        // Long-press floats the Rename/Share quick actions on the right side; the bottom
        // selection bar keeps Copy/Move/Delete only - no More step.
        clearSelection(device)
        val fixtureRow = device.findObject(By.textContains("sfe-fixture-report.pdf"))
            ?: throw AssertionError("PDF fixture row still visible for long press")
        fixtureRow.longClick()
        assertTrue(
            "Selection bar after long press",
            device.wait(Until.hasObject(By.res("selection_bar")), 5000)
        )
        assertTrue(
            "Floating quick actions after long press",
            device.wait(Until.hasObject(By.res("selection_quick_popup")), 5000)
        )
        assertTrue("Umbenennen visible", device.hasObject(By.text("Umbenennen")))
        assertTrue("Teilen visible", device.hasObject(By.text("Teilen")))
        assertFalse("No More step in the selection bar", device.hasObject(By.text("Mehr")))
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-4-quick-actions.png"))
        // Enlarged system font: both German action labels must stay fully visible.
        // The font change recreates MainActivity (fontScale is not in configChanges),
        // which clears the selection, so navigate back and select the fixture again.
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.3")
        Thread.sleep(2000)
        assertTrue(
            "Dashboard again after font-scale recreation",
            device.wait(Until.hasObject(By.text("Interner Speicher")), 10000)
        )
        device.findObject(By.text("Hauptspeicher")).click()
        assertTrue("Download folder row visible in the root listing",
            device.wait(Until.hasObject(By.text("Download")), 8000) || scrollToText(device, "Download").also { if (!it) device.takeScreenshot(File("/sdcard/Download/sfe-german-root-download-missing.png")) })
        device.findObject(By.text("Download")).click()
        if (!findFixture(device, "sfe-fixture-report.pdf")) {
            throw AssertionError("PDF fixture visible in Download at 1.3 font")
        }
        clearSelection(device)
        device.findObject(By.textContains("sfe-fixture-report.pdf")).longClick()
        assertTrue(
            "Floating quick actions at 1.3 font",
            device.wait(Until.hasObject(By.res("selection_quick_popup")), 5000)
        )
        assertTrue("Umbenennen visible at 1.3 font", device.hasObject(By.text("Umbenennen")))
        assertTrue("Teilen visible at 1.3 font", device.hasObject(By.text("Teilen")))
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-5-quick-actions-largefont.png"))
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.0")
        Thread.sleep(1500)
        // Restoring the font recreates the activity once more; the gallery section
        // below navigates from the dashboard, so just wait for it to settle.
        assertTrue(
            "Dashboard again after font restore",
            device.wait(Until.hasObject(By.text("Interner Speicher")), 10000)
        )

        // Image category lists the sample picture (thumbnail verified visually).
        device.findObject(By.text("Bibliothek")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Bilder")), 5000))
        device.findObject(By.text("Bilder")).click()
        assertTrue(
            "Sample image listed in Bilder category",
            scrollToText(device, "sfe-sample-picture.jpg")
        )
        assertFalse(
            "Category opened from cached scan without a blocking rescan",
            device.wait(Until.hasObject(By.textContains("Suche läuft")), 1500)
        )
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-3-gallery.png"))
    }
}
