package com.github.xckevin927.android.battery.widget.model

import android.bluetooth.BluetoothDevice

data class BtDeviceState(
    val bluetoothDevice: BluetoothDevice,
    var batteryLevel: Int,
    val isConnected: Boolean,
    val lastCheckedAt: Long = 0,
    val lastSuccessfulReadAt: Long = 0,
    val source: String = "unknown",
    val status: String = "unknown",
    val failureReason: String? = null
)
