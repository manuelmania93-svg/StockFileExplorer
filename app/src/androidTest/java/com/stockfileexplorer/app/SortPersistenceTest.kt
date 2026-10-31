package com.stockfileexplorer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Storage-level check: a saved sort order is read back by a fresh read, as after an app restart. */
@RunWith(AndroidJUnit4::class)
class SortPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun browserSortIsKept() {
        FileActionPreferences.saveBrowserSort(context, "SIZE")
        assertEquals("SIZE", FileActionPreferences.browserSort(context))
        FileActionPreferences.saveBrowserSort(context, "NAME")
    }

    @Test
    fun categorySortIsKeptPerCategory() {
        FileActionPreferences.saveCategorySort(context, "VIDEOS", "SIZE", false)
        assertEquals("SIZE:false", FileActionPreferences.categorySort(context, "VIDEOS"))
        assertNull(FileActionPreferences.categorySort(context, "NO_SUCH_CATEGORY"))
    }
}
