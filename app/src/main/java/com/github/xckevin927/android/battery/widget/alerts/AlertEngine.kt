package com.github.xckevin927.android.battery.widget.alerts

/** Settings consumed by the pure alert state machine. All alert types are off by default. */
data class AlertSettings(
    val phoneEnabled: Boolean = false,
    val phoneThreshold: Int = DEFAULT_PHONE_THRESHOLD,
    val bluetoothEnabled: Boolean = false,
    val bluetoothThreshold: Int = DEFAULT_BLUETOOTH_THRESHOLD,
    val selectedBluetoothDeviceIds: Set<String> = emptySet(),
    val quietHours: QuietHours = QuietHours()
) {
    companion object {
        const val DEFAULT_PHONE_THRESHOLD = 80
        const val DEFAULT_BLUETOOTH_THRESHOLD = 20
        const val MAX_BLUETOOTH_THRESHOLD = 95
    }
}

data class QuietHours(
    val enabled: Boolean = false,
    val startMinuteOfDay: Int = 22 * 60,
    val endMinuteOfDay: Int = 7 * 60
) {
    fun contains(minuteOfDay: Int): Boolean {
        if (!enabled) return false
        val minute = minuteOfDay.floorMod(MINUTES_PER_DAY)
        val start = startMinuteOfDay.floorMod(MINUTES_PER_DAY)
        val end = endMinuteOfDay.floorMod(MINUTES_PER_DAY)
        return when {
            start == end -> true
            start < end -> minute in start until end
            else -> minute >= start || minute < end
        }
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60
    }
}

data class PhoneAlertInput(
    val level: Int,
    val isCharging: Boolean,
    val isPlugged: Boolean,
    val checkedAtMillis: Long
)

data class BluetoothAlertInput(
    val deviceId: String,
    val displayName: String,
    val level: Int,
    val isConnected: Boolean,
    val lastCheckedAtMillis: Long,
    val lastSuccessfulReadAtMillis: Long,
    val source: String,
    val status: String
)

/**
 * Persist this state after every evaluation. A device is armed unless its id is in
 * [disarmedBluetoothDeviceIds], which keeps the on-disk representation compact.
 */
data class AlertEngineState(
    val phoneCycleActive: Boolean = false,
    val phoneNotifiedThisCycle: Boolean = false,
    val disarmedBluetoothDeviceIds: Set<String> = emptySet()
)

sealed class BatteryAlert {
    abstract val deliveryKey: String

    data class PhoneChargeReached(val level: Int, val threshold: Int) : BatteryAlert() {
        override val deliveryKey: String = PHONE_DELIVERY_KEY
    }

    data class BluetoothLow(
        val deviceId: String,
        val displayName: String,
        val level: Int,
        val threshold: Int
    ) : BatteryAlert() {
        override val deliveryKey: String = "$BLUETOOTH_DELIVERY_PREFIX$deviceId"
    }

    companion object {
        const val PHONE_DELIVERY_KEY = "phone"
        const val BLUETOOTH_DELIVERY_PREFIX = "bluetooth:"
    }
}

data class AlertEvaluation(
    /** State changes that are safe to persist before a notification is delivered. */
    val state: AlertEngineState,
    /** Proposed notifications. Call [AlertEngine.acknowledgeDelivered] after posting. */
    val alerts: List<BatteryAlert>
)

/** Pure, deterministic alert logic. Android storage and notification code live outside this object. */
object AlertEngine {
    const val READING_MAX_AGE_MILLIS = 2 * 60 * 1000L
    const val REARM_HYSTERESIS_PERCENT = 5
    private const val FUTURE_CLOCK_TOLERANCE_MILLIS = 5_000L

    fun evaluate(
        settings: AlertSettings,
        previousState: AlertEngineState,
        phone: PhoneAlertInput?,
        bluetoothDevices: List<BluetoothAlertInput>,
        nowMillis: Long,
        nowMinuteOfDay: Int
    ): AlertEvaluation {
        val alerts = mutableListOf<BatteryAlert>()
        val quiet = settings.quietHours.contains(nowMinuteOfDay)
        val selectedIds = settings.selectedBluetoothDeviceIds

        var phoneCycleActive = previousState.phoneCycleActive
        var phoneNotified = previousState.phoneNotifiedThisCycle
        if (phone != null && isFresh(phone.checkedAtMillis, nowMillis)) {
            if (!phone.isPlugged) {
                phoneCycleActive = false
                phoneNotified = false
            } else {
                if (!phoneCycleActive) {
                    phoneCycleActive = true
                    phoneNotified = false
                }
                val threshold = settings.phoneThreshold.coerceIn(1, 100)
                if (settings.phoneEnabled && phone.level in threshold..100 && !phoneNotified && !quiet) {
                    alerts += BatteryAlert.PhoneChargeReached(phone.level, threshold)
                }
            }
        }

        val disarmed = previousState.disarmedBluetoothDeviceIds
            .filterTo(mutableSetOf()) { it in selectedIds }
        val threshold = settings.bluetoothThreshold.coerceIn(
            1,
            AlertSettings.MAX_BLUETOOTH_THRESHOLD
        )
        val rearmAt = threshold + REARM_HYSTERESIS_PERCENT
        bluetoothDevices
            .asSequence()
            .filter { it.deviceId in selectedIds }
            .distinctBy { it.deviceId }
            .filter { isUsableBluetoothReading(it, nowMillis) }
            .forEach { device ->
                if (device.level >= rearmAt) {
                    disarmed.remove(device.deviceId)
                }
                if (
                    settings.bluetoothEnabled &&
                    device.level <= threshold &&
                    device.deviceId !in disarmed &&
                    !quiet
                ) {
                    alerts += BatteryAlert.BluetoothLow(
                        deviceId = device.deviceId,
                        displayName = device.displayName,
                        level = device.level,
                        threshold = threshold
                    )
                }
            }

        return AlertEvaluation(
            state = AlertEngineState(
                phoneCycleActive = phoneCycleActive,
                phoneNotifiedThisCycle = phoneNotified,
                disarmedBluetoothDeviceIds = disarmed
            ),
            alerts = alerts
        )
    }

    /**
     * Marks only notifications that the platform accepted. Permission failures therefore leave an
     * alert pending for the next fresh evaluation after the user restores notification access.
     */
    fun acknowledgeDelivered(
        state: AlertEngineState,
        alerts: List<BatteryAlert>,
        deliveredKeys: Set<String>
    ): AlertEngineState {
        var phoneNotified = state.phoneNotifiedThisCycle
        val disarmed = state.disarmedBluetoothDeviceIds.toMutableSet()
        alerts.forEach { alert ->
            if (alert.deliveryKey !in deliveredKeys) return@forEach
            when (alert) {
                is BatteryAlert.PhoneChargeReached -> phoneNotified = true
                is BatteryAlert.BluetoothLow -> disarmed += alert.deviceId
            }
        }
        return state.copy(
            phoneNotifiedThisCycle = phoneNotified,
            disarmedBluetoothDeviceIds = disarmed
        )
    }

    fun isUsableBluetoothReading(device: BluetoothAlertInput, nowMillis: Long): Boolean {
        if (!device.isConnected || device.status != STATUS_AVAILABLE || device.level !in 0..100) {
            return false
        }
        if (!isFresh(device.lastCheckedAtMillis, nowMillis)) return false
        return (device.source == SOURCE_GATT || device.source == "framework") &&
            isFresh(device.lastSuccessfulReadAtMillis, nowMillis)
    }

    private fun isFresh(timestampMillis: Long, nowMillis: Long): Boolean =
        timestampMillis > 0 &&
            timestampMillis >= nowMillis - READING_MAX_AGE_MILLIS &&
            timestampMillis <= nowMillis + FUTURE_CLOCK_TOLERANCE_MILLIS

    const val SOURCE_GATT = "gatt"
    const val STATUS_AVAILABLE = "available"
}
