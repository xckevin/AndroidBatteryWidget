package com.github.xckevin927.android.battery.widget.alerts

import android.annotation.SuppressLint
import android.content.Context
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import java.util.Locale

/** Persistent alert configuration and delivery state. Writes use commit so process death cannot lose dedupe state. */
object AlertPreferences {
    private const val PREFS_NAME = "battery_alerts"
    private const val KEY_PHONE_ENABLED = "phone_enabled"
    private const val KEY_PHONE_THRESHOLD = "phone_threshold"
    private const val KEY_BLUETOOTH_ENABLED = "bluetooth_enabled"
    private const val KEY_BLUETOOTH_THRESHOLD = "bluetooth_threshold"
    private const val KEY_SELECTED_DEVICES = "selected_devices"
    private const val KEY_QUIET_ENABLED = "quiet_enabled"
    private const val KEY_QUIET_START = "quiet_start"
    private const val KEY_QUIET_END = "quiet_end"

    private const val KEY_STATE_VERSION = "state_version"
    private const val KEY_PHONE_CYCLE_ACTIVE = "state_phone_cycle_active"
    private const val KEY_PHONE_NOTIFIED = "state_phone_notified"
    private const val KEY_DISARMED_DEVICES = "state_disarmed_devices"
    private const val STATE_VERSION = 1

    private val lock = Any()

    @JvmStatic
    fun getSettings(context: Context): AlertSettings = synchronized(lock) {
        val prefs = prefs(context)
        AlertSettings(
            phoneEnabled = prefs.getBoolean(KEY_PHONE_ENABLED, false),
            phoneThreshold = prefs.getInt(
                KEY_PHONE_THRESHOLD,
                AlertSettings.DEFAULT_PHONE_THRESHOLD
            ).coerceIn(1, 100),
            bluetoothEnabled = prefs.getBoolean(KEY_BLUETOOTH_ENABLED, false),
            bluetoothThreshold = prefs.getInt(
                KEY_BLUETOOTH_THRESHOLD,
                AlertSettings.DEFAULT_BLUETOOTH_THRESHOLD
            ).coerceIn(1, AlertSettings.MAX_BLUETOOTH_THRESHOLD),
            selectedBluetoothDeviceIds = prefs.getStringSet(KEY_SELECTED_DEVICES, emptySet())
                .orEmpty()
                .mapTo(linkedSetOf(), ::normalizeDeviceId),
            quietHours = QuietHours(
                enabled = prefs.getBoolean(KEY_QUIET_ENABLED, false),
                startMinuteOfDay = prefs.getInt(KEY_QUIET_START, 22 * 60).coerceIn(0, 1439),
                endMinuteOfDay = prefs.getInt(KEY_QUIET_END, 7 * 60).coerceIn(0, 1439)
            )
        )
    }

    @JvmStatic
    fun saveSettings(context: Context, settings: AlertSettings): Boolean = synchronized(lock) {
        val normalized = settings.copy(
            phoneThreshold = settings.phoneThreshold.coerceIn(1, 100),
            bluetoothThreshold = settings.bluetoothThreshold.coerceIn(
                1,
                AlertSettings.MAX_BLUETOOTH_THRESHOLD
            ),
            selectedBluetoothDeviceIds = settings.selectedBluetoothDeviceIds
                .mapTo(linkedSetOf(), ::normalizeDeviceId),
            quietHours = settings.quietHours.copy(
                startMinuteOfDay = settings.quietHours.startMinuteOfDay.coerceIn(0, 1439),
                endMinuteOfDay = settings.quietHours.endMinuteOfDay.coerceIn(0, 1439)
            )
        )
        val prefs = prefs(context)
        val oldBluetoothThreshold = prefs.getInt(
            KEY_BLUETOOTH_THRESHOLD,
            AlertSettings.DEFAULT_BLUETOOTH_THRESHOLD
        )
        val oldDisarmed = prefs.getStringSet(KEY_DISARMED_DEVICES, emptySet()).orEmpty()
        val retainedDisarmed = if (oldBluetoothThreshold == normalized.bluetoothThreshold) {
            oldDisarmed.map(::normalizeDeviceId)
                .filterTo(linkedSetOf()) { it in normalized.selectedBluetoothDeviceIds }
        } else {
            // A new threshold is a new user request; allow each selected device to be evaluated once.
            emptySet()
        }
        prefs.edit()
            .putBoolean(KEY_PHONE_ENABLED, normalized.phoneEnabled)
            .putInt(KEY_PHONE_THRESHOLD, normalized.phoneThreshold)
            .putBoolean(KEY_BLUETOOTH_ENABLED, normalized.bluetoothEnabled)
            .putInt(KEY_BLUETOOTH_THRESHOLD, normalized.bluetoothThreshold)
            .putStringSet(KEY_SELECTED_DEVICES, normalized.selectedBluetoothDeviceIds.toSet())
            .putBoolean(KEY_QUIET_ENABLED, normalized.quietHours.enabled)
            .putInt(KEY_QUIET_START, normalized.quietHours.startMinuteOfDay)
            .putInt(KEY_QUIET_END, normalized.quietHours.endMinuteOfDay)
            .putStringSet(KEY_DISARMED_DEVICES, retainedDisarmed)
            .commit()
    }

    @JvmStatic
    fun isAnyAlertEnabled(context: Context): Boolean = getSettings(context).let {
        it.phoneEnabled || it.bluetoothEnabled
    }

    internal fun getEngineState(context: Context): AlertEngineState = synchronized(lock) {
        val prefs = prefs(context)
        if (prefs.getInt(KEY_STATE_VERSION, STATE_VERSION) != STATE_VERSION) {
            return@synchronized AlertEngineState()
        }
        AlertEngineState(
            phoneCycleActive = prefs.getBoolean(KEY_PHONE_CYCLE_ACTIVE, false),
            phoneNotifiedThisCycle = prefs.getBoolean(KEY_PHONE_NOTIFIED, false),
            disarmedBluetoothDeviceIds = prefs.getStringSet(KEY_DISARMED_DEVICES, emptySet())
                .orEmpty()
                .mapTo(linkedSetOf(), ::normalizeDeviceId)
        )
    }

    internal fun saveEngineState(context: Context, state: AlertEngineState): Boolean = synchronized(lock) {
        val selected = prefs(context).getStringSet(KEY_SELECTED_DEVICES, emptySet())
            .orEmpty()
            .mapTo(linkedSetOf(), ::normalizeDeviceId)
        prefs(context).edit()
            .putInt(KEY_STATE_VERSION, STATE_VERSION)
            .putBoolean(KEY_PHONE_CYCLE_ACTIVE, state.phoneCycleActive)
            .putBoolean(KEY_PHONE_NOTIFIED, state.phoneNotifiedThisCycle)
            .putStringSet(
                KEY_DISARMED_DEVICES,
                state.disarmedBluetoothDeviceIds
                    .map(::normalizeDeviceId)
                    .filterTo(linkedSetOf()) { it in selected }
            )
            .commit()
    }

    @SuppressLint("MissingPermission")
    fun deviceId(state: BtDeviceState): String? = try {
        state.bluetoothDevice.address?.takeIf { it.isNotBlank() }?.let(::normalizeDeviceId)
    } catch (_: SecurityException) {
        null
    }

    private fun normalizeDeviceId(value: String): String = value.trim().uppercase(Locale.ROOT)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
