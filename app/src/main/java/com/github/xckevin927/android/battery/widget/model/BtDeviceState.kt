package com.github.xckevin927.android.battery.widget.model;

import android.bluetooth.BluetoothDevice;

public class BtDeviceState {

    private BluetoothDevice bluetoothDevice;
    private int batteryLevel;
    private boolean connected;

    public BtDeviceState(BluetoothDevice bluetoothDevice, int batteryLevel, boolean connected) {
        this.bluetoothDevice = bluetoothDevice;
        this.batteryLevel = batteryLevel;
        this.connected = connected;
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public String toString() {
        return "BtDeviceState{" +
                "bluetoothDevice=" + bluetoothDevice +
                ", batteryLevel=" + batteryLevel +
                ", connected=" + connected +
                '}';
    }
}

