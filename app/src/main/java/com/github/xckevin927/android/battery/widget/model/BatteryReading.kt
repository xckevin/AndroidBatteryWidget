package com.github.xckevin927.android.battery.widget.model

/** A successful read and the latest attempt are deliberately separate clocks. */
data class BatteryReading(
    val level: Int = -1,
    val successAt: Long = 0,
    val attemptedAt: Long = 0,
    val failure: String? = null
) {
    fun statusAt(now: Long): String {
        if (failure == "disconnected" || failure == "permission_denied") return "unknown"
        if (level !in 0..100 || successAt <= 0) return if (failure == "unsupported") "unsupported" else "unknown"
        val age = now - successAt
        if (age < 0 || age > EXPIRE_AFTER_MS) return "unknown"
        if (age > STALE_AFTER_MS) return "stale"
        return if (failure != null) "cached" else "available"
    }

    fun displayedLevel(now: Long): Int = if (statusAt(now) in setOf("unknown", "unsupported")) -1 else level

    companion object {
        const val STALE_AFTER_MS = 5 * 60_000L
        const val EXPIRE_AFTER_MS = 30 * 60_000L
    }
}
