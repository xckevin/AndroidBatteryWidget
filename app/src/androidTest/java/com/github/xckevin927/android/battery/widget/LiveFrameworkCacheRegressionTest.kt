package com.github.xckevin927.android.battery.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.xckevin927.android.battery.widget.alerts.AlertEngine
import com.github.xckevin927.android.battery.widget.alerts.BluetoothAlertInput
import com.github.xckevin927.android.battery.widget.model.BatteryReading
import com.github.xckevin927.android.battery.widget.model.FrameworkBatteryReading
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

/**
 * Opt-in QA regression: ages only the in-memory timestamp of a real, unchanged system reading.
 * It never fabricates a battery level, changes the phone clock or edits receipts/preferences.
 * This accelerated boundary check is not a claim of a 31-minute real standby test.
 */
@RunWith(AndroidJUnit4::class)
class LiveFrameworkCacheRegressionTest {
    @Test fun currentSystemValueRecoversAnAgedReadingWithoutClaimingANewReport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveFrameworkCache") == "true")
        assumeTrue(instrumentation.targetContext.packageName ==
            "com.github.xckevin927.android.battery.widget.oppoqa")
        BatteryRepo.refresh("qa_framework_cache_baseline")
        val device = BatteryRepo.getBtSnapshot().firstOrNull {
            it.isConnected && it.source == "framework" && it.batteryLevel in 0..100
        }
        assumeTrue("Requires a real connected peripheral with system battery data", device != null)
        val actual = requireNotNull(device)
        val address = actual.bluetoothDevice.address
        val field = BatteryRepo::class.java.getDeclaredField("frameworkReadings").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val readings = field.get(BatteryRepo) as ConcurrentHashMap<String, FrameworkBatteryReading>
        val original = requireNotNull(readings[address])
        val oldTime = System.currentTimeMillis() - BatteryReading.EXPIRE_AFTER_MS - 60_000
        val aged = original.copy(successAt = oldTime, checkedAt = oldTime, validatedAt = oldTime)
        try {
            synchronized(BatteryRepo) { readings[address] = aged }
            assertEquals(-1, BatteryRepo.getBtSnapshot().first {
                it.bluetoothDevice.address == address
            }.batteryLevel)
            BatteryRepo.refresh("qa_framework_cache_revalidate")
            val refreshed = BatteryRepo.getBtSnapshot().first { it.bluetoothDevice.address == address }
            assertTrue(refreshed.isConnected)
            assertEquals(actual.batteryLevel, refreshed.batteryLevel)
            assertEquals("framework", refreshed.source)
            assertEquals("cached", refreshed.status)
            assertEquals(oldTime, refreshed.lastSuccessfulReadAt)
            assertTrue(refreshed.lastCheckedAt > oldTime)
            assertFalse(AlertEngine.isUsableBluetoothReading(BluetoothAlertInput(
                deviceId = "qa-device", displayName = "QA device", level = refreshed.batteryLevel,
                isConnected = refreshed.isConnected, lastCheckedAtMillis = refreshed.lastCheckedAt,
                lastSuccessfulReadAtMillis = refreshed.lastSuccessfulReadAt,
                source = refreshed.source, status = refreshed.status
            ), System.currentTimeMillis()))
        } finally {
            // Do not replace a genuinely newer platform report that arrived during this test.
            synchronized(BatteryRepo) {
                readings.computeIfPresent(address) { _, current ->
                    if (current.successAt == oldTime) original else current
                }
            }
            BatteryRepo.refresh("qa_framework_cache_restored")
        }
    }
}
