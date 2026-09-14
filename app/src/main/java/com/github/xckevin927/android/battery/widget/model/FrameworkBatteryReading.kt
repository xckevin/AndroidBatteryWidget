package com.github.xckevin927.android.battery.widget.model

/**
 * A framework battery lookup can return Android's cached value, so checking the cache and
 * receiving evidence of a new peripheral report use separate clocks. A valid lookup keeps
 * the displayed level usable without turning an unchanged cache value into a fresh report.
 */
data class FrameworkBatteryReading(
    val level: Int = -1,
    val successAt: Long = 0,
    val checkedAt: Long = 0,
    val validatedAt: Long = successAt
) {
    fun observe(observedLevel: Int, now: Long, explicitlyReported: Boolean = false): FrameworkBatteryReading {
        if (observedLevel !in 0..100) return copy(checkedAt = now)
        val hasNewEvidence = explicitlyReported || successAt <= 0 || observedLevel != level
        return copy(
            level = observedLevel,
            successAt = if (hasNewEvidence) now else successAt,
            checkedAt = now,
            validatedAt = now
        )
    }

    fun statusAt(now: Long): String {
        if (level !in 0..100 || successAt <= 0 || validatedAt <= 0) return "unknown"
        val validationAge = now - validatedAt
        val reportAge = now - successAt
        if (validationAge < 0 || reportAge < 0 || validationAge > BatteryReading.EXPIRE_AFTER_MS) return "unknown"
        if (validationAge > BatteryReading.STALE_AFTER_MS) return "stale"
        if (reportAge > BatteryReading.STALE_AFTER_MS || checkedAt > validatedAt) return "cached"
        return "available"
    }

    fun displayedLevel(now: Long): Int = if (statusAt(now) == "unknown") -1 else level
}
