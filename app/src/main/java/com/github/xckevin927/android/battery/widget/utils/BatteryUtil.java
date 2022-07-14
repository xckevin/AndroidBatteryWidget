package com.github.xckevin927.android.battery.widget.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public class BatteryUtil {

    public static BatteryState getBatteryState(Context context) {
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // Are we charging / charged?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        // How are we charging?
        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level * 100 / (float)scale;

        return new BatteryState(usbCharge, acCharge, (int) batteryPct);

//        int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
//        return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }

    public static class BatteryState {
        private boolean isUsbCharge;
        private boolean isAcCharge;
        private int level;

        public BatteryState(boolean isUsbCharge, boolean isAcCharge, int level) {
            this.isUsbCharge = isUsbCharge;
            this.isAcCharge = isAcCharge;
            this.level = level;
        }

        public boolean isUsbCharge() {
            return isUsbCharge;
        }

        public boolean isAcCharge() {
            return isAcCharge;
        }

        public int getLevel() {
            return level;
        }
    }
}
