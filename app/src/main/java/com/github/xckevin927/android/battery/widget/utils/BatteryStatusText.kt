package com.github.xckevin927.android.battery.widget.utils

import android.content.Context
import android.os.BatteryManager
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState

object BatteryStatusText {
    @JvmStatic fun phone(context: Context, state: PhoneBatteryState): String {
        val level = if (state.level in 0..100) "${state.level}%" else context.getString(R.string.battery_unknown)
        return "$level · ${phoneStatus(context, state)}"
    }

    @JvmStatic fun phoneStatus(context: Context, state: PhoneBatteryState): String {
        val status = when {
            state.status == BatteryManager.BATTERY_STATUS_FULL -> R.string.battery_full
            state.isCharging -> R.string.battery_charging
            state.isPlugged -> R.string.battery_plugged_paused
            state.status == BatteryManager.BATTERY_STATUS_DISCHARGING -> R.string.battery_discharging
            else -> R.string.battery_unknown
        }
        return context.getString(status)
    }

    @JvmStatic fun bluetooth(context: Context, state: BtDeviceState): String {
        val status = when {
            !state.isConnected -> R.string.device_disconnected
            state.status == "permission_denied" -> R.string.device_permission_missing
            state.status == "unsupported" -> R.string.device_battery_unsupported
            state.status == "stale" -> R.string.device_stale
            state.status == "cached" -> R.string.device_cached
            state.batteryLevel !in 0..100 -> R.string.device_battery_unknown
            else -> R.string.device_connected
        }
        val label = context.getString(status)
        return if (state.isConnected && state.batteryLevel in 0..100) "${state.batteryLevel}% · $label" else label
    }

    @JvmStatic fun freshness(context: Context, state: BtDeviceState): String {
        if (!state.isConnected) return context.getString(R.string.device_offline_no_live_data)
        if (state.source == "framework") return context.getString(R.string.framework_freshness, age(context, state.lastSuccessfulReadAt), age(context, state.lastCheckedAt))
        if (state.lastSuccessfulReadAt > 0) return context.getString(R.string.gatt_freshness,
            age(context, state.lastSuccessfulReadAt), age(context, state.lastCheckedAt))
        return context.getString(when (state.failureReason) {
            "timeout" -> R.string.device_read_timeout
            "unsupported" -> R.string.device_battery_unsupported
            "read_failed", "invalid_value", "disconnected", "connection_failed", "permission_denied" -> R.string.device_read_failed
            else -> R.string.device_waiting_read
        })
    }

    @JvmStatic fun age(context: Context, timestamp: Long): String {
        val elapsed = System.currentTimeMillis() - timestamp
        if (timestamp <= 0 || elapsed < 0) return context.getString(R.string.check_never)
        if (elapsed < 60_000) return context.getString(R.string.check_just_now)
        return context.getString(R.string.check_minutes_ago, elapsed / 60_000)
    }
}
