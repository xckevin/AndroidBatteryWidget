package com.github.xckevin927.android.battery.widget.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {
    @Test
    fun phoneThresholdFiresOncePerChargingCycle() {
        val settings = AlertSettings(phoneEnabled = true, phoneThreshold = 80)
        var state = AlertEngineState()

        var result = evaluate(settings, state, phone(level = 79, plugged = true))
        assertTrue(result.alerts.isEmpty())
        state = result.state

        result = evaluate(settings, state, phone(level = 80, plugged = true))
        assertEquals(1, result.alerts.size)
        state = acknowledgeAll(result)

        result = evaluate(settings, state, phone(level = 96, plugged = true))
        assertTrue(result.alerts.isEmpty())
        state = result.state

        result = evaluate(settings, state, phone(level = 70, plugged = false))
        assertFalse(result.state.phoneCycleActive)
        state = result.state

        result = evaluate(settings, state, phone(level = 82, plugged = true))
        assertEquals(1, result.alerts.size)
    }

    @Test
    fun customAndFullPhoneThresholdsUseTheConfiguredLevel() {
        val custom = evaluate(
            AlertSettings(phoneEnabled = true, phoneThreshold = 73),
            AlertEngineState(),
            phone(level = 73, plugged = true)
        )
        assertEquals(73, (custom.alerts.single() as BatteryAlert.PhoneChargeReached).threshold)

        val full = evaluate(
            AlertSettings(phoneEnabled = true, phoneThreshold = 100),
            AlertEngineState(),
            phone(level = 99, plugged = true)
        )
        assertTrue(full.alerts.isEmpty())
        assertEquals(
            1,
            evaluate(
                AlertSettings(phoneEnabled = true, phoneThreshold = 100),
                full.state,
                phone(level = 100, plugged = true)
            ).alerts.size
        )
    }

    @Test
    fun bluetoothNeedsHysteresisBeforeItRearms() {
        val settings = bluetoothSettings(threshold = 20)
        var result = evaluate(settings, bluetooth = listOf(bluetooth(level = 20)))
        assertEquals(1, result.alerts.size)
        var state = acknowledgeAll(result)

        result = evaluate(settings, state, bluetooth = listOf(bluetooth(level = 20)))
        assertTrue(result.alerts.isEmpty())
        state = result.state

        result = evaluate(settings, state, bluetooth = listOf(bluetooth(level = 24)))
        assertTrue(result.alerts.isEmpty())
        assertTrue(DEVICE_ID in result.state.disarmedBluetoothDeviceIds)
        state = result.state

        result = evaluate(settings, state, bluetooth = listOf(bluetooth(level = 25)))
        assertTrue(result.alerts.isEmpty())
        assertFalse(DEVICE_ID in result.state.disarmedBluetoothDeviceIds)

        result = evaluate(settings, result.state, bluetooth = listOf(bluetooth(level = 19)))
        assertEquals(1, result.alerts.size)
    }

    @Test
    fun bluetoothThresholdIsCappedSoAFullReadingCanRearm() {
        val settings = bluetoothSettings(threshold = 100)
        val first = evaluate(settings, bluetooth = listOf(bluetooth(level = 95)))
        assertEquals(1, first.alerts.size)
        val disarmed = acknowledgeAll(first)

        val full = evaluate(settings, disarmed, bluetooth = listOf(bluetooth(level = 100)))
        assertTrue(full.alerts.isEmpty())
        assertFalse(DEVICE_ID in full.state.disarmedBluetoothDeviceIds)

        val lowAgain = evaluate(settings, full.state, bluetooth = listOf(bluetooth(level = 95)))
        assertEquals(1, lowAgain.alerts.size)
        assertEquals(
            AlertSettings.MAX_BLUETOOTH_THRESHOLD,
            (lowAgain.alerts.single() as BatteryAlert.BluetoothLow).threshold
        )
    }

    @Test
    fun quietHoursSupportOvernightWindowsAndKeepAlertPending() {
        val quietHours = QuietHours(enabled = true, startMinuteOfDay = 22 * 60, endMinuteOfDay = 7 * 60)
        assertTrue(quietHours.contains(23 * 60))
        assertTrue(quietHours.contains(6 * 60 + 59))
        assertFalse(quietHours.contains(7 * 60))
        assertFalse(quietHours.contains(12 * 60))

        val settings = bluetoothSettings().copy(quietHours = quietHours)
        val quietResult = evaluate(
            settings,
            bluetooth = listOf(bluetooth(level = 15)),
            minuteOfDay = 23 * 60
        )
        assertTrue(quietResult.alerts.isEmpty())
        assertFalse(DEVICE_ID in quietResult.state.disarmedBluetoothDeviceIds)

        val afterQuiet = evaluate(
            settings,
            quietResult.state,
            bluetooth = listOf(bluetooth(level = 15)),
            minuteOfDay = 8 * 60
        )
        assertEquals(1, afterQuiet.alerts.size)
    }

    @Test
    fun bluetoothRejectsUnknownExpiredDisconnectedAndUnsuccessfulGattReadings() {
        val invalid = listOf(
            bluetooth(level = -1),
            bluetooth(level = 10, checkedAt = NOW - AlertEngine.READING_MAX_AGE_MILLIS - 1),
            bluetooth(level = 10, connected = false),
            bluetooth(level = 10, status = "stale"),
            bluetooth(level = 10, source = "framework", successfulAt = NOW - AlertEngine.READING_MAX_AGE_MILLIS - 1),
            bluetooth(level = 10, source = "unknown"),
            bluetooth(
                level = 10,
                source = AlertEngine.SOURCE_GATT,
                successfulAt = NOW - AlertEngine.READING_MAX_AGE_MILLIS - 1
            )
        )
        invalid.forEach { reading ->
            assertTrue(evaluate(bluetoothSettings(), bluetooth = listOf(reading)).alerts.isEmpty())
        }

        val freshGatt = bluetooth(
            level = 10,
            source = AlertEngine.SOURCE_GATT,
            successfulAt = NOW
        )
        assertEquals(1, evaluate(bluetoothSettings(), bluetooth = listOf(freshGatt)).alerts.size)
    }

    @Test
    fun acknowledgedStatePreventsDuplicateAfterProcessRestart() {
        val settings = bluetoothSettings()
        val first = evaluate(settings, bluetooth = listOf(bluetooth(level = 10)))
        val acknowledged = acknowledgeAll(first)
        val persistedState = acknowledged.copy(
            disarmedBluetoothDeviceIds = acknowledged.disarmedBluetoothDeviceIds.toSet()
        )

        // A new invocation using only persisted data models a fresh app process.
        val afterRestart = evaluate(
            settings,
            persistedState,
            bluetooth = listOf(bluetooth(level = 10))
        )
        assertTrue(afterRestart.alerts.isEmpty())
        assertTrue(DEVICE_ID in afterRestart.state.disarmedBluetoothDeviceIds)
    }

    @Test
    fun failedNotificationDeliveryDoesNotConsumeAnAlert() {
        val result = evaluate(
            AlertSettings(phoneEnabled = true),
            phone = phone(level = 85, plugged = true)
        )
        val notDelivered = AlertEngine.acknowledgeDelivered(result.state, result.alerts, emptySet())
        assertFalse(notDelivered.phoneNotifiedThisCycle)

        val retry = evaluate(
            AlertSettings(phoneEnabled = true),
            notDelivered,
            phone = phone(level = 85, plugged = true)
        )
        assertEquals(1, retry.alerts.size)
    }

    private fun evaluate(
        settings: AlertSettings,
        state: AlertEngineState = AlertEngineState(),
        phone: PhoneAlertInput? = null,
        bluetooth: List<BluetoothAlertInput> = emptyList(),
        minuteOfDay: Int = 12 * 60
    ): AlertEvaluation = AlertEngine.evaluate(
        settings = settings,
        previousState = state,
        phone = phone,
        bluetoothDevices = bluetooth,
        nowMillis = NOW,
        nowMinuteOfDay = minuteOfDay
    )

    private fun acknowledgeAll(result: AlertEvaluation): AlertEngineState =
        AlertEngine.acknowledgeDelivered(
            result.state,
            result.alerts,
            result.alerts.mapTo(linkedSetOf()) { it.deliveryKey }
        )

    private fun phone(level: Int, plugged: Boolean) = PhoneAlertInput(
        level = level,
        isCharging = plugged,
        isPlugged = plugged,
        checkedAtMillis = NOW
    )

    private fun bluetoothSettings(threshold: Int = 20) = AlertSettings(
        bluetoothEnabled = true,
        bluetoothThreshold = threshold,
        selectedBluetoothDeviceIds = setOf(DEVICE_ID)
    )

    private fun bluetooth(
        level: Int,
        connected: Boolean = true,
        checkedAt: Long = NOW,
        successfulAt: Long = NOW,
        source: String = "framework",
        status: String = AlertEngine.STATUS_AVAILABLE
    ) = BluetoothAlertInput(
        deviceId = DEVICE_ID,
        displayName = "Headphones",
        level = level,
        isConnected = connected,
        lastCheckedAtMillis = checkedAt,
        lastSuccessfulReadAtMillis = successfulAt,
        source = source,
        status = status
    )

    companion object {
        private const val NOW = 1_000_000L
        private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"
    }
}
