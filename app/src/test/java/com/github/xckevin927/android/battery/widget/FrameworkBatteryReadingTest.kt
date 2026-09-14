package com.github.xckevin927.android.battery.widget

import com.github.xckevin927.android.battery.widget.model.BatteryReading
import com.github.xckevin927.android.battery.widget.model.FrameworkBatteryReading
import com.github.xckevin927.android.battery.widget.alerts.AlertEngine
import com.github.xckevin927.android.battery.widget.alerts.BluetoothAlertInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameworkBatteryReadingTest {
    private val t = 1_000_000L

    @Test fun unchangedCacheCheckDoesNotRefreshSuccessTime() {
        val first = FrameworkBatteryReading().observe(67, t)
        val checkedAgain = first.observe(67, t + 60_000)

        assertEquals(t, checkedAgain.successAt)
        assertEquals(t + 60_000, checkedAgain.checkedAt)
        assertEquals("cached", checkedAgain.statusAt(t + BatteryReading.STALE_AFTER_MS + 1))
        assertEquals("stale", checkedAgain.statusAt(checkedAgain.validatedAt + BatteryReading.STALE_AFTER_MS + 1))
        assertEquals(67, checkedAgain.displayedLevel(t + BatteryReading.EXPIRE_AFTER_MS + 1))
        assertEquals(-1, checkedAgain.displayedLevel(checkedAgain.validatedAt + BatteryReading.EXPIRE_AFTER_MS + 1))
    }

    @Test fun unchangedValidChecksKeepLevelVisibleForHoursWithoutClaimingFreshReports() {
        var reading = FrameworkBatteryReading().observe(100, t)
        for (minute in 1..120) {
            val now = t + minute * 60_000L
            reading = reading.observe(100, now)
            assertEquals(100, reading.displayedLevel(now))
            assertEquals(t, reading.successAt)
            assertEquals(now, reading.validatedAt)
            assertEquals(if (minute <= 5) "available" else "cached", reading.statusAt(now))
        }
    }

    @Test fun failedChecksDoNotRenewTheLastValidValue() {
        val reading = FrameworkBatteryReading().observe(67, t)
        val failedSoon = reading.observe(-1, t + 60_000)
        assertEquals("cached", failedSoon.statusAt(t + 60_000))
        assertEquals(t, failedSoon.validatedAt)
        val failedLater = failedSoon.observe(-1, t + BatteryReading.EXPIRE_AFTER_MS + 1)
        assertEquals(-1, failedLater.displayedLevel(failedLater.checkedAt))
        assertEquals("unknown", failedLater.statusAt(failedLater.checkedAt))
    }

    @Test fun sameValidValueRecoversAnExpiredDisplayWithoutRefreshingReportTime() {
        val first = FrameworkBatteryReading().observe(67, t)
        val later = t + BatteryReading.EXPIRE_AFTER_MS + 1
        assertEquals(-1, first.displayedLevel(later))
        val recovered = first.observe(67, later)
        assertEquals(67, recovered.displayedLevel(later))
        assertEquals("cached", recovered.statusAt(later))
        assertEquals(t, recovered.successAt)
    }

    @Test fun noValidCheckOrClockRollbackDoesNotExposeABatteryLevel() {
        assertEquals(-1, FrameworkBatteryReading().observe(-1, t).displayedLevel(t))
        assertEquals(-1, FrameworkBatteryReading().observe(101, t).displayedLevel(t))
        assertEquals(-1, FrameworkBatteryReading().observe(67, t).displayedLevel(t - 1))
    }

    @Test fun repeatedValidCacheChecksDoNotEnableLowBatteryAlertsWithoutFreshEvidence() {
        val now = t + BatteryReading.EXPIRE_AFTER_MS + 1
        val cached = FrameworkBatteryReading().observe(15, t).observe(15, now)
        fun alertInput(reading: FrameworkBatteryReading) = BluetoothAlertInput(
            deviceId = "test-device", displayName = "Test device", level = reading.displayedLevel(now),
            isConnected = true, lastCheckedAtMillis = reading.checkedAt,
            lastSuccessfulReadAtMillis = reading.successAt, source = "framework", status = reading.statusAt(now)
        )
        assertEquals(15, cached.displayedLevel(now))
        assertFalse(AlertEngine.isUsableBluetoothReading(alertInput(cached), now))
        val reported = cached.observe(15, now, explicitlyReported = true)
        assertTrue(AlertEngine.isUsableBluetoothReading(alertInput(reported), now))
    }

    @Test fun changedValueAndExplicitReportAdvanceSuccessTime() {
        val first = FrameworkBatteryReading().observe(67, t)
        val changed = first.observe(66, t + 1_000)
        val sameValueReport = changed.observe(66, t + 2_000, explicitlyReported = true)

        assertEquals(t + 1_000, changed.successAt)
        assertEquals(t + 2_000, sameValueReport.successAt)
        assertEquals("available", sameValueReport.statusAt(t + 2_000))
    }
}
