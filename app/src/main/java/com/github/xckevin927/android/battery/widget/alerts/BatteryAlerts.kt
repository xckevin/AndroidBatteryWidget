package com.github.xckevin927.android.battery.widget.alerts

import android.content.Context
import android.os.BatteryManager
import com.github.xckevin927.android.battery.widget.billing.ProBilling
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState
import com.github.xckevin927.android.battery.widget.utils.DevicePreferences
import java.util.Calendar

/** Entry point for phone and Bluetooth refresh paths. */
object BatteryAlerts {
    private val evaluationLock = Any()

    /**
     * Evaluates fresh repository state without starting a service or scheduling an alarm.
     * Safe for Java callers and repeated invocations from independent refresh callbacks.
     */
    @JvmStatic
    fun evaluate(context: Context, phoneState: PhoneBatteryState, btStates: List<BtDeviceState>) {
        val appContext = context.applicationContext
        synchronized(evaluationLock) {
            // Ownership is checked at the execution boundary as well as in the settings UI.
            // Keep saved settings intact so a restored purchase can resume them unchanged.
            if (!ProBilling.hasPro(appContext)) return
            val settings = AlertPreferences.getSettings(appContext)
            val previousState = AlertPreferences.getEngineState(appContext)
            val now = System.currentTimeMillis()
            val calendar = Calendar.getInstance()
            val phone = PhoneAlertInput(
                level = phoneState.level,
                isCharging = phoneState.isCharging ||
                    phoneState.status == BatteryManager.BATTERY_STATUS_FULL,
                isPlugged = phoneState.isPlugged,
                checkedAtMillis = phoneState.checkedAtMillis
            )
            val bluetooth = btStates.mapNotNull { state ->
                val id = AlertPreferences.deviceId(state) ?: return@mapNotNull null
                BluetoothAlertInput(
                    deviceId = id,
                    displayName = DevicePreferences.displayName(appContext, state),
                    level = state.batteryLevel,
                    isConnected = state.isConnected,
                    lastCheckedAtMillis = state.lastCheckedAt,
                    lastSuccessfulReadAtMillis = state.lastSuccessfulReadAt,
                    source = state.source,
                    status = state.status
                )
            }
            val evaluation = AlertEngine.evaluate(
                settings = settings,
                previousState = previousState,
                phone = phone,
                bluetoothDevices = bluetooth,
                nowMillis = now,
                nowMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 +
                    calendar.get(Calendar.MINUTE)
            )
            val delivered = AlertNotificationController.post(appContext, evaluation.alerts)
            val finalState = AlertEngine.acknowledgeDelivered(
                evaluation.state,
                evaluation.alerts,
                delivered
            )
            if (finalState != previousState) {
                AlertPreferences.saveEngineState(appContext, finalState)
            }
        }
    }

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        ProBilling.hasPro(context.applicationContext) &&
            AlertPreferences.isAnyAlertEnabled(context.applicationContext)
}
