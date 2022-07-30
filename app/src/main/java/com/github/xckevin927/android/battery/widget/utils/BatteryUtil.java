package com.github.xckevin927.android.battery.widget.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;

import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState;

public class BatteryUtil {

    public static PhoneBatteryState getBatteryState(Context context) {
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // Are we charging / charged?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        // How are we charging?
        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
        boolean wirelessCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS;

        PhoneBatteryState phoneBatteryState = new PhoneBatteryState();
        phoneBatteryState.setAcCharge(acCharge);
        phoneBatteryState.setUsbCharge(usbCharge);
        phoneBatteryState.setWirelessCharge(wirelessCharge);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level * 100F / scale;
        phoneBatteryState.setLevel((int) batteryPct);

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        phoneBatteryState.setInPowerSaveMode(powerManager.isPowerSaveMode());

        return phoneBatteryState;
    }
}
