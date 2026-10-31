package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.data.Trash
import com.stockfileexplorer.app.data.VolumeStats
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Physical free space: moving to the Papierkorb must not count as freed, deleting from the bin must,
 * and the dashboard must show the new value without a restart.
 */
@RunWith(AndroidJUnit4::class)
class DiskSpaceRefreshTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val dir = File("/sdcard/Download/DiskFixture")
    private val out = File("/sdcard/Download/sfe-disk-refresh.txt")

    private fun launch(vararg extras: Pair<String, Any>) {
        val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        extras.forEach { (k, v) -> if (v is Boolean) i.putExtra(k, v) }
        context.startActivity(i)
    }
    private fun freeText(bytes: Long) = context.getString(R.string.free_space, FileLogic.formatSize(bytes))
    private fun waitText(text: String, ms: Long = 20000) {
        if (!device.wait(Until.hasObject(By.text(text)), ms)) {
            device.takeScreenshot(File("/sdcard/Download/sfe-disk-missing.png"))
            throw AssertionError("Expected '$text'. Visible: " + device.findObjects(By.pkg(context.packageName)).mapNotNull { it.text }.distinct().take(30))
        }
    }

    @Test
    fun permanentDeleteFromBinUpdatesFreeSpaceOnDashboard() {
        val fd = inst.uiAutomation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        assumeTrue("needs 2 GB free", VolumeStats.read().freeBytes > 2L * 1024 * 1024 * 1024)
        dir.deleteRecursively(); dir.mkdirs(); Trash.root().deleteRecursively()
        val size = 700L * 1024 * 1024
        val file = File(dir, "disk-fixture.bin")
        val chunk = ByteArray(1024 * 1024) { 7 }
        file.outputStream().use { o -> repeat((size / chunk.size).toInt()) { o.write(chunk) } }
        val freeWithFile = VolumeStats.read().freeBytes

        assertTrue(Trash.moveToTrash(file))
        val freeInBin = VolumeStats.read().freeBytes
        val moveDelta = kotlin.math.abs(freeInBin - freeWithFile)
        assertTrue("moving to the bin must not free space (changed by $moveDelta bytes)", moveDelta < 50L * 1024 * 1024)

        launch()
        waitText(freeText(VolumeStats.read().freeBytes))
        device.takeScreenshot(File("/sdcard/Download/sfe-disk-1-before.png"))

        launch("papierkorb" to true)
        waitText("disk-fixture.bin"); device.findObject(By.text("disk-fixture.bin")).click()
        waitText(context.getString(R.string.delete_permanently)); device.findObject(By.text(context.getString(R.string.delete_permanently))).click()
        waitText(context.getString(R.string.delete_permanently_body))
        device.findObjects(By.text(context.getString(R.string.delete_permanently))).last().click()
        waitText(context.getString(R.string.papierkorb_empty))
        device.takeScreenshot(File("/sdcard/Download/sfe-disk-2-bin-empty.png"))
        Thread.sleep(3000)
        val freeAfter = VolumeStats.read().freeBytes
        assertTrue("deleting from the bin must free the space (before ${freeInBin}, after $freeAfter)", freeAfter - freeInBin > size * 8 / 10)

        device.pressBack()
        waitText(freeText(freeAfter))
        device.takeScreenshot(File("/sdcard/Download/sfe-disk-3-after.png"))
        out.writeText("withFile=$freeWithFile inBin=$freeInBin afterDelete=$freeAfter movedDelta=$moveDelta freedByDelete=${freeAfter - freeInBin}\n")
        dir.deleteRecursively(); Trash.root().deleteRecursively()
    }
}
