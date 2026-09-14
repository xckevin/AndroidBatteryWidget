package com.github.xckevin927.android.battery.widget

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.xckevin927.android.battery.widget.billing.ReceiptStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReceiptStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ReceiptStore(context)
    private val file = File(context.noBackupFilesDir, "battery_widget_pro.receipt")

    @After fun clear() = store.clear()

    @Test fun interruptedWritePreservesPreviousReceiptAndClearRemovesIt() {
        store.clear()
        val previous = ReceiptStore.Receipt("{\"purchaseState\":0}", "storage-test-only")
        assertTrue(store.write(previous))
        // Simulate process death before AtomicFile.finishWrite; no signed entitlement is forged.
        AtomicFile(file).startWrite().use { it.write(byteArrayOf(1, 2, 3)) }
        assertEquals(previous, ReceiptStore(context).read())
        assertTrue(file.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + "/"))
        store.clear()
        assertNull(store.read())
        assertFalse(file.exists())
    }

    @Test fun truncatedReceiptIsRejectedAndOversizedWriteDoesNotDestroyPreviousReceipt() {
        store.clear()
        file.writeBytes(byteArrayOf(0, 0, 0, 100, 1))
        assertNull(store.read())
        val previous = ReceiptStore.Receipt("{}", "storage-test-only")
        assertTrue(store.write(previous))
        assertFalse(store.write(ReceiptStore.Receipt("x".repeat(70_000), "signature")))
        assertEquals(previous, store.read())
    }
}
