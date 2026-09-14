package com.github.xckevin927.android.battery.widget

import com.github.xckevin927.android.battery.widget.model.BatteryReading
import org.junit.Assert.*
import org.junit.Test

class BatteryReadingTest {
    private val t = 1_000_000L
    @Test fun failedCheckDoesNotRefreshLastSuccessOrFakeZero() {
        val old = BatteryReading(42, t, t)
        val failed = old.copy(attemptedAt = t + 60_000, failure = "timeout")
        assertEquals(t, failed.successAt)
        assertEquals("cached", failed.statusAt(t + 60_000))
        assertEquals(42, failed.displayedLevel(t + 60_000))
        assertEquals(-1, BatteryReading(failure = "timeout").displayedLevel(t))
    }
    @Test fun staleReadingEventuallyBecomesUnknown() {
        val value = BatteryReading(99, t, t)
        assertEquals("stale", value.statusAt(t + BatteryReading.STALE_AFTER_MS + 1))
        assertEquals(-1, value.displayedLevel(t + BatteryReading.EXPIRE_AFTER_MS + 1))
    }
    @Test fun unsupportedAndClockRollbackNeverLookFresh() {
        assertEquals("unsupported", BatteryReading(failure = "unsupported").statusAt(t))
        assertEquals("unknown", BatteryReading(88, t, t).statusAt(t - 1))
    }
    @Test fun validZeroRemainsARealReading() {
        assertEquals("available", BatteryReading(0, t, t).statusAt(t))
        assertEquals(0, BatteryReading(0, t, t).displayedLevel(t))
    }
}
