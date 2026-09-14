package com.github.xckevin927.android.battery.widget.repo

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import com.github.xckevin927.android.battery.widget.App
import com.github.xckevin927.android.battery.widget.model.BatteryReading
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.model.FrameworkBatteryReading
import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState
import com.github.xckevin927.android.battery.widget.utils.ReflectUtil
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object BatteryRepo {

    private const val TAG = "BatteryRepo"
    private const val UNKNOWN_BATTERY_LEVEL = -1
    private const val GATT_READ_INTERVAL_MS = 60_000L
    private const val GATT_CONNECTION_TIMEOUT_MS = 15_000L

    private val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    // Bluetooth SIG Battery Level characteristic.
    private val batteryLevelUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    private val context by lazy { App.getAppContext() }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val gattReadsInFlight = ConcurrentHashMap<String, Long>()
    private val lastGattReadAttempt = ConcurrentHashMap<String, Long>()
    private val gattReadings = ConcurrentHashMap<String, BatteryReading>()
    private val frameworkReadings = ConcurrentHashMap<String, FrameworkBatteryReading>()
    private val connectionSessions = ConcurrentHashMap<String, Long>()
    private val nextSession = AtomicLong()
    private val stateListeners = CopyOnWriteArraySet<Runnable>()
    @Volatile private var bluetoothSnapshot: List<BtDeviceState> = emptyList()
    @Volatile private var phoneSnapshot: PhoneBatteryState? = null
    @Volatile var lastRefreshReason: String = "none"
        private set
    @Volatile var lastRefreshAt: Long = 0
        private set
    @Volatile var lastPhoneReadAt: Long = 0
        private set

    @JvmStatic fun addListener(listener: Runnable) { stateListeners.add(listener) }
    @JvmStatic fun removeListener(listener: Runnable) { stateListeners.remove(listener) }

    /** No Bluetooth I/O: views can render repeatedly while cached values age naturally. */
    @JvmStatic fun getBtSnapshot(): List<BtDeviceState> = bluetoothSnapshot.map { state ->
        if (!state.isConnected) return@map state.copy()
        val address = deviceAddress(state.bluetoothDevice)
            ?: return@map state.copy(batteryLevel = -1, status = "permission_denied")
        resolveBluetoothState(state, address, System.currentTimeMillis())
    }

    /** Record evidence carried by the platform battery-level broadcast before doing a full refresh. */
    @JvmStatic @Synchronized fun recordFrameworkBatteryReport(device: BluetoothDevice?, level: Int) {
        if (device == null || level !in 0..100 || !hasBluetoothConnectPermission()) return
        val address = deviceAddress(device) ?: return
        val now = System.currentTimeMillis()
        frameworkReadings.compute(address) { _, old ->
            (old ?: FrameworkBatteryReading()).observe(level, now, explicitlyReported = true)
        }
    }

    @JvmStatic fun refresh(reason: String) {
        readPhoneState()
        getBtDeviceStates()
        lastRefreshAt = System.currentTimeMillis()
        lastRefreshReason = reason
        notifyStateChanged()
    }

    private fun notifyStateChanged() {
        mainHandler.post { stateListeners.forEach { runCatching { it.run() } } }
    }

    /** Callbacks are always dispatched on the main thread. */
    val leUpdateListener = CopyOnWriteArraySet<((BtDeviceState) -> Unit)>()

    fun getBatteryState(): PhoneBatteryState = phoneSnapshot ?: readPhoneState()

    private fun readPhoneState(): PhoneBatteryState {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
        val acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
        val wirelessCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (scale > 0 && level >= 0) {
            (level * 100f / scale).toInt().coerceIn(0, 100)
        } else {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
                ?: UNKNOWN_BATTERY_LEVEL
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        lastPhoneReadAt = System.currentTimeMillis()
        return PhoneBatteryState().apply {
            setStatus(status)
            setPlugged(chargePlug > 0)
            checkedAtMillis = lastPhoneReadAt
            isAcCharge = acCharge
            isUsbCharge = usbCharge
            isWirelessCharge = wirelessCharge
            setLevel(batteryPct)
            isInPowerSaveMode = powerManager.isPowerSaveMode
        }.also { phoneSnapshot = it }
    }

    @Synchronized fun getBtDeviceStates(): List<BtDeviceState> {
        if (!hasBluetoothConnectPermission()) {
            return clearBluetoothSnapshot()
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return clearBluetoothSnapshot()
        val bluetoothAdapter = try {
            bluetoothManager.adapter
        } catch (e: SecurityException) {
            return clearBluetoothSnapshot()
        } ?: return clearBluetoothSnapshot()
        val enabled = try {
            bluetoothAdapter.isEnabled
        } catch (e: SecurityException) {
            false
        }
        if (!enabled) {
            return clearBluetoothSnapshot()
        }

        val pairedDevices = try {
            bluetoothAdapter.bondedDevices
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth permission was revoked while reading paired devices", e)
            return clearBluetoothSnapshot()
        }
        val gattConnectedAddresses = connectedGattDeviceAddresses(bluetoothManager)
        val states = pairedDevices.mapNotNull { device ->
            val address = deviceAddress(device) ?: return@mapNotNull null
            // HiddenApiBypass enables the primary framework source for per-device state.
            // GATT is only a public-API fallback when that hidden method is unavailable.
            val connected = isDeviceConnected(device) || address in gattConnectedAddresses
            if (!connected) {
                // Do not carry a previous session's BLE value into a later reconnection.
                gattReadings.remove(address)
                frameworkReadings.remove(address)
                lastGattReadAttempt.remove(address)
                connectionSessions.remove(address)
                gattReadsInFlight.remove(address)
            }
            val reflectedLevel = reflectedBatteryLevel(device)
            val now = System.currentTimeMillis()
            if (connected && (reflectedLevel != UNKNOWN_BATTERY_LEVEL || frameworkReadings.containsKey(address))) {
                frameworkReadings.compute(address) { _, old ->
                    (old ?: FrameworkBatteryReading()).observe(reflectedLevel, now)
                }
            }
            resolveBluetoothState(
                BtDeviceState(device, UNKNOWN_BATTERY_LEVEL, connected,
                    lastCheckedAt = 0, lastSuccessfulReadAt = 0,
                    source = "unknown", status = if (connected) "unknown" else "disconnected",
                    failureReason = null),
                address,
                now
            )
        }.toMutableList()

        states.sortWith(compareByDescending<BtDeviceState> { it.isConnected }
            .thenBy { deviceName(it.bluetoothDevice) }
            .thenBy { deviceAddress(it.bluetoothDevice).orEmpty() })
        bluetoothSnapshot = states
        // Publish before starting any asynchronous operation; even an immediate callback must
        // update this generation instead of being overwritten by an older snapshot below it.
        states.filter { state ->
            if (!state.isConnected) false else {
                val address = deviceAddress(state.bluetoothDevice)
                address == null || frameworkReadings[address]?.statusAt(System.currentTimeMillis()) in
                    setOf(null, "unknown")
            }
        }.forEach {
            tryConnectBleService(it.bluetoothDevice, it)
        }
        return states
    }

    private fun clearBluetoothSnapshot(): List<BtDeviceState> {
        bluetoothSnapshot = emptyList()
        connectionSessions.clear()
        gattReadings.clear()
        frameworkReadings.clear()
        gattReadsInFlight.clear()
        lastGattReadAttempt.clear()
        return emptyList()
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        // BLUETOOTH_CONNECT became a runtime permission on Android 12. Earlier releases use
        // the manifest BLUETOOTH permission and must not be rejected by this Android-12 check.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun connectedGattDeviceAddresses(bluetoothManager: BluetoothManager): Set<String> {
        if (!hasBluetoothConnectPermission()) return emptySet()
        return try {
            bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
                .mapNotNullTo(mutableSetOf()) { deviceAddress(it) }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read connected GATT devices", e)
            emptySet()
        } catch (e: IllegalArgumentException) {
            // Some Bluetooth stacks do not expose the GATT profile query.
            emptySet()
        }
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return ReflectUtil.invoke<Boolean>(device, "isConnected", emptyArray<Class<*>>()) == true
    }

    private fun reflectedBatteryLevel(device: BluetoothDevice): Int {
        return ReflectUtil.invoke<Int>(device, "getBatteryLevel", emptyArray<Class<*>>())
            ?.takeIf { it in 0..100 }
            ?: UNKNOWN_BATTERY_LEVEL
    }

    private fun deviceName(device: BluetoothDevice): String {
        if (!hasBluetoothConnectPermission()) return ""
        return try {
            device.name.orEmpty()
        } catch (e: SecurityException) {
            ""
        }
    }

    private fun deviceAddress(device: BluetoothDevice): String? {
        if (!hasBluetoothConnectPermission()) return null
        return try {
            device.address
        } catch (e: SecurityException) {
            null
        }
    }

    private fun resolveBluetoothState(
        state: BtDeviceState,
        address: String,
        now: Long
    ): BtDeviceState {
        if (!state.isConnected) {
            return state.copy(batteryLevel = UNKNOWN_BATTERY_LEVEL, status = "disconnected")
        }
        val framework = frameworkReadings[address]
        val frameworkStatus = framework?.statusAt(now) ?: "unknown"
        val gatt = gattReadings[address]
        val gattLevel = gatt?.displayedLevel(now) ?: UNKNOWN_BATTERY_LEVEL

        // A still-valid framework lookup keeps the display usable even if the value never changes.
        // Its report clock stays separate for alerts. Once valid lookups expire, allow GATT fallback.
        return when {
            framework != null && frameworkStatus != "unknown" -> state.copy(
                batteryLevel = framework.displayedLevel(now),
                lastCheckedAt = framework.checkedAt,
                lastSuccessfulReadAt = framework.successAt,
                source = "framework",
                status = frameworkStatus,
                failureReason = null
            )
            gatt != null && gattLevel != UNKNOWN_BATTERY_LEVEL -> state.copy(
                batteryLevel = gattLevel,
                lastCheckedAt = gatt.attemptedAt,
                lastSuccessfulReadAt = gatt.successAt,
                source = "gatt",
                status = gatt.statusAt(now),
                failureReason = gatt.failure
            )
            framework != null -> state.copy(
                batteryLevel = UNKNOWN_BATTERY_LEVEL,
                lastCheckedAt = framework.checkedAt,
                lastSuccessfulReadAt = framework.successAt,
                source = "framework",
                status = "unknown",
                failureReason = null
            )
            gatt != null -> state.copy(
                batteryLevel = UNKNOWN_BATTERY_LEVEL,
                lastCheckedAt = gatt.attemptedAt,
                lastSuccessfulReadAt = gatt.successAt,
                source = "gatt",
                status = gatt.statusAt(now),
                failureReason = gatt.failure
            )
            else -> state.copy(
                batteryLevel = UNKNOWN_BATTERY_LEVEL,
                lastCheckedAt = 0,
                lastSuccessfulReadAt = 0,
                source = "unknown",
                status = "unknown",
                failureReason = null
            )
        }
    }

    private fun tryConnectBleService(device: BluetoothDevice, state: BtDeviceState) {
        if (!hasBluetoothConnectPermission()) return
        val address = deviceAddress(device) ?: return
        val now = SystemClock.elapsedRealtime()
        val previousAttempt = lastGattReadAttempt[address]
        if (previousAttempt != null && now - previousAttempt < GATT_READ_INTERVAL_MS) return
        val session = connectionSessions.getOrPut(address) { nextSession.incrementAndGet() }
        if (gattReadsInFlight.putIfAbsent(address, session) != null) return
        lastGattReadAttempt[address] = now
        gattReadings.compute(address) { _, old -> (old ?: BatteryReading()).copy(attemptedAt = System.currentTimeMillis()) }

        val callback = object : BluetoothGattCallback() {
            private val completed = AtomicBoolean(false)
            @Volatile private var activeGatt: BluetoothGatt? = null
            private var failureReason = "read_failed"

            private fun claimCompletion(): Boolean {
                if (!completed.compareAndSet(false, true)) return false
                // A late callback from a disconnected session must not release a new read.
                gattReadsInFlight.remove(address, session)
                return true
            }

            private fun closeGatt(gatt: BluetoothGatt?) {
                try {
                    gatt?.close()
                } catch (e: SecurityException) {
                    Log.d(TAG, "Bluetooth permission revoked before GATT cleanup", e)
                } catch (e: Exception) {
                    Log.d(TAG, "Unable to close GATT for $address", e)
                }
            }

            private fun complete(gatt: BluetoothGatt?) {
                if (!claimCompletion()) {
                    closeGatt(gatt)
                    return
                }
                synchronized(this@BatteryRepo) {
                    if (connectionSessions[address] == session) {
                        gattReadings.compute(address) { _, old -> (old ?: BatteryReading()).copy(failure = failureReason) }
                        publishGattState(state, address)
                    }
                }
                closeGatt(gatt ?: activeGatt)
            }

            fun setGatt(gatt: BluetoothGatt) {
                activeGatt = gatt
                // connectGatt may dispatch an immediate failure callback before returning.
                if (completed.get()) closeGatt(gatt)
            }

            fun timeout() {
                failureReason = "timeout"
                complete(activeGatt)
            }

            fun failed(reason: String) {
                failureReason = reason
                complete(activeGatt)
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (completed.get()) {
                    closeGatt(gatt)
                    return
                }
                when {
                    status != BluetoothGatt.GATT_SUCCESS -> complete(gatt)
                    newState == BluetoothProfile.STATE_CONNECTED -> {
                        try {
                            if (gatt?.discoverServices() != true) complete(gatt)
                        } catch (e: SecurityException) {
                            complete(gatt)
                        }
                    }
                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        failureReason = "disconnected"
                        complete(gatt)
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (completed.get()) {
                    closeGatt(gatt)
                    return
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    complete(gatt)
                    return
                }
                val activeGatt = gatt ?: run {
                    complete(null)
                    return
                }
                val batteryLevel = activeGatt.getService(batteryServiceUuid)
                    ?.getCharacteristic(batteryLevelUuid)
                try {
                    if (batteryLevel == null) failureReason = "unsupported"
                    if (batteryLevel == null || !activeGatt.readCharacteristic(batteryLevel)) {
                        complete(activeGatt)
                    }
                } catch (e: SecurityException) {
                    complete(activeGatt)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                handleCharacteristicRead(gatt, characteristic, characteristic.value, status)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                handleCharacteristicRead(gatt, characteristic, value, status)
            }

            private fun handleCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray?,
                status: Int
            ) {
                // Claim completion before touching cache so a timeout cannot race a late read.
                if (!claimCompletion()) {
                    closeGatt(gatt)
                    return
                }
                synchronized(this@BatteryRepo) {
                    if (connectionSessions[address] != session) {
                        closeGatt(gatt)
                        return
                    }
                    if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == batteryLevelUuid) {
                        val level = value?.firstOrNull()?.toInt()?.and(0xff)
                        if (level != null && level in 0..100) {
                            gattReadings[address] = BatteryReading(level, System.currentTimeMillis(), System.currentTimeMillis())
                        } else {
                            gattReadings.compute(address) { _, old -> (old ?: BatteryReading()).copy(failure = "invalid_value") }
                        }
                    } else {
                        gattReadings.compute(address) { _, old -> (old ?: BatteryReading()).copy(failure = "read_failed") }
                    }
                    publishGattState(state, address)
                }
                closeGatt(gatt)
            }
        }

        try {
            val gatt = device.connectGatt(context, false, callback)
            if (gatt == null) {
                callback.failed("connection_failed")
            } else {
                callback.setGatt(gatt)
                mainHandler.postDelayed({ callback.timeout() }, GATT_CONNECTION_TIMEOUT_MS)
            }
        } catch (e: SecurityException) {
            callback.failed("permission_denied")
            Log.w(TAG, "Bluetooth permission was revoked while connecting to $address", e)
        } catch (e: IllegalStateException) {
            callback.failed("connection_failed")
            Log.w(TAG, "Cannot create GATT connection for $address", e)
        }
    }

    private fun notifyLeUpdate(state: BtDeviceState) {
        mainHandler.post {
            leUpdateListener.forEach { listener ->
                runCatching { listener(state) }
                    .onFailure { Log.w(TAG, "Bluetooth update listener failed", it) }
            }
        }
    }

    private fun publishGattState(original: BtDeviceState, address: String) {
        val reading = gattReadings[address] ?: return
        val current = bluetoothSnapshot.firstOrNull { deviceAddress(it.bluetoothDevice) == address } ?: original
        // A callback from the previous connection must not restore an offline device.
        if (!current.isConnected) return
        val now = System.currentTimeMillis()
        val framework = frameworkReadings[address]
        if (framework != null && framework.statusAt(now) != "unknown") return
        val updated = resolveBluetoothState(current, address, now)
        bluetoothSnapshot = bluetoothSnapshot.map { if (deviceAddress(it.bluetoothDevice) == address) updated else it }
        notifyLeUpdate(updated)
        notifyStateChanged()
    }
}
