package com.github.xckevin927.android.battery.widget.repo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.github.xckevin927.android.battery.widget.App
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState
import com.github.xckevin927.android.battery.widget.utils.ReflectUtil
import java.lang.Boolean
import java.lang.String
import java.util.UUID
import kotlin.ByteArray
import kotlin.Int
import kotlin.apply
import kotlin.arrayOfNulls
import kotlin.getValue
import kotlin.lazy

object BatteryRepo {

    private const val TAG = "BatteryRepo"

    var BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    var BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a1b-0000-1000-8000-00805f9b34fb")

    private val context by lazy {
        App.getAppContext()
    }

    val leUpdateListener = mutableListOf<((BtDeviceState) -> Unit)>()

    fun getBatteryState(): PhoneBatteryState {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Are we charging / charged?
        val status = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        // How are we charging?
        val chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
        val acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
        val wirelessCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS
        
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = level * 100f / scale

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager


        val phoneBatteryState = PhoneBatteryState().apply {
            isAcCharge = acCharge
            isUsbCharge = usbCharge
            isWirelessCharge = wirelessCharge
            setLevel(batteryPct.toInt())
            isInPowerSaveMode = powerManager.isPowerSaveMode
        }
        
        return phoneBatteryState
    }

    fun getBtDeviceStates(): List<BtDeviceState> {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return listOf()
        }
        val bluetoothManager = App.getAppContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val pairedDevices: Set<BluetoothDevice> = bluetoothManager.adapter.bondedDevices
        val pairedDevicesCount = pairedDevices.size
        val list: MutableList<BtDeviceState> = ArrayList(pairedDevicesCount)
        for (device in pairedDevices) {
            val connected = Boolean.TRUE == ReflectUtil.invoke(device, "isConnected", arrayOfNulls(0))
            val level = if (connected) {
                val levelBox = ReflectUtil.invoke<Int>(device, "getBatteryLevel", arrayOfNulls(0))
                levelBox ?: -1
            } else  {
                -1
            }
            val state = BtDeviceState(device, level, connected)
            list.add(state)

            if (connected) {
                tryConnectBleService(device, state)
            }
        }
        list.sortWith(object : Comparator<BtDeviceState> {
            @SuppressLint("MissingPermission")
            override fun compare(o1: BtDeviceState, o2: BtDeviceState): Int {
                if (o1.isConnected && o2.isConnected) {
                    return o1.bluetoothDevice.name.compareTo(o2.bluetoothDevice.name)
                }
                if (!o1.isConnected && !o2.isConnected) {
                    return o1.bluetoothDevice.name.compareTo(o2.bluetoothDevice.name)
                }
                if (o1.isConnected) {
                    return -1
                }
                return 1
            }
        })
        return list
    }

    @SuppressLint("MissingPermission")
    private fun tryConnectBleService(device: BluetoothDevice, state: BtDeviceState) {
        Log.i(TAG, "tryConnectBleService: ${device.name}")
        device.connectGatt(context, false, object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // successfully connected to the GATT Server
                    Log.i(TAG, "onConnectionStateChange: STATE_CONNECTED ${device.name}")
                    readBattery(gatt)
                    // gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // disconnected from the GATT Server
                    Log.i(TAG, "onConnectionStateChange: STATE_DISCONNECTED ${device.name}")
                }
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                val list = gatt?.services
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "onServicesDiscovered: ${device.name}")
                    readBattery(gatt)
                }
            }

            private fun readBattery(gatt: BluetoothGatt?) {
                val batteryService: BluetoothGattService? = gatt?.getService(BATTERY_SERVICE_UUID)
                if (batteryService == null) {
                    Log.i(TAG, "Battery service not found!")
                    return
                }
                Log.i(TAG, "Battery service found!")

                val batteryLevel = batteryService.getCharacteristic(BATTERY_LEVEL_UUID)
                if (batteryLevel == null) {
                    Log.i(TAG, "Battery characteristic not found!")
                    return
                }
                Log.i(TAG, "Battery characteristic found!")
                Log.i(TAG, String.valueOf(gatt.readCharacteristic(batteryLevel)))
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                super.onCharacteristicChanged(gatt, characteristic, value)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                super.onCharacteristicRead(gatt, characteristic, value, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val level = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    Log.i(TAG, "onCharacteristicRead: $level")
                    state.batteryLevel = level
                    leUpdateListener.forEach { it.invoke(state) }
                }
            }
        })

    }
}