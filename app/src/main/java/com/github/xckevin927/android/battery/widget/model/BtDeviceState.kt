package com.github.xckevin927.android.battery.widget.model

import android.bluetooth.BluetoothDevice

data class BtDeviceState(val bluetoothDevice: BluetoothDevice, var batteryLevel: Int, val isConnected: Boolean)