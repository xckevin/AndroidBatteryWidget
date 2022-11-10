package com.github.xckevin927.android.battery.widget.repo

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import com.github.xckevin927.android.battery.widget.App
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState
import com.github.xckevin927.android.battery.widget.utils.ReflectUtil
import java.lang.Boolean
import kotlin.Int
import kotlin.apply
import kotlin.arrayOfNulls
import kotlin.getValue
import kotlin.lazy

object BatteryRepo {

    private val context by lazy {
        App.getAppContext()
    }

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
            val levelBox = ReflectUtil.invoke<Int>(device, "getBatteryLevel", arrayOfNulls(0))
            val level = levelBox ?: -1
            val connected = Boolean.TRUE == ReflectUtil.invoke(device, "isConnected", arrayOfNulls(0))
            list.add(BtDeviceState(device, level, connected))
        }
        return list
    }
}