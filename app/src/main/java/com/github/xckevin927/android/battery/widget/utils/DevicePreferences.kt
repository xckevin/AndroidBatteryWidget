package com.github.xckevin927.android.battery.widget.utils

import android.annotation.SuppressLint
import android.content.Context
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService
import java.util.Locale

object DevicePreferences {
    private const val PREFERENCES_NAME = "bluetooth_device_preferences"
    private const val KEY_HIDDEN_DEVICES = "hidden_devices"
    private const val KEY_DEVICE_ORDER = "device_order"
    private const val KEY_PERMISSION_REQUESTED = "bluetooth_permission_requested"
    private const val ALIAS_PREFIX = "alias:"
    private const val ORDER_SEPARATOR = ","

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @JvmStatic
    fun displayName(context: Context, state: BtDeviceState): String {
        val alias = preferences(context).getString(ALIAS_PREFIX + deviceKey(state), null)
            ?.trim()
            .orEmpty()
        if (alias.isNotEmpty()) return alias
        return platformName(state).ifEmpty { context.getString(R.string.device_unknown_name) }
    }

    @JvmStatic
    fun visibleDevices(context: Context, states: List<BtDeviceState>): List<BtDeviceState> {
        val hidden = preferences(context).getStringSet(KEY_HIDDEN_DEVICES, emptySet()).orEmpty()
        return allDevices(context, states).filterNot { deviceKey(it) in hidden }
    }

    @JvmStatic
    @Synchronized
    fun allDevices(context: Context, states: List<BtDeviceState>): List<BtDeviceState> {
        val byKey = LinkedHashMap<String, BtDeviceState>()
        states.forEach { byKey.putIfAbsent(deviceKey(it), it) }
        val deterministicKeys = byKey.values
            .sortedWith(compareBy<BtDeviceState> { displayName(context, it).lowercase(Locale.ROOT) }
                .thenBy { deviceKey(it) })
            .map(::deviceKey)
        val storedOrder = readOrder(context)
        val orderedKeys = DeviceOrder.reconcile(storedOrder, deterministicKeys)
        if (orderedKeys.isNotEmpty() && orderedKeys != storedOrder) {
            preferences(context).edit()
                .putString(KEY_DEVICE_ORDER, orderedKeys.joinToString(ORDER_SEPARATOR))
                .apply()
        }
        return orderedKeys.mapNotNull(byKey::get)
    }

    @JvmStatic
    fun isVisible(context: Context, state: BtDeviceState): Boolean {
        return deviceKey(state) !in preferences(context)
            .getStringSet(KEY_HIDDEN_DEVICES, emptySet())
            .orEmpty()
    }

    @JvmStatic
    @Synchronized
    fun setVisible(context: Context, state: BtDeviceState, visible: Boolean) {
        val prefs = preferences(context)
        val hidden = prefs.getStringSet(KEY_HIDDEN_DEVICES, emptySet()).orEmpty().toMutableSet()
        if (visible) hidden.remove(deviceKey(state)) else hidden.add(deviceKey(state))
        prefs.edit().putStringSet(KEY_HIDDEN_DEVICES, hidden).apply()
        refreshWidgets(context)
    }

    @JvmStatic
    fun alias(context: Context, state: BtDeviceState): String {
        return preferences(context).getString(ALIAS_PREFIX + deviceKey(state), null).orEmpty()
    }

    @JvmStatic
    @Synchronized
    fun setAlias(context: Context, state: BtDeviceState, alias: String) {
        val key = ALIAS_PREFIX + deviceKey(state)
        val normalized = alias.trim()
        preferences(context).edit().apply {
            if (normalized.isEmpty()) remove(key) else putString(key, normalized)
        }.apply()
        refreshWidgets(context)
    }

    @JvmStatic
    @Synchronized
    fun move(context: Context, states: List<BtDeviceState>, state: BtDeviceState, offset: Int) {
        val current = allDevices(context, states).map(::deviceKey)
        val moved = DeviceOrder.move(current, deviceKey(state), offset)
        preferences(context).edit().putString(KEY_DEVICE_ORDER, moved.joinToString(ORDER_SEPARATOR)).apply()
        refreshWidgets(context)
    }

    @JvmStatic
    fun wasBluetoothPermissionRequested(context: Context): Boolean {
        return preferences(context).getBoolean(KEY_PERMISSION_REQUESTED, false)
    }

    @JvmStatic
    fun markBluetoothPermissionRequested(context: Context) {
        preferences(context).edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
    }

    private fun readOrder(context: Context): List<String> {
        return preferences(context).getString(KEY_DEVICE_ORDER, null)
            ?.split(ORDER_SEPARATOR)
            ?.filter(String::isNotBlank)
            .orEmpty()
    }

    private fun refreshWidgets(context: Context) {
        WidgetUpdateService.refreshWidgets(context.applicationContext)
    }

    @SuppressLint("MissingPermission")
    private fun platformName(state: BtDeviceState): String {
        return try {
            state.bluetoothDevice.name.orEmpty()
        } catch (_: SecurityException) {
            ""
        }
    }

    @SuppressLint("MissingPermission")
    internal fun deviceKey(state: BtDeviceState): String {
        return try {
            state.bluetoothDevice.address
        } catch (_: SecurityException) {
            state.bluetoothDevice.toString()
        }
    }
}
